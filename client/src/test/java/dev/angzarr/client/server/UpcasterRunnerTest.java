package dev.angzarr.client.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import dev.angzarr.client.router.Built;
import dev.angzarr.client.router.UpcasterRouter;
import org.junit.jupiter.api.Test;

/**
 * HIGH-5.3 — Java must expose an upcaster server runner so all 5 router kinds (CH / Saga / PM /
 * Projector / Upcaster) have a {@code start*Server} entry point. Pre-fix, {@code
 * Server.startServer(Built, ...)} threw {@link IllegalArgumentException} for an UpcasterRouter
 * input and there was no {@code startUpcasterServer} method.
 *
 * <p>Combined with HIGH-4.4 (UpcasterRouter now in {@link Built} permits), the dispatch path should
 * accept upcaster routers.
 *
 * <p>These tests assert the API surface — they don't actually bind a gRPC server (no kindService is
 * provided for upcasters in the standard wiring).
 */
class UpcasterRunnerTest {

  @Test
  void healthNameUpcasterIsCanonical() {
    assertThat(Server.HEALTH_NAME_UPCASTER)
        .isEqualTo("angzarr_client.proto.angzarr.v1.UpcasterService");
  }

  @Test
  void probesForUpcasterIsEmpty() {
    // Upcasters are read-side transformers; no sync output domains,
    // so no OutputDomainProbes. Mirrors probesForProjector.
    UpcasterRouter ur = new UpcasterRouter("orders").on("OrderCreatedV1", any -> any);
    assertThat(Server.probesForUpcaster(ur)).isEmpty();
  }

  @Test
  void startServerDispatchAcceptsUpcasterRouter() {
    // Pre-fix: Server.startServer(Built, ...) threw
    // IllegalArgumentException for an upcaster. We don't actually bind
    // the server here (would require a BindableService), but we want
    // the dispatch table to know the variant. The reflective probe
    // below confirms a startUpcasterServer method is present.
    UpcasterRouter ur = new UpcasterRouter("orders");
    Built b = ur; // compiles only with HIGH-4.4 applied
    assertThat(b).isInstanceOf(UpcasterRouter.class);
  }

  @Test
  void startUpcasterServerMethodIsPresent() {
    // Reflective surface check — actually invoking would require
    // BindableService and a free TCP port, out of scope for unit test.
    assertDoesNotThrow(
        () ->
            Server.class.getMethod(
                "startUpcasterServer",
                UpcasterRouter.class,
                io.grpc.BindableService.class,
                int.class));
  }
}
