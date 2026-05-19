package dev.angzarr.client.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.angzarr.Cover;
import dev.angzarr.client.Destinations;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Applies;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.ProcessManager;
import dev.angzarr.client.annotations.Projector;
import dev.angzarr.client.annotations.Saga;
import dev.angzarr.client.router.CommandHandlerRouter;
import dev.angzarr.client.router.ProcessManagerResponse;
import dev.angzarr.client.router.ProcessManagerRouter;
import dev.angzarr.client.router.ProjectorRouter;
import dev.angzarr.client.router.Router;
import dev.angzarr.client.router.SagaRouter;
import dev.angzarr.client.server.Readiness.BusProbe;
import dev.angzarr.client.server.Readiness.OutputDomainProbe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Cross-language parity for probe-selection per router kind (audit #74).
 *
 * <p>Probes returned per kind:
 *
 * <ul>
 *   <li>CommandHandler / Projector: no output probes.
 *   <li>Saga: one {@code OutputDomainProbe} per {@code sync = true} target; optional {@code
 *       BusProbe} when async targets remain AND {@code ANGZARR_BUS_ENDPOINT} resolves to a
 *       non-empty value.
 *   <li>ProcessManager: one {@code OutputDomainProbe} per {@code syncTargets} entry; optional
 *       {@code BusProbe} on the same conditions as saga.
 * </ul>
 */
class ServerProbesTest {

  // ----- saga fixtures -----

  @Saga(name = "syncS", source = "order", target = "inventory", sync = true)
  static final class SyncSaga {
    @Handles(Cover.class)
    public Object on(Cover c, Destinations d) {
      return null;
    }
  }

  @Saga(name = "asyncS", source = "order", target = "inventory")
  static final class AsyncSaga {
    @Handles(Cover.class)
    public Object on(Cover c, Destinations d) {
      return null;
    }
  }

  @Saga(name = "asyncOther", source = "order", target = "billing")
  static final class AsyncOtherSaga {
    @Handles(Cover.class)
    public Object on(Cover c, Destinations d) {
      return null;
    }
  }

  // ----- PM fixtures -----

  @ProcessManager(
      name = "syncPm",
      pmDomain = "fulfillment",
      sources = {"order"},
      targets = {"inventory", "shipping"},
      syncTargets = {"inventory"},
      state = PmState.class)
  static final class SyncPm {
    @Applies(Cover.class)
    public void apply(PmState s, Cover c) {}

    @Handles(Cover.class)
    public ProcessManagerResponse on(Cover c, PmState s, Destinations d) {
      return ProcessManagerResponse.empty();
    }
  }

  @ProcessManager(
      name = "asyncPm",
      pmDomain = "fulfillment",
      sources = {"order"},
      targets = {"inventory"},
      state = PmState.class)
  static final class AsyncPm {
    @Applies(Cover.class)
    public void apply(PmState s, Cover c) {}

    @Handles(Cover.class)
    public ProcessManagerResponse on(Cover c, PmState s, Destinations d) {
      return ProcessManagerResponse.empty();
    }
  }

  @ProcessManager(
      name = "badPm",
      pmDomain = "fulfillment",
      sources = {"order"},
      targets = {"inventory"},
      syncTargets = {"shipping"}, // NOT in targets — wiring bug
      state = PmState.class)
  static final class BadPm {
    @Handles(Cover.class)
    public ProcessManagerResponse on(Cover c, PmState s, Destinations d) {
      return ProcessManagerResponse.empty();
    }
  }

  public static final class PmState {}

  // ----- CH + projector fixtures (sanity) -----

  @Aggregate(domain = "order", state = AggState.class)
  static final class OrderAgg {
    @Handles(Cover.class)
    public Object handle(Cover c, AggState s, long seq) {
      return null;
    }
  }

  public static final class AggState {}

  @Projector(
      name = "orderProj",
      domains = {"order"})
  static final class OrderProj {
    @Handles(Cover.class)
    public void on(Cover c) {}
  }

  // ----- bus env helper -----

  private String savedBus;

  @AfterEach
  void restoreEnv() {
    if (savedBus == null) {
      System.clearProperty(Readiness.ENV_BUS_ENDPOINT);
    } else {
      System.setProperty(Readiness.ENV_BUS_ENDPOINT, savedBus);
    }
  }

  // ----- saga probe selection -----

  @Test
  void sagaSyncTrueAddsOutputProbeNoBusProbe() {
    SagaRouter router =
        (SagaRouter) Router.newBuilder("s").withHandler(SyncSaga.class, SyncSaga::new).build();
    // No bus env — async path is missing anyway because sync=true.
    var probes = Server.probesForSaga(router, d -> "inventory.svc:1310");
    assertThat(probes).hasSize(1);
    assertThat(probes.get(0)).isInstanceOf(OutputDomainProbe.class);
    assertThat(probes.get(0).name()).isEqualTo("inventory");
  }

  @Test
  void sagaAsyncDefaultAddsBusProbeOnlyWhenEnvSet() {
    SagaRouter router =
        (SagaRouter) Router.newBuilder("s").withHandler(AsyncSaga.class, AsyncSaga::new).build();
    // Env unset → no bus probe → 0 probes.
    var probes = Server.probesForSaga(router, d -> "inventory.svc:1310");
    assertThat(probes).isEmpty();

    // Env set → BusProbe added; no output probe.
    var probesWithBus =
        Server.probesForSagaWithBus(
            router, d -> "inventory.svc:1310", () -> BusProbe.fromEnv(n -> "kafka:9092"));
    assertThat(probesWithBus).hasSize(1);
    assertThat(probesWithBus.get(0)).isInstanceOf(BusProbe.class);
  }

  @Test
  void sagaMixedHandlersSameTargetReportsAsyncTrue() {
    SagaRouter router =
        (SagaRouter)
            Router.newBuilder("s")
                .withHandler(SyncSaga.class, SyncSaga::new)
                .withHandler(AsyncSaga.class, AsyncSaga::new)
                .build();
    assertThat(router.syncOutputDomains()).containsExactly("inventory");
    assertThat(router.hasAsyncOutputs()).isTrue();
    var probesWithBus =
        Server.probesForSagaWithBus(
            router, d -> "inventory.svc:1310", () -> BusProbe.fromEnv(n -> "kafka:9092"));
    assertThat(probesWithBus).hasSize(2);
    assertThat(probesWithBus.get(0)).isInstanceOf(OutputDomainProbe.class);
    assertThat(probesWithBus.get(1)).isInstanceOf(BusProbe.class);
  }

  // ----- PM probe selection -----

  @Test
  void pmEmptySyncTargetsHasNoOutputProbes() {
    ProcessManagerRouter<?> router =
        (ProcessManagerRouter<?>)
            Router.newBuilder("pm").withHandler(AsyncPm.class, AsyncPm::new).build();
    assertThat(router.syncOutputDomains()).isEmpty();
    assertThat(router.hasAsyncOutputs()).isTrue();
    var probes = Server.probesForProcessManager(router, d -> "inventory.svc:1310");
    assertThat(probes).isEmpty();
  }

  @Test
  void pmSyncTargetsAddOutputProbe() {
    ProcessManagerRouter<?> router =
        (ProcessManagerRouter<?>)
            Router.newBuilder("pm").withHandler(SyncPm.class, SyncPm::new).build();
    assertThat(router.syncOutputDomains()).containsExactly("inventory");
    // shipping is in targets but NOT in syncTargets → async outputs present.
    assertThat(router.hasAsyncOutputs()).isTrue();
    var probes = Server.probesForProcessManager(router, d -> "inventory.svc:1310");
    assertThat(probes).hasSize(1);
    assertThat(probes.get(0)).isInstanceOf(OutputDomainProbe.class);
    assertThat(probes.get(0).name()).isEqualTo("inventory");
  }

  @Test
  void pmRejectsSyncTargetNotInTargets() {
    // MED-4.6: post-fix message is the canonical static text and the
    // offending sync_target name is in structured details.
    assertThatThrownBy(() -> Router.newBuilder("pm").withHandler(BadPm.class, BadPm::new).build())
        .isInstanceOf(dev.angzarr.client.router.BuildException.class)
        .satisfies(
            ex -> {
              dev.angzarr.client.router.BuildException be =
                  (dev.angzarr.client.router.BuildException) ex;
              assertThat(be.getCode())
                  .isEqualTo(dev.angzarr.client.error_codes.Codes.HANDLER_FIELD_EMPTY_LIST);
              assertThat(be.getDetails())
                  .containsEntry(dev.angzarr.client.error_codes.Keys.FIELD, "sync_targets");
              assertThat(be.getDetails())
                  .containsEntry(dev.angzarr.client.error_codes.Keys.INPUT, "shipping");
            });
  }

  // ----- CH / projector -----

  @Test
  void commandHandlerHasNoOutputProbes() {
    CommandHandlerRouter<?> router =
        (CommandHandlerRouter<?>)
            Router.newBuilder("a").withHandler(OrderAgg.class, OrderAgg::new).build();
    assertThat(Server.probesForCommandHandler(router)).isEmpty();
  }

  @Test
  void projectorHasNoOutputProbes() {
    ProjectorRouter router =
        (ProjectorRouter)
            Router.newBuilder("p").withHandler(OrderProj.class, OrderProj::new).build();
    assertThat(Server.probesForProjector(router)).isEmpty();
  }
}
