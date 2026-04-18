package dev.angzarr.client.router;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import dev.angzarr.BusinessResponse;
import dev.angzarr.CommandBook;
import dev.angzarr.ContextualCommand;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.Notification;
import dev.angzarr.RejectionNotification;
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

        // Notification branch: compensation for a rejected upstream command.
        if (typeSuffix.equals("angzarr.Notification")) {
            return dispatchNotification(commandAny, request);
        }

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

    private BusinessResponse dispatchNotification(Any commandAny, ContextualCommand request) {
        Notification notification;
        try {
            notification = Notification.parseFrom(commandAny.getValue());
        } catch (InvalidProtocolBufferException ipbe) {
            throw new DispatchException(
                    Status.Code.INVALID_ARGUMENT, "malformed Notification payload", ipbe);
        }
        RejectionNotification rejection = RejectionNotification.getDefaultInstance();
        if (notification.hasPayload()) {
            try {
                rejection =
                        notification.getPayload().unpack(RejectionNotification.class);
            } catch (InvalidProtocolBufferException ipbe) {
                throw new DispatchException(
                        Status.Code.INVALID_ARGUMENT,
                        "Notification payload is not a RejectionNotification",
                        ipbe);
            }
        }

        String sourceDomain = "";
        String cmdSuffix = "";
        if (rejection.hasRejectedCommand()) {
            CommandBook rc = rejection.getRejectedCommand();
            if (rc.hasCover()) {
                sourceDomain = rc.getCover().getDomain();
            }
            if (rc.getPagesCount() > 0 && rc.getPages(0).hasCommand()) {
                String rcTypeUrl = rc.getPages(0).getCommand().getTypeUrl();
                if (!rcTypeUrl.isEmpty()) {
                    int slash = rcTypeUrl.lastIndexOf('/');
                    String fullName = slash < 0 ? rcTypeUrl : rcTypeUrl.substring(slash + 1);
                    int dot = fullName.lastIndexOf('.');
                    cmdSuffix = dot < 0 ? fullName : fullName.substring(dot + 1);
                }
            }
        }

        EventBook prior = request.hasEvents() ? request.getEvents() : null;
        long baseSeq = prior == null ? 0L : prior.getNextSequence();
        long currentSeq = baseSeq;
        EventBook.Builder merged = EventBook.newBuilder();

        HandlerMetadata.RejectedKey key = new HandlerMetadata.RejectedKey(sourceDomain, cmdSuffix);
        for (Registration<?> r : registrations) {
            MethodHandle rejectedHandle = r.metadata().rejected().get(key);
            if (rejectedHandle == null) continue;
            Aggregate aggregate = (Aggregate) r.metadata().kindAnnotation();
            Object handler = r.factory().get();
            Object state = Dispatch.buildFreshState(handler, r.metadata(), aggregate.state());
            Dispatch.replayAppliers(handler, state, r.metadata(), prior);
            Object emitted;
            try {
                emitted = rejectedHandle.invoke(handler, notification, state);
            } catch (Throwable t) {
                throw new DispatchException(
                        Status.Code.INTERNAL,
                        "@Rejected on "
                                + r.handlerClass().getSimpleName()
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

    private record Match(Registration<?> registration, Class<?> commandClass, MethodHandle handle) {}
}
