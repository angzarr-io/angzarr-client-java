package dev.angzarr.client.router;

import java.util.List;

/**
 * Runtime router for projector components.
 *
 * <p>Produced exclusively by {@link Router#build()} when all registered handler classes carry
 * {@link dev.angzarr.client.annotations.Projector @Projector}. Dispatch logic lands in R13.
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
}
