package dev.angzarr.client.router;

import java.util.List;

/**
 * Sealed marker for a router produced by {@link Router#build()}.
 *
 * <p>Permits exactly the four typed runtime routers. Callers pattern-match or {@code instanceof} to
 * recover the specific type, e.g.:
 *
 * <pre>{@code
 * Built built = Router.newBuilder("agg").withHandler(Player.class, Player::new).build();
 * if (built instanceof CommandHandlerRouter<?> router) {
 *     BusinessResponse resp = router.dispatch(cmd);
 * }
 * }</pre>
 */
public sealed interface Built
    permits CommandHandlerRouter,
        SagaRouter,
        ProcessManagerRouter,
        ProjectorRouter,
        UpcasterRouter {

  /** The router name (identifier supplied to {@link Router#newBuilder(String)}). */
  String name();

  /**
   * Number of registered handler factories.
   *
   * <p>Audit #42 / parity P3.2: cross-language helper exposed on every {@code Built} router.
   * Mirrors Python's {@code BuiltRouter.handler_count} / {@code __len__} and Rust's {@code
   * Router::handler_count}.
   */
  int handlerCount();

  /**
   * Output / target domains this router emits commands to (as declared on the registered handlers).
   *
   * <ul>
   *   <li>CommandHandler / Projector: empty (write events, not commands).
   *   <li>Saga: deduped {@code @Saga(target=...)} from each handler.
   *   <li>ProcessManager: deduped {@code @ProcessManager(targets={...})}.
   * </ul>
   *
   * <p>Mirrors Python's {@code BuiltRouter.output_domains()} and Rust's {@code
   * Router::output_domains}.
   */
  List<String> outputDomains();
}
