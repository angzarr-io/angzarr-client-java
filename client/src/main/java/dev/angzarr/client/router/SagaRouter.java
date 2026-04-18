package dev.angzarr.client.router;

import java.util.List;

/**
 * Runtime router for saga components.
 *
 * <p>Produced exclusively by {@link Router#build()} when all registered handler classes carry
 * {@link dev.angzarr.client.annotations.Saga @Saga}. Dispatch logic lands in R11.
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
}
