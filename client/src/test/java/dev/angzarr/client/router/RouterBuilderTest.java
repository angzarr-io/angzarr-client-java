package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.ProcessManager;
import dev.angzarr.client.annotations.Projector;
import dev.angzarr.client.annotations.Saga;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** R3–R5 — {@link Router} builder: registration, mode inference, build-time validation. */
class RouterBuilderTest {

  static class State {}

  @Aggregate(domain = "player", state = State.class)
  static class PlayerAgg {}

  @Aggregate(domain = "hand", state = State.class)
  static class HandAgg {}

  @Saga(name = "saga", source = "order", target = "inventory")
  static class OrderSaga {}

  @ProcessManager(
      name = "pm",
      pmDomain = "fulfillment",
      sources = {"order"},
      targets = {"shipping"},
      state = State.class)
  static class FulfillmentPm {}

  @Projector(
      name = "prj",
      domains = {"order"})
  static class OrderProjector {}

  @Projector(
      name = "empty",
      domains = {})
  static class EmptyDomainsProjector {}

  static class NoKind {}

  // --- R3: factories aren't invoked prematurely ---

  @Test
  void factoryNotInvokedAtRegistration() {
    AtomicInteger calls = new AtomicInteger();
    Router.newBuilder("agg")
        .withHandler(
            PlayerAgg.class,
            () -> {
              calls.incrementAndGet();
              return new PlayerAgg();
            });
    assertThat(calls).hasValue(0);
  }

  @Test
  void factoryNotInvokedAtBuild() {
    AtomicInteger calls = new AtomicInteger();
    Router.newBuilder("agg")
        .withHandler(
            PlayerAgg.class,
            () -> {
              calls.incrementAndGet();
              return new PlayerAgg();
            })
        .build();
    assertThat(calls).hasValue(0);
  }

  @Test
  void emptyBuilderThrowsBuildException() {
    // Audit #59 structural form: static message + stable code +
    // structured details["router_name"]. No interpolation in message.
    assertThatThrownBy(() -> Router.newBuilder("empty").build())
        .isInstanceOf(BuildException.class)
        .hasMessage("no handlers registered on Router")
        .satisfies(
            t -> {
              BuildException be = (BuildException) t;
              assertThat(be.getCode())
                  .isEqualTo(dev.angzarr.client.error_codes.Codes.ROUTER_NO_HANDLERS);
              assertThat(be.getDetails())
                  .containsEntry(dev.angzarr.client.error_codes.Keys.ROUTER_NAME, "empty");
            });
  }

  @Test
  void registeringUnannotatedClassThrowsBuildException() {
    // MED-4.7: post-fix the message is the canonical static
    // Messages.HANDLER_UNKNOWN_KIND text; the handler class is now
    // surfaced via the structured details map.
    assertThatThrownBy(() -> Router.newBuilder("bad").withHandler(NoKind.class, NoKind::new))
        .isInstanceOf(BuildException.class)
        .satisfies(
            ex -> {
              BuildException be = (BuildException) ex;
              assertThat(be.getCode())
                  .isEqualTo(dev.angzarr.client.error_codes.Codes.HANDLER_UNKNOWN_KIND);
              assertThat(be.getDetails())
                  .containsEntry(
                      dev.angzarr.client.error_codes.Keys.HANDLER_CLASS, NoKind.class.getName());
            });
  }

  // --- R4: mode inference ---

  @Test
  void homogeneousAggregatesProduceCommandHandlerRouter() {
    Built built =
        Router.newBuilder("agg")
            .withHandler(PlayerAgg.class, PlayerAgg::new)
            .withHandler(HandAgg.class, HandAgg::new)
            .build();
    assertThat(built).isInstanceOf(CommandHandlerRouter.class);
    assertThat(built.name()).isEqualTo("agg");
  }

  @Test
  void sagaClassProducesSagaRouter() {
    Built built = Router.newBuilder("sagas").withHandler(OrderSaga.class, OrderSaga::new).build();
    assertThat(built).isInstanceOf(SagaRouter.class);
  }

  @Test
  void processManagerClassProducesProcessManagerRouter() {
    Built built =
        Router.newBuilder("pms").withHandler(FulfillmentPm.class, FulfillmentPm::new).build();
    assertThat(built).isInstanceOf(ProcessManagerRouter.class);
  }

  @Test
  void projectorClassProducesProjectorRouter() {
    Built built =
        Router.newBuilder("prjs").withHandler(OrderProjector.class, OrderProjector::new).build();
    assertThat(built).isInstanceOf(ProjectorRouter.class);
  }

  @Test
  void mixedKindsThrowsBuildException() {
    assertThatThrownBy(
            () ->
                Router.newBuilder("mixed")
                    .withHandler(PlayerAgg.class, PlayerAgg::new)
                    .withHandler(OrderSaga.class, OrderSaga::new)
                    .build())
        .isInstanceOf(BuildException.class)
        .hasMessageContaining("mix");
  }

  // --- R5: per-kind validation ---

  @Test
  void projectorWithEmptyDomainsThrows() {
    // MED-4.8: post-fix uses canonical HANDLER_FIELD_EMPTY_LIST code +
    // structured details identifying the offending field.
    assertThatThrownBy(
            () ->
                Router.newBuilder("prj")
                    .withHandler(EmptyDomainsProjector.class, EmptyDomainsProjector::new)
                    .build())
        .isInstanceOf(BuildException.class)
        .satisfies(
            ex -> {
              BuildException be = (BuildException) ex;
              assertThat(be.getCode())
                  .isEqualTo(dev.angzarr.client.error_codes.Codes.HANDLER_FIELD_EMPTY_LIST);
              assertThat(be.getDetails())
                  .containsEntry(dev.angzarr.client.error_codes.Keys.FIELD, "domains");
            });
  }

  @Test
  void duplicateRegistrationsAllowed() {
    Built built =
        Router.newBuilder("agg")
            .withHandler(PlayerAgg.class, PlayerAgg::new)
            .withHandler(PlayerAgg.class, PlayerAgg::new)
            .build();
    assertThat(((CommandHandlerRouter<?>) built).registrations()).hasSize(2);
  }
}
