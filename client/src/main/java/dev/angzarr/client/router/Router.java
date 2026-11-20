package dev.angzarr.client.router;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Builder for the unified Tier 5 router.
 *
 * <p>Each call to {@link #withHandler(Class, Supplier)} registers a handler class (annotated with
 * {@code @Aggregate}, {@code @Saga}, {@code @ProcessManager}, or {@code @Projector}) together with
 * a zero-arg factory. Factories are <b>not</b> invoked at registration or build time — only
 * during dispatch, so each request gets a fresh (or pooled) handler instance and the built router
 * is safe to share across threads.
 *
 * <p>{@link #build()} infers the kind from the registered classes and returns a typed runtime
 * router. All registered classes must share one kind; mixing kinds raises {@link BuildException}.
 */
public final class Router {

    private final String name;
    private final List<Registration<?>> registrations = new ArrayList<>();

    private Router(String name) {
        this.name = Objects.requireNonNull(name, "router name");
    }

    /** Start building a router with the given name. */
    public static Router newBuilder(String name) {
        return new Router(name);
    }

    /**
     * Register a handler class with a factory that produces a fresh instance on each dispatch.
     *
     * <p>The factory is not invoked at registration time. Validation of the class (that it carries
     * a kind annotation) is deferred to {@link #build()}; this mirrors the Python reference where
     * registration stores the tuple and build-time inference catches problems.
     */
    public <H> Router withHandler(Class<H> cls, Supplier<? extends H> factory) {
        Objects.requireNonNull(cls, "handler class");
        Objects.requireNonNull(factory, "handler factory");
        HandlerMetadata metadata;
        try {
            metadata = HandlerMetadata.of(cls);
        } catch (IllegalStateException ise) {
            throw new BuildException(
                    "cannot register " + cls.getName() + ": " + ise.getMessage(), ise);
        }
        registrations.add(new Registration<>(cls, factory, metadata));
        return this;
    }

    /** Finalize the router. */
    public Built build() {
        if (registrations.isEmpty()) {
            throw new BuildException(
                    "cannot build router '"
                            + name
                            + "' with no handlers — call withHandler() at least once");
        }
        EnumSet<HandlerMetadata.Kind> kinds = EnumSet.noneOf(HandlerMetadata.Kind.class);
        for (Registration<?> r : registrations) {
            kinds.add(r.metadata().kind());
        }
        if (kinds.size() > 1) {
            throw new BuildException(
                    "cannot mix kinds in one router '"
                            + name
                            + "' — got "
                            + kinds
                            + "; use separate routers for each kind");
        }
        HandlerMetadata.Kind kind = kinds.iterator().next();
        return switch (kind) {
            case COMMAND_HANDLER -> new CommandHandlerRouter<>(name, List.copyOf(registrations));
            case SAGA -> new SagaRouter(name, List.copyOf(registrations));
            case PROCESS_MANAGER -> new ProcessManagerRouter<>(name, List.copyOf(registrations));
            case PROJECTOR -> {
                validateProjectorDomains(name, registrations);
                yield new ProjectorRouter(name, List.copyOf(registrations));
            }
        };
    }

    private static void validateProjectorDomains(String name, List<Registration<?>> registrations) {
        for (Registration<?> r : registrations) {
            dev.angzarr.client.annotations.Projector p =
                    (dev.angzarr.client.annotations.Projector) r.metadata().kindAnnotation();
            if (p.domains().length == 0) {
                throw new BuildException(
                        "router '"
                                + name
                                + "': projector "
                                + r.handlerClass().getSimpleName()
                                + " must declare at least one domain in @Projector(domains=...)");
            }
        }
    }

    String name() {
        return name;
    }

    List<Registration<?>> registrations() {
        return registrations;
    }
}
