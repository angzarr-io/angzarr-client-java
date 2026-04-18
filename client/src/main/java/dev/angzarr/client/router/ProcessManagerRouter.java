package dev.angzarr.client.router;

import java.util.List;

/**
 * Runtime router for process manager components.
 *
 * <p>Produced exclusively by {@link Router#build()} when all registered handler classes carry
 * {@link dev.angzarr.client.annotations.ProcessManager @ProcessManager}. Dispatch logic lands in
 * R12.
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
}
