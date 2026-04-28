package dev.angzarr.client.router;

import com.google.protobuf.Message;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.Projection;
import dev.angzarr.client.annotations.Projector;
import io.grpc.Status;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Runtime router for projector components.
 *
 * <p>Produced exclusively by {@link Router#build()}. Every projector whose
 * {@code @Projector(domains = [...])} includes the book's cover domain is instantiated once per
 * dispatch and reused across every event in the book (so implementations can batch writes).
 * Handler methods are {@code @Handles(EventClass)} → {@code void (EventClass)} — return values
 * are ignored.
 */
public final class ProjectorRouter implements Built {

    private final String name;
    private final List<Registration<?>> registrations;

    ProjectorRouter(String name, List<Registration<?>> registrations) {
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

    public Projection dispatch(EventBook events) {
        String domain = events.hasCover() ? events.getCover().getDomain() : "";

        // Live projectors: one instance per matching registration, shared across the book.
        List<LiveProjector> live = new ArrayList<>();
        for (Registration<?> r : registrations) {
            Projector p = (Projector) r.metadata().kindAnnotation();
            if (!Arrays.asList(p.domains()).contains(domain)) continue;
            if (r.metadata().handles().isEmpty()) continue;
            live.add(new LiveProjector(r.factory().get(), r.metadata().handles(), r.handlerClass()));
        }

        for (EventPage page : events.getPagesList()) {
            if (!page.hasEvent()) continue;
            String suffix = Dispatch.fullNameFromTypeUrl(page.getEvent().getTypeUrl());
            if (suffix.isEmpty()) continue;
            for (LiveProjector proj : live) {
                for (Map.Entry<Class<?>, MethodHandle> entry : proj.handles.entrySet()) {
                    if (!Dispatch.fullNameOf(entry.getKey()).equals(suffix)) continue;
                    Message evt = Dispatch.decodeMessage(page.getEvent(), entry.getKey());
                    try {
                        entry.getValue().invoke(proj.instance, evt);
                    } catch (Throwable t) {
                        throw new DispatchException(
                                Status.Code.INTERNAL,
                                "@Handles on projector "
                                        + proj.handlerClass.getSimpleName()
                                        + " failed: "
                                        + t.getMessage(),
                                t);
                    }
                }
            }
        }

        Projection.Builder result = Projection.newBuilder();
        if (events.hasCover()) result.setCover(events.getCover());
        return result.build();
    }

    private record LiveProjector(
            Object instance, Map<Class<?>, MethodHandle> handles, Class<?> handlerClass) {}
}
