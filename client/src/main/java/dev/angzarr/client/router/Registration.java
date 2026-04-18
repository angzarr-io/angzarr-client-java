package dev.angzarr.client.router;

import java.util.function.Supplier;

/**
 * A single {@code (handlerClass, factory, metadata)} registration captured by {@link Router}.
 *
 * <p>Package-private — the router builder produces these and the runtime routers consume them.
 */
record Registration<H>(Class<H> handlerClass, Supplier<? extends H> factory, HandlerMetadata metadata) {}
