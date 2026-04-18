package dev.angzarr.client.router;

/**
 * Sealed marker for a router produced by {@link Router#build()}.
 *
 * <p>Permits exactly the four typed runtime routers. Callers pattern-match or {@code instanceof}
 * to recover the specific type, e.g.:
 *
 * <pre>{@code
 * Built built = Router.newBuilder("agg").withHandler(Player.class, Player::new).build();
 * if (built instanceof CommandHandlerRouter<?> router) {
 *     BusinessResponse resp = router.dispatch(cmd);
 * }
 * }</pre>
 */
public sealed interface Built
        permits CommandHandlerRouter, SagaRouter, ProcessManagerRouter, ProjectorRouter {

    /** The router name (identifier supplied to {@link Router#newBuilder(String)}). */
    String name();
}
