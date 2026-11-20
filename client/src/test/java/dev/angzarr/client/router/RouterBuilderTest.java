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

    @Projector(name = "prj", domains = {"order"})
    static class OrderProjector {}

    @Projector(name = "empty", domains = {})
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
        assertThatThrownBy(() -> Router.newBuilder("empty").build())
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("empty")
                .hasMessageContaining("no handlers");
    }

    @Test
    void registeringUnannotatedClassThrowsBuildException() {
        assertThatThrownBy(
                        () -> Router.newBuilder("bad").withHandler(NoKind.class, NoKind::new))
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("NoKind");
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
        Built built =
                Router.newBuilder("sagas").withHandler(OrderSaga.class, OrderSaga::new).build();
        assertThat(built).isInstanceOf(SagaRouter.class);
    }

    @Test
    void processManagerClassProducesProcessManagerRouter() {
        Built built =
                Router.newBuilder("pms")
                        .withHandler(FulfillmentPm.class, FulfillmentPm::new)
                        .build();
        assertThat(built).isInstanceOf(ProcessManagerRouter.class);
    }

    @Test
    void projectorClassProducesProjectorRouter() {
        Built built =
                Router.newBuilder("prjs")
                        .withHandler(OrderProjector.class, OrderProjector::new)
                        .build();
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
        assertThatThrownBy(
                        () ->
                                Router.newBuilder("prj")
                                        .withHandler(
                                                EmptyDomainsProjector.class,
                                                EmptyDomainsProjector::new)
                                        .build())
                .isInstanceOf(BuildException.class)
                .hasMessageContaining("domain");
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
