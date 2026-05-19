package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;

import dev.angzarr.Cover;
import dev.angzarr.client.Destinations;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.ProcessManager;
import dev.angzarr.client.annotations.Saga;
import org.junit.jupiter.api.Test;

/**
 * Audit #74 / parity with Python {@code TestSagaRouterSyncOutputDomains}, {@code
 * TestProcessManagerRouterSyncOutputDomains}, and {@code TestHasAsyncOutputs}. These accessors
 * drive the readiness supervisor's per-target probe selection.
 */
class RouterSyncTargetsTest {

  @Saga(name = "async-s", source = "order", target = "inventory")
  static final class AsyncSaga {
    @Handles(Cover.class)
    public Object on(Cover c, Destinations d) {
      return null;
    }
  }

  @Saga(name = "sync-s", source = "order", target = "inventory", sync = true)
  static final class SyncSaga {
    @Handles(Cover.class)
    public Object on(Cover c, Destinations d) {
      return null;
    }
  }

  @Saga(name = "async-s2", source = "order", target = "inventory")
  static final class AsyncSaga2 {
    @Handles(dev.angzarr.UUID.class)
    public Object on(dev.angzarr.UUID c, Destinations d) {
      return null;
    }
  }

  @Test
  void sagaSyncOutputDomainsEmptyWhenSyncFalse() {
    SagaRouter router =
        (SagaRouter) Router.newBuilder("s").withHandler(AsyncSaga.class, AsyncSaga::new).build();
    assertThat(router.outputDomains()).containsExactly("inventory");
    assertThat(router.syncOutputDomains()).isEmpty();
    assertThat(router.hasAsyncOutputs()).isTrue();
  }

  @Test
  void sagaSyncOutputDomainsIncludesTargetWhenSyncTrue() {
    SagaRouter router =
        (SagaRouter) Router.newBuilder("s").withHandler(SyncSaga.class, SyncSaga::new).build();
    assertThat(router.outputDomains()).containsExactly("inventory");
    assertThat(router.syncOutputDomains()).containsExactly("inventory");
    assertThat(router.hasAsyncOutputs()).isFalse();
  }

  @Test
  void sagaMixedHandlersSameTargetHasBothSyncAndAsync() {
    // Mirrors Python's TestHasAsyncOutputs:test_saga_router_with_mixed_handlers_same_target.
    SagaRouter router =
        (SagaRouter)
            Router.newBuilder("sagas")
                .withHandler(SyncSaga.class, SyncSaga::new)
                .withHandler(AsyncSaga2.class, AsyncSaga2::new)
                .build();
    assertThat(router.syncOutputDomains()).containsExactly("inventory");
    assertThat(router.hasAsyncOutputs()).isTrue();
  }

  // -------- PM --------

  @ProcessManager(
      name = "asyncPm",
      pmDomain = "fulfillment",
      sources = {"order"},
      targets = {"inventory", "shipping"},
      state = PmState.class)
  static final class AsyncPm {
    @Handles(Cover.class)
    public ProcessManagerResponse on(Cover c, PmState s, Destinations d) {
      return ProcessManagerResponse.empty();
    }
  }

  @ProcessManager(
      name = "syncPm",
      pmDomain = "fulfillment",
      sources = {"order"},
      targets = {"inventory", "shipping"},
      syncTargets = {"inventory"},
      state = PmState.class)
  static final class SyncPm {
    @Handles(Cover.class)
    public ProcessManagerResponse on(Cover c, PmState s, Destinations d) {
      return ProcessManagerResponse.empty();
    }
  }

  public static final class PmState {}

  @Test
  void pmEmptyWhenNoSyncTargets() {
    ProcessManagerRouter<?> router =
        (ProcessManagerRouter<?>)
            Router.newBuilder("pm").withHandler(AsyncPm.class, AsyncPm::new).build();
    assertThat(router.outputDomains()).containsExactlyInAnyOrder("inventory", "shipping");
    assertThat(router.syncOutputDomains()).isEmpty();
    assertThat(router.hasAsyncOutputs()).isTrue();
  }

  @Test
  void pmReturnsOnlySyncSubset() {
    ProcessManagerRouter<?> router =
        (ProcessManagerRouter<?>)
            Router.newBuilder("pm").withHandler(SyncPm.class, SyncPm::new).build();
    assertThat(router.outputDomains()).containsExactlyInAnyOrder("inventory", "shipping");
    assertThat(router.syncOutputDomains()).containsExactly("inventory");
    // shipping is async still.
    assertThat(router.hasAsyncOutputs()).isTrue();
  }
}
