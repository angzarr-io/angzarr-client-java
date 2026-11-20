package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.ProcessManager;
import dev.angzarr.client.annotations.Projector;
import dev.angzarr.client.annotations.Saga;
import org.junit.jupiter.api.Test;

/** R1 — class-level kind annotations and metadata extraction. */
class HandlerMetadataTest {

    // --- Dummy state types ---
    static class PlayerState {}
    static class FulfillmentState {}

    // --- Happy-path classes (one kind each) ---
    @Aggregate(domain = "player", state = PlayerState.class)
    static class PlayerHandler {}

    @Saga(name = "saga-order-fulfillment", source = "order", target = "inventory")
    static class OrderFulfillmentSaga {}

    @ProcessManager(
            name = "pm-fulfillment",
            pmDomain = "fulfillment",
            sources = {"order", "inventory"},
            targets = {"shipping"},
            state = FulfillmentState.class)
    static class FulfillmentPm {}

    @Projector(name = "prj-output", domains = {"order", "hand"})
    static class OutputProjector {}

    // --- Conflict classes (two kinds) ---
    @Aggregate(domain = "x", state = PlayerState.class)
    @Saga(name = "s", source = "a", target = "b")
    static class AggregateAndSaga {}

    @Projector(name = "p", domains = {"a"})
    @ProcessManager(
            name = "pm",
            pmDomain = "d",
            sources = {"a"},
            targets = {"b"},
            state = PlayerState.class)
    static class ProjectorAndPm {}

    // --- Unannotated ---
    static class NoKind {}

    // --- Aggregate ---
    @Test
    void aggregateAnnotationCarriesDomainAndState() {
        Aggregate a = PlayerHandler.class.getAnnotation(Aggregate.class);
        assertThat(a).isNotNull();
        assertThat(a.domain()).isEqualTo("player");
        assertThat(a.state()).isEqualTo(PlayerState.class);
    }

    @Test
    void aggregateHandlerMetadataKindIsCommandHandler() {
        HandlerMetadata md = HandlerMetadata.of(PlayerHandler.class);
        assertThat(md.kind()).isEqualTo(HandlerMetadata.Kind.COMMAND_HANDLER);
        assertThat(md.handlerClass()).isEqualTo(PlayerHandler.class);
    }

    // --- Saga ---
    @Test
    void sagaAnnotationCarriesFields() {
        Saga s = OrderFulfillmentSaga.class.getAnnotation(Saga.class);
        assertThat(s).isNotNull();
        assertThat(s.name()).isEqualTo("saga-order-fulfillment");
        assertThat(s.source()).isEqualTo("order");
        assertThat(s.target()).isEqualTo("inventory");
    }

    @Test
    void sagaHandlerMetadataKindIsSaga() {
        HandlerMetadata md = HandlerMetadata.of(OrderFulfillmentSaga.class);
        assertThat(md.kind()).isEqualTo(HandlerMetadata.Kind.SAGA);
    }

    // --- ProcessManager ---
    @Test
    void processManagerAnnotationCarriesFields() {
        ProcessManager pm = FulfillmentPm.class.getAnnotation(ProcessManager.class);
        assertThat(pm).isNotNull();
        assertThat(pm.name()).isEqualTo("pm-fulfillment");
        assertThat(pm.pmDomain()).isEqualTo("fulfillment");
        assertThat(pm.sources()).containsExactly("order", "inventory");
        assertThat(pm.targets()).containsExactly("shipping");
        assertThat(pm.state()).isEqualTo(FulfillmentState.class);
    }

    @Test
    void processManagerHandlerMetadataKindIsProcessManager() {
        HandlerMetadata md = HandlerMetadata.of(FulfillmentPm.class);
        assertThat(md.kind()).isEqualTo(HandlerMetadata.Kind.PROCESS_MANAGER);
    }

    // --- Projector ---
    @Test
    void projectorAnnotationCarriesFields() {
        Projector p = OutputProjector.class.getAnnotation(Projector.class);
        assertThat(p).isNotNull();
        assertThat(p.name()).isEqualTo("prj-output");
        assertThat(p.domains()).containsExactly("order", "hand");
    }

    @Test
    void projectorHandlerMetadataKindIsProjector() {
        HandlerMetadata md = HandlerMetadata.of(OutputProjector.class);
        assertThat(md.kind()).isEqualTo(HandlerMetadata.Kind.PROJECTOR);
    }

    // --- Conflicts ---
    @Test
    void stackingAggregateAndSagaThrows() {
        assertThatThrownBy(() -> HandlerMetadata.of(AggregateAndSaga.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AggregateAndSaga")
                .hasMessageContaining("@Aggregate")
                .hasMessageContaining("@Saga");
    }

    @Test
    void stackingProjectorAndPmThrows() {
        assertThatThrownBy(() -> HandlerMetadata.of(ProjectorAndPm.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ProjectorAndPm");
    }

    // --- Missing kind ---
    @Test
    void unannotatedClassThrows() {
        assertThatThrownBy(() -> HandlerMetadata.of(NoKind.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NoKind")
                .hasMessageContaining("@Aggregate");
    }

    // --- Caching ---
    @Test
    void handlerMetadataIsCachedPerClass() {
        HandlerMetadata a = HandlerMetadata.of(PlayerHandler.class);
        HandlerMetadata b = HandlerMetadata.of(PlayerHandler.class);
        assertThat(a).isSameAs(b);
    }
}
