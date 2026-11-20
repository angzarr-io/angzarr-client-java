package dev.angzarr.client.router;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.PageHeader;
import io.grpc.Status;
import java.lang.invoke.MethodHandle;
import java.util.Map;

/**
 * Shared dispatch plumbing used by the four runtime routers: state rebuild from prior events,
 * decoding a protobuf {@link Any}, packing a handler return value into an {@link EventBook} with
 * sequential pages.
 */
final class Dispatch {

    static final String TYPE_URL_PREFIX = "type.googleapis.com/";

    private Dispatch() {}

    /** Return the type URL's full-name suffix, or empty string if not a {@code type.googleapis.com} URL. */
    static String fullNameFromTypeUrl(String typeUrl) {
        if (typeUrl == null || !typeUrl.startsWith(TYPE_URL_PREFIX)) {
            return "";
        }
        return typeUrl.substring(TYPE_URL_PREFIX.length());
    }

    /**
     * Decode an {@link Any} payload into an instance of {@code messageClass} via its {@code
     * parseFrom(byte[])} static method.
     */
    static Message decodeMessage(Any payload, Class<?> messageClass) {
        try {
            var method = messageClass.getMethod("parseFrom", byte[].class);
            return (Message) method.invoke(null, payload.getValue().toByteArray());
        } catch (ReflectiveOperationException roe) {
            Throwable cause = roe.getCause() instanceof InvalidProtocolBufferException ipbe ? ipbe : roe;
            throw new DispatchException(
                    Status.Code.INVALID_ARGUMENT,
                    "failed to decode " + messageClass.getSimpleName() + ": " + cause.getMessage(),
                    cause);
        }
    }

    /**
     * Construct a fresh state instance for {@code handler}. If {@code metadata} carries a
     * {@code @StateFactory} method, invoke it on the handler; otherwise call {@code
     * stateClass.getDeclaredConstructor().newInstance()}.
     */
    static Object buildFreshState(Object handler, HandlerMetadata metadata, Class<?> stateClass) {
        if (metadata.stateFactory().isPresent()) {
            try {
                return metadata.stateFactory().get().invoke(handler);
            } catch (Throwable t) {
                throw new DispatchException(
                        Status.Code.INTERNAL,
                        "@StateFactory on "
                                + metadata.handlerClass().getSimpleName()
                                + " failed: "
                                + t.getMessage(),
                        t);
            }
        }
        try {
            return stateClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException roe) {
            throw new DispatchException(
                    Status.Code.INTERNAL,
                    "cannot instantiate state "
                            + stateClass.getSimpleName()
                            + " — declare @StateFactory or provide a no-arg constructor",
                    roe);
        }
    }

    /**
     * Replay prior events through the handler's {@code @Applies} methods.
     *
     * <p>Events whose type URL doesn't match any applier are silently skipped — appliers are
     * additive, not total.
     */
    static void replayAppliers(
            Object handler, Object state, HandlerMetadata metadata, EventBook prior) {
        if (prior == null || prior.getPagesCount() == 0) return;
        Map<Class<?>, MethodHandle> appliers = metadata.applies();
        if (appliers.isEmpty()) return;
        // full-name → (eventClass, appliers entry)
        for (EventPage page : prior.getPagesList()) {
            if (!page.hasEvent()) continue;
            String suffix = fullNameFromTypeUrl(page.getEvent().getTypeUrl());
            if (suffix.isEmpty()) continue;
            for (Map.Entry<Class<?>, MethodHandle> entry : appliers.entrySet()) {
                Class<?> evtClass = entry.getKey();
                if (!fullNameOf(evtClass).equals(suffix)) continue;
                Message evt = decodeMessage(page.getEvent(), evtClass);
                try {
                    entry.getValue().invoke(handler, state, evt);
                } catch (Throwable t) {
                    throw new DispatchException(
                            Status.Code.INTERNAL,
                            "@Applies "
                                    + metadata.handlerClass().getSimpleName()
                                    + " failed for "
                                    + suffix
                                    + ": "
                                    + t.getMessage(),
                            t);
                }
                break;
            }
        }
    }

    /**
     * Pack a handler return value (single {@link Message}, {@link EventBook}, {@code Iterable},
     * or {@code null}) into an {@link EventBook} starting at {@code baseSeq}.
     */
    static EventBook packEvents(Object emitted, long baseSeq) {
        EventBook.Builder book = EventBook.newBuilder();
        if (emitted == null) return book.build();
        if (emitted instanceof EventBook eb) {
            return eb;
        }
        if (emitted instanceof Iterable<?> many) {
            long seq = baseSeq;
            for (Object m : many) {
                if (!(m instanceof Message msg)) {
                    throw new DispatchException(
                            Status.Code.INTERNAL,
                            "handler returned non-Message element of type "
                                    + (m == null ? "null" : m.getClass().getSimpleName()));
                }
                book.addPages(pageFor(msg, seq++));
            }
            return book.build();
        }
        if (emitted instanceof Message one) {
            book.addPages(pageFor(one, baseSeq));
            return book.build();
        }
        throw new DispatchException(
                Status.Code.INTERNAL,
                "handler returned unsupported type " + emitted.getClass().getSimpleName());
    }

    /** Proto full name for a {@link Message} class via its descriptor. */
    static String fullNameOf(Class<?> messageClass) {
        try {
            var method = messageClass.getMethod("getDescriptor");
            var descriptor = method.invoke(null);
            return (String) descriptor.getClass().getMethod("getFullName").invoke(descriptor);
        } catch (ReflectiveOperationException roe) {
            throw new DispatchException(
                    Status.Code.INTERNAL,
                    "cannot derive full name for " + messageClass.getName(),
                    roe);
        }
    }

    /** Build the {@link Any} type URL for a message class. */
    static String typeUrlFor(Class<?> messageClass) {
        return TYPE_URL_PREFIX + fullNameOf(messageClass);
    }

    private static EventPage pageFor(Message msg, long seq) {
        Any payload =
                Any.newBuilder()
                        .setTypeUrl(TYPE_URL_PREFIX + msg.getDescriptorForType().getFullName())
                        .setValue(msg.toByteString())
                        .build();
        return EventPage.newBuilder()
                .setHeader(PageHeader.newBuilder().setSequence((int) seq).build())
                .setEvent(payload)
                .build();
    }
}
