package dev.angzarr.client.router;

import com.google.protobuf.Message;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.ProcessManagerHandleRequest;
import dev.angzarr.ProcessManagerHandleResponse;
import dev.angzarr.client.Destinations;
import dev.angzarr.client.annotations.ProcessManager;
import io.grpc.Status;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Runtime router for process manager components.
 *
 * <p>Produced exclusively by {@link Router#build()}. The trigger is the last event in {@code
 * request.trigger}; PMs whose {@code @ProcessManager(sources = [...])} include the trigger's domain
 * AND whose {@code @Handles(E)} matches the trigger's proto type are invoked in registration order.
 * Each PM rebuilds its own state from {@code request.process_state} via {@code @Applies}. Returned
 * {@link ProcessManagerResponse}s are merged into a single {@link ProcessManagerHandleResponse}.
 *
 * @param <S> The PM state type (erased at runtime; declared via {@code @ProcessManager(state=...)})
 */
public final class ProcessManagerRouter<S> implements Built {

  private final String name;
  private final List<Registration<?>> registrations;

  ProcessManagerRouter(String name, List<Registration<?>> registrations) {
    this.name = name;
    this.registrations = registrations;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public int handlerCount() {
    return registrations.size();
  }

  @Override
  public List<String> outputDomains() {
    // Audit #42 / parity P3.2: deduped targets across every PM's
    // @ProcessManager(targets={...}).
    java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
    for (Registration<?> r : registrations) {
      ProcessManager pm = (ProcessManager) r.metadata().kindAnnotation();
      for (String t : pm.targets()) {
        if (t != null && !t.isEmpty()) {
          seen.add(t);
        }
      }
    }
    return List.copyOf(seen);
  }

  /**
   * Audit #74: deduped subset of {@link #outputDomains()} drawn from each PM's
   * {@code @ProcessManager(syncTargets = {...})}. Each entry gets an {@code OutputDomainProbe} at
   * runner startup; targets not in {@code syncTargets} ride the async bus. Mirrors Python's {@code
   * ProcessManagerRouter.sync_output_domains()} and Rust's {@code
   * ProcessManagerRouter::sync_output_domains}.
   */
  public List<String> syncOutputDomains() {
    java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
    for (Registration<?> r : registrations) {
      ProcessManager pm = (ProcessManager) r.metadata().kindAnnotation();
      for (String t : pm.syncTargets()) {
        if (t != null && !t.isEmpty()) {
          seen.add(t);
        }
      }
    }
    return List.copyOf(seen);
  }

  /**
   * Audit #74: per-handler check — true when at least one registered PM declares a target that is
   * NOT in its own {@code syncTargets}. Cannot be derived as a set-difference: two PMs may share a
   * target but disagree on its sync status, and the framework must keep the async path covered.
   */
  public boolean hasAsyncOutputs() {
    for (Registration<?> r : registrations) {
      ProcessManager pm = (ProcessManager) r.metadata().kindAnnotation();
      java.util.Set<String> sync = new java.util.HashSet<>(Arrays.asList(pm.syncTargets()));
      for (String t : pm.targets()) {
        if (t == null || t.isEmpty()) continue;
        if (!sync.contains(t)) {
          return true;
        }
      }
    }
    return false;
  }

  List<Registration<?>> registrations() {
    return registrations;
  }

  public ProcessManagerHandleResponse dispatch(ProcessManagerHandleRequest request) {
    if (!request.hasTrigger() || request.getTrigger().getPagesCount() == 0) {
      return ProcessManagerHandleResponse.getDefaultInstance();
    }
    EventBook trigger = request.getTrigger();
    String triggerDomain = trigger.hasCover() ? trigger.getCover().getDomain() : "";
    EventPage last = trigger.getPages(trigger.getPagesCount() - 1);
    if (!last.hasEvent()) return ProcessManagerHandleResponse.getDefaultInstance();
    String suffix = Dispatch.fullNameFromTypeUrl(last.getEvent().getTypeUrl());
    if (suffix.isEmpty()) return ProcessManagerHandleResponse.getDefaultInstance();

    Destinations destinations = new Destinations(request.getDestinationSequencesMap());
    EventBook processState = request.hasProcessState() ? request.getProcessState() : null;

    ProcessManagerHandleResponse.Builder response = ProcessManagerHandleResponse.newBuilder();

    for (Registration<?> r : registrations) {
      ProcessManager pm = (ProcessManager) r.metadata().kindAnnotation();
      if (!Arrays.asList(pm.sources()).contains(triggerDomain)) continue;
      for (Map.Entry<Class<?>, MethodHandle> entry : r.metadata().handles().entrySet()) {
        if (!Dispatch.fullNameOf(entry.getKey()).equals(suffix)) continue;
        Message evt = Dispatch.decodeMessage(last.getEvent(), entry.getKey());
        Object handler = r.factory().get();
        Object state = Dispatch.buildFreshState(handler, r.metadata(), pm.state());
        Dispatch.replayAppliers(handler, state, r.metadata(), processState);
        Object emitted;
        try {
          emitted = entry.getValue().invoke(handler, evt, state, destinations);
        } catch (Throwable t) {
          throw new DispatchException(
              Status.Code.INTERNAL,
              "@Handles on PM " + r.handlerClass().getSimpleName() + " failed: " + t.getMessage(),
              t);
        }
        if (emitted == null) continue;
        if (!(emitted instanceof ProcessManagerResponse pmr)) {
          throw new DispatchException(
              Status.Code.INTERNAL,
              "PM handler must return ProcessManagerResponse, got "
                  + emitted.getClass().getSimpleName());
        }
        response.addAllCommands(pmr.getCommands());
        response.addAllFacts(pmr.getFacts());
        // Audit #92 (2026-04-29 revert): process_events is
        // `repeated EventBook` so the coordinator gets every book
        // intact. Pass the handler's books through verbatim —
        // no client-side merge / first-non-empty-cover-wins.
        // Mirrors Python `response.process_events.extend(...)`
        // (dispatch.py audit #92 block).
        List<EventBook> handlerBooks = pmr.getProcessEventsList();
        if (!handlerBooks.isEmpty()) {
          response.addAllProcessEvents(handlerBooks);
        }
      }
    }
    return response.build();
  }
}
