package dev.angzarr.client.router;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import dev.angzarr.BusinessResponse;
import dev.angzarr.CommandBook;
import dev.angzarr.ContextualCommand;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.client.annotations.Aggregate;
import io.grpc.Status;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runtime router for aggregate (command handler) components.
 *
 * <p>Produced exclusively by {@link Router#build()} when all registered handler classes carry
 * {@link dev.angzarr.client.annotations.Aggregate @Aggregate}.
 *
 * @param <S> The state type (erased at runtime; declared via {@code @Aggregate(state = ...)})
 */
public final class CommandHandlerRouter<S> implements Built {

    private final String name;
    private final List<Registration<?>> registrations;

    CommandHandlerRouter(String name, List<Registration<?>> registrations) {
        this.name = name;
        this.registrations = registrations;
    }

    @Override
    public String name() {
        return name;
    }

    /** Registered (class, factory, metadata) triples, in registration order. */
    List<Registration<?>> registrations() {
        return registrations;
    }

    /**
     * Dispatch a {@link ContextualCommand} to all registered handlers matching
     * {@code (domain, type_url)}.
     *
     * <p>Multi-handler fan-out: every matching factory is invoked once, handlers run in
     * registration order, events are concatenated into one {@link EventBook}, and the sequence
     * counter advances monotonically across the merged stream. State is rebuilt independently per
     * handler instance via its own {@code @Applies} methods.
     */
    public BusinessResponse dispatch(ContextualCommand request) {
        CommandBook cmdBook = request.getCommand();
        if (cmdBook.getPagesCount() == 0) {
            throw new DispatchException(Status.Code.INVALID_ARGUMENT, "CommandBook has no pages");
        }
        if (!cmdBook.hasCover() || cmdBook.getCover().getDomain().isEmpty()) {
            throw new DispatchException(
                    Status.Code.INVALID_ARGUMENT, "CommandBook cover has no domain");
        }
        String domain = cmdBook.getCover().getDomain();
        Any commandAny = cmdBook.getPages(0).getCommand();
        String typeUrl = commandAny.getTypeUrl();
        if (typeUrl.isEmpty()) {
            throw new DispatchException(
                    Status.Code.INVALID_ARGUMENT, "CommandPage has no command payload");
        }
        String typeSuffix = Dispatch.fullNameFromTypeUrl(typeUrl);

        List<Match> matches = findMatches(domain, typeSuffix);
        if (matches.isEmpty()) {
            throw new DispatchException(
                    Status.Code.INVALID_ARGUMENT,
                    "no handler registered for domain='" + domain + "' type_url='" + typeUrl + "'");
        }

        EventBook prior = request.hasEvents() ? request.getEvents() : null;
        long baseSeq = prior == null ? 0L : prior.getNextSequence();
        long currentSeq = baseSeq;
        EventBook.Builder merged = EventBook.newBuilder();

        for (Match match : matches) {
            Object handler = match.registration.factory().get();
            Aggregate aggregate = (Aggregate) match.registration.metadata().kindAnnotation();
            Object state =
                    Dispatch.buildFreshState(handler, match.registration.metadata(), aggregate.state());
            Dispatch.replayAppliers(handler, state, match.registration.metadata(), prior);

            Message cmd = Dispatch.decodeMessage(commandAny, match.commandClass);
            Object emitted;
            try {
                emitted = match.handle.invoke(handler, cmd, state, currentSeq);
            } catch (Throwable t) {
                throw new DispatchException(
                        Status.Code.INTERNAL,
                        "@Handles on "
                                + match.registration.handlerClass().getSimpleName()
                                + " failed: "
                                + t.getMessage(),
                        t);
            }
            EventBook packed = Dispatch.packEvents(emitted, currentSeq);
            for (EventPage page : packed.getPagesList()) {
                merged.addPages(page);
            }
            currentSeq += packed.getPagesCount();
        }

        return BusinessResponse.newBuilder().setEvents(merged.build()).build();
    }

    private List<Match> findMatches(String domain, String typeSuffix) {
        List<Match> out = new ArrayList<>();
        for (Registration<?> r : registrations) {
            Aggregate a = (Aggregate) r.metadata().kindAnnotation();
            if (!a.domain().equals(domain)) continue;
            for (Map.Entry<Class<?>, MethodHandle> e : r.metadata().handles().entrySet()) {
                if (Dispatch.fullNameOf(e.getKey()).equals(typeSuffix)) {
                    out.add(new Match(r, e.getKey(), e.getValue()));
                }
            }
        }
        return out;
    }

    private record Match(Registration<?> registration, Class<?> commandClass, MethodHandle handle) {}
}
