package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;

import dev.angzarr.Cover;
import dev.angzarr.SagaResponse;
import dev.angzarr.client.Destinations;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.ProcessManager;
import dev.angzarr.client.annotations.Projector;
import dev.angzarr.client.annotations.Saga;
import org.junit.jupiter.api.Test;

/**
 * Audit findings #42 / parity P3.2: every {@link Built} router exposes {@code handlerCount()} and
 * {@code outputDomains()} accessors.
 *
 * <p>Saga / PM expose deduped target domains from their declared {@code @Saga(target=...)} /
 * {@code @ProcessManager(targets={...})}. CommandHandler / Projector return empty (read-only /
 * write-events-only).
 */
class RouterAccessorsTest {

  public static class S {}

  @Aggregate(domain = "orders", state = S.class)
  public static class Order {
    @Handles(Cover.class)
    public Cover handle(Cover c, S s, long seq) {
      return c;
    }
  }

  @Saga(name = "order-saga", source = "orders", target = "inventory")
  public static class OrderSaga {
    @Handles(Cover.class)
    public SagaHandlerResponse handle(Cover c, Destinations d) {
      return SagaHandlerResponse.empty();
    }
  }

  @ProcessManager(
      name = "pmgr",
      pmDomain = "fulfillment",
      sources = {"orders"},
      targets = {"shipping", "billing"},
      state = S.class)
  public static class ShippingPM {
    @Handles(Cover.class)
    public ProcessManagerResponse handle(Cover c, S state, Destinations dest) {
      return ProcessManagerResponse.empty();
    }
  }

  @Projector(
      name = "prj",
      domains = {"orders"})
  public static class OrderProjector {
    @Handles(Cover.class)
    public void onCover(Cover c) {}
  }

  @Test
  void commandHandlerExposesHandlerCount() {
    Built built = Router.newBuilder("agg").withHandler(Order.class, Order::new).build();
    assertThat(built.handlerCount()).isEqualTo(1);
    // CommandHandler emits events, not commands — no output_domains.
    assertThat(built.outputDomains()).isEmpty();
  }

  @Test
  void sagaExposesTargetAsOutputDomain() {
    Built built = Router.newBuilder("sagas").withHandler(OrderSaga.class, OrderSaga::new).build();
    assertThat(built.handlerCount()).isEqualTo(1);
    assertThat(built.outputDomains()).containsExactly("inventory");
  }

  @Test
  void processManagerExposesAllTargetsDeduped() {
    Built built = Router.newBuilder("pms").withHandler(ShippingPM.class, ShippingPM::new).build();
    assertThat(built.handlerCount()).isEqualTo(1);
    assertThat(built.outputDomains()).containsExactly("shipping", "billing");
  }

  @Test
  void projectorReturnsEmptyOutputDomains() {
    Built built =
        Router.newBuilder("prj").withHandler(OrderProjector.class, OrderProjector::new).build();
    assertThat(built.handlerCount()).isEqualTo(1);
    assertThat(built.outputDomains()).isEmpty();
  }

  @Test
  void sagaRespondsToHandlerCountAfterMultipleRegistrations() {
    // No multi-handler restriction on sagas — they fan out legitimately.
    Built built =
        Router.newBuilder("sagas")
            .withHandler(OrderSaga.class, OrderSaga::new)
            .withHandler(OrderSaga.class, OrderSaga::new)
            .build();
    assertThat(built.handlerCount()).isEqualTo(2);
    // Deduped output domains: same target across both handlers.
    assertThat(built.outputDomains()).containsExactly("inventory");
  }

  @Test
  void emittedSagaResponseDefaultIsEmpty() {
    SagaResponse empty = SagaResponse.getDefaultInstance();
    assertThat(empty.getCommandsCount()).isZero();
  }
}
