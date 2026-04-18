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
 * request.trigger}; PMs whose {@code @ProcessManager(sources = [...])} include the trigger's
 * domain AND whose {@code @Handles(E)} matches the trigger's proto type are invoked in
 * registration order. Each PM rebuilds its own state from {@code request.process_state} via
 * {@code @Applies}. Returned {@link ProcessManagerResponse}s are merged into a single {@link
 * ProcessManagerHandleResponse}.
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
        EventBook.Builder mergedProcessEvents = EventBook.newBuilder();
        boolean hasProcessEvents = false;

        for (Registration<?> r : registrations) {
            ProcessManager pm = (ProcessManager) r.metadata().kindAnnotation();
            if (!Arrays.asList(pm.sources()).contains(triggerDomain)) continue;
            for (Map.Entry<Class<?>, MethodHandle> entry : r.metadata().handles().entrySet()) {
                if (!Dispatch.fullNameOf(entry.getKey()).equals(suffix)) continue;
                Message evt = Dispatch.decodeMessage(last.getEvent(), entry.getKey());
                Object handler = r.factory().get();
                Object state =
                        Dispatch.buildFreshState(handler, r.metadata(), pm.state());
                Dispatch.replayAppliers(handler, state, r.metadata(), processState);
                Object emitted;
                try {
                    emitted = entry.getValue().invoke(handler, evt, state, destinations);
                } catch (Throwable t) {
                    throw new DispatchException(
                            Status.Code.INTERNAL,
                            "@Handles on PM "
                                    + r.handlerClass().getSimpleName()
                                    + " failed: "
                                    + t.getMessage(),
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
                if (pmr.getProcessEvents() != null) {
                    EventBook pe = pmr.getProcessEvents();
                    for (EventPage page : pe.getPagesList()) {
                        mergedProcessEvents.addPages(page);
                    }
                    if (!hasProcessEvents && pe.hasCover()) {
                        mergedProcessEvents.setCover(pe.getCover());
                        hasProcessEvents = true;
                    } else if (!hasProcessEvents) {
                        hasProcessEvents = true;
                    }
                }
            }
        }
        if (hasProcessEvents) {
            response.setProcessEvents(mergedProcessEvents.build());
        }
        return response.build();
    }
}
