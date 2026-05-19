package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * HIGH-4.4 — {@link UpcasterRouter} must participate in the {@link Built} sealed interface so
 * callers can pattern-match against it the way they do with the other four router kinds. Pre-fix,
 * {@code Built} permits only 4 variants (CommandHandler / Saga / PM / Projector); upcaster routing
 * was unreachable from the unified router path.
 */
class UpcasterBuiltTest {

  @Test
  void upcasterRouterIsBuilt() {
    UpcasterRouter ur = new UpcasterRouter("orders");
    // Compiles only if UpcasterRouter implements Built.
    Built b = ur;
    assertThat(b.name()).isEqualTo("orders");
  }

  @Test
  void upcasterRouterHandlerCountReflectsRegistrations() {
    UpcasterRouter ur =
        new UpcasterRouter("orders")
            .on("OrderCreatedV1", any -> any)
            .on("OrderUpdatedV1", any -> any);
    assertThat(ur.handlerCount()).isEqualTo(2);
  }

  @Test
  void upcasterRouterOutputDomainsIsEmpty() {
    // Upcasters consume events and emit transformed events; no commands.
    UpcasterRouter ur = new UpcasterRouter("orders");
    assertThat(ur.outputDomains()).isEmpty();
  }

  @Test
  void emptyUpcasterRouterHasZeroHandlers() {
    UpcasterRouter ur = new UpcasterRouter("orders");
    assertThat(ur.handlerCount()).isEqualTo(0);
  }
}
