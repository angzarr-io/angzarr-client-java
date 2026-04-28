/**
 * Tier 5 unified Router: one builder, four typed runtime routers, annotation-driven dispatch.
 *
 * <h2>Shape</h2>
 *
 * <p>Handler classes are plain POJOs annotated with exactly one of
 * {@link dev.angzarr.client.annotations.Aggregate @Aggregate},
 * {@link dev.angzarr.client.annotations.Saga @Saga},
 * {@link dev.angzarr.client.annotations.ProcessManager @ProcessManager}, or
 * {@link dev.angzarr.client.annotations.Projector @Projector}. Methods carry
 * {@link dev.angzarr.client.annotations.Handles @Handles},
 * {@link dev.angzarr.client.annotations.Applies @Applies},
 * {@link dev.angzarr.client.annotations.Rejected @Rejected}, or
 * {@link dev.angzarr.client.annotations.StateFactory @StateFactory}.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * Built built = Router.newBuilder("agg-service")
 *     .withHandler(Player.class, () -> new Player(dbPool))
 *     .withHandler(Hand.class, () -> new Hand(rng))
 *     .build();
 * if (built instanceof CommandHandlerRouter<?> router) {
 *     BusinessResponse response = router.dispatch(ctxCmd);   // R6+
 * }
 * }</pre>
 *
 * <h2>Metadata + caching</h2>
 *
 * <p>{@link HandlerMetadata#of(Class)} extracts (kind, method dispatch tables) once per handler
 * class, cached in a {@link ClassValue}. Method invocations go through {@link
 * java.lang.invoke.MethodHandle}s bound at registration.
 */
package dev.angzarr.client.router;
