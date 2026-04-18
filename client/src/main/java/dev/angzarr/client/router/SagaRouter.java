package dev.angzarr.client.router;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.SagaHandleRequest;
import dev.angzarr.SagaResponse;
import dev.angzarr.client.Destinations;
import dev.angzarr.client.annotations.Saga;
import io.grpc.Status;
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Map;

/**
 * Runtime router for saga components.
 *
 * <p>Produced exclusively by {@link Router#build()}. The trigger is the last event in {@code
 * request.source}; sagas whose {@code @Saga(source = ...)} matches the source book's cover domain
 * AND whose {@code @Handles(E)} matches the trigger's proto type are invoked in registration
 * order. Returned {@link SagaHandlerResponse}s are merged into a single {@link SagaResponse}.
 */
public final class SagaRouter implements Built {

    private final String name;
    private final List<Registration<?>> registrations;

    SagaRouter(String name, List<Registration<?>> registrations) {
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

    public SagaResponse dispatch(SagaHandleRequest request) {
        if (!request.hasSource() || request.getSource().getPagesCount() == 0) {
            return SagaResponse.getDefaultInstance();
        }
        EventBook source = request.getSource();
        String sourceDomain = source.hasCover() ? source.getCover().getDomain() : "";
        EventPage trigger = source.getPages(source.getPagesCount() - 1);
        if (!trigger.hasEvent()) return SagaResponse.getDefaultInstance();
        String suffix = Dispatch.fullNameFromTypeUrl(trigger.getEvent().getTypeUrl());
        if (suffix.isEmpty()) return SagaResponse.getDefaultInstance();

        Destinations destinations = new Destinations(request.getDestinationSequencesMap());

        SagaResponse.Builder response = SagaResponse.newBuilder();
        for (Registration<?> r : registrations) {
            Saga saga = (Saga) r.metadata().kindAnnotation();
            if (!saga.source().equals(sourceDomain)) continue;
            for (Map.Entry<Class<?>, MethodHandle> entry : r.metadata().handles().entrySet()) {
                if (!Dispatch.fullNameOf(entry.getKey()).equals(suffix)) continue;
                Message evt = Dispatch.decodeMessage(trigger.getEvent(), entry.getKey());
                Object handler = r.factory().get();
                Object emitted;
                try {
                    emitted = entry.getValue().invoke(handler, evt, destinations);
                } catch (Throwable t) {
                    throw new DispatchException(
                            Status.Code.INTERNAL,
                            "@Handles on saga "
                                    + r.handlerClass().getSimpleName()
                                    + " failed: "
                                    + t.getMessage(),
                            t);
                }
                mergeSagaOutput(emitted, response);
            }
        }
        return response.build();
    }

    private static void mergeSagaOutput(Object emitted, SagaResponse.Builder response) {
        if (emitted == null) return;
        if (emitted instanceof SagaHandlerResponse shr) {
            response.addAllCommands(shr.getCommands());
            response.addAllEvents(shr.getEvents());
            return;
        }
        if (emitted instanceof dev.angzarr.CommandBook cb) {
            response.addCommands(cb);
            return;
        }
        if (emitted instanceof EventBook eb) {
            response.addEvents(eb);
            return;
        }
        if (emitted instanceof Iterable<?> items) {
            for (Object item : items) {
                if (item instanceof dev.angzarr.CommandBook cb) response.addCommands(cb);
                else if (item instanceof EventBook eb) response.addEvents(eb);
                else
                    throw new DispatchException(
                            Status.Code.INTERNAL,
                            "saga handler returned unsupported element type "
                                    + (item == null ? "null" : item.getClass().getSimpleName()));
            }
            return;
        }
        throw new DispatchException(
                Status.Code.INTERNAL,
                "saga handler returned unsupported type " + emitted.getClass().getSimpleName());
    }
}
