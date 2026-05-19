package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.angzarr.Cover;
import dev.angzarr.client.Destinations;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.ProcessManager;
import dev.angzarr.client.annotations.Projector;
import dev.angzarr.client.annotations.Saga;
import dev.angzarr.client.error_codes.Codes;
import dev.angzarr.client.error_codes.Keys;
import dev.angzarr.client.error_codes.Messages;
import org.junit.jupiter.api.Test;

/**
 * Spec convergence for Router build-time validation:
 *
 * <ul>
 *   <li>MED-4.6 — PM sync-target-not-in-targets must stamp a canonical code + canonical message +
 *       structured details, not dynamic-interpolated text with no code.
 *   <li>MED-4.7 — A class without a kind annotation must stamp {@link Codes#HANDLER_UNKNOWN_KIND} +
 *       canonical message, not get collapsed into a generic "cannot register" wrap.
 *   <li>MED-4.8 — Empty {@code domain=""} on {@code @Aggregate} must fail build with {@link
 *       Codes#HANDLER_FIELD_EMPTY_STRING}; same for empty {@code sources}/{@code targets} on PM and
 *       {@code domains} on Projector ({@link Codes#HANDLER_FIELD_EMPTY_LIST}).
 * </ul>
 */
class RouterValidationCanonicalErrorsTest {

  public static final class S {}

  @Aggregate(domain = "", state = S.class)
  static final class EmptyDomainAgg {
    @Handles(Cover.class)
    public Object on(Cover c, S s, long seq) {
      return null;
    }
  }

  @Saga(name = "", source = "order", target = "billing")
  static final class EmptyNameSaga {
    @Handles(Cover.class)
    public Object on(Cover c, Destinations d) {
      return null;
    }
  }

  @ProcessManager(
      name = "pm1",
      pmDomain = "fulfillment",
      sources = {},
      targets = {"inventory"},
      state = S.class)
  static final class EmptySourcesPm {
    @Handles(Cover.class)
    public ProcessManagerResponse on(Cover c, S s, Destinations d) {
      return ProcessManagerResponse.empty();
    }
  }

  @ProcessManager(
      name = "pm2",
      pmDomain = "fulfillment",
      sources = {"order"},
      targets = {"inventory"},
      syncTargets = {"missing"},
      state = S.class)
  static final class SyncTargetNotInTargets {
    @Handles(Cover.class)
    public ProcessManagerResponse on(Cover c, S s, Destinations d) {
      return ProcessManagerResponse.empty();
    }
  }

  @Projector(
      name = "p",
      domains = {})
  static final class EmptyDomainsProjector {
    @Handles(Cover.class)
    public void on(Cover c) {}
  }

  /** No kind annotation. */
  static final class NotAHandler {
    @Handles(Cover.class)
    public Object on(Cover c) {
      return null;
    }
  }

  // --- MED-4.7 ---

  @Test
  void handlerWithoutKindAnnotationStampsHandlerUnknownKindCode() {
    BuildException ex =
        assertThrows(
            BuildException.class,
            () -> Router.newBuilder("r").withHandler(NotAHandler.class, NotAHandler::new).build());
    assertThat(ex.getMessage()).isEqualTo(Messages.HANDLER_UNKNOWN_KIND);
    assertThat(ex.getCode()).isEqualTo(Codes.HANDLER_UNKNOWN_KIND);
    assertThat(ex.getDetails()).containsEntry(Keys.HANDLER_CLASS, NotAHandler.class.getName());
  }

  // --- MED-4.6 ---

  @Test
  void pmSyncTargetNotInTargetsStampsHandlerFieldEmptyOrCanonicalCode() {
    BuildException ex =
        assertThrows(
            BuildException.class,
            () ->
                Router.newBuilder("pm")
                    .withHandler(SyncTargetNotInTargets.class, SyncTargetNotInTargets::new)
                    .build());
    assertThat(ex.getCode()).isEqualTo(Codes.HANDLER_FIELD_EMPTY_LIST);
    assertThat(ex.getMessage()).isEqualTo(Messages.HANDLER_FIELD_EMPTY_LIST);
    // Details identify the offending field + value + router name for
    // downstream tooling.
    assertThat(ex.getDetails()).containsEntry(Keys.FIELD, "sync_targets");
    assertThat(ex.getDetails()).containsEntry(Keys.ROUTER_NAME, "pm");
  }

  // --- MED-4.8 ---

  @Test
  void aggregateEmptyDomainStampsHandlerFieldEmptyString() {
    BuildException ex =
        assertThrows(
            BuildException.class,
            () ->
                Router.newBuilder("r")
                    .withHandler(EmptyDomainAgg.class, EmptyDomainAgg::new)
                    .build());
    assertThat(ex.getCode()).isEqualTo(Codes.HANDLER_FIELD_EMPTY_STRING);
    assertThat(ex.getMessage()).isEqualTo(Messages.HANDLER_FIELD_EMPTY_STRING);
    assertThat(ex.getDetails()).containsEntry(Keys.FIELD, "domain");
  }

  @Test
  void sagaEmptyNameStampsHandlerFieldEmptyString() {
    BuildException ex =
        assertThrows(
            BuildException.class,
            () ->
                Router.newBuilder("r")
                    .withHandler(EmptyNameSaga.class, EmptyNameSaga::new)
                    .build());
    assertThat(ex.getCode()).isEqualTo(Codes.HANDLER_FIELD_EMPTY_STRING);
    assertThat(ex.getMessage()).isEqualTo(Messages.HANDLER_FIELD_EMPTY_STRING);
    assertThat(ex.getDetails()).containsEntry(Keys.FIELD, "name");
  }

  @Test
  void pmEmptySourcesStampsHandlerFieldEmptyList() {
    BuildException ex =
        assertThrows(
            BuildException.class,
            () ->
                Router.newBuilder("r")
                    .withHandler(EmptySourcesPm.class, EmptySourcesPm::new)
                    .build());
    assertThat(ex.getCode()).isEqualTo(Codes.HANDLER_FIELD_EMPTY_LIST);
    assertThat(ex.getMessage()).isEqualTo(Messages.HANDLER_FIELD_EMPTY_LIST);
    assertThat(ex.getDetails()).containsEntry(Keys.FIELD, "sources");
  }

  @Test
  void projectorEmptyDomainsStampsHandlerFieldEmptyList() {
    BuildException ex =
        assertThrows(
            BuildException.class,
            () ->
                Router.newBuilder("r")
                    .withHandler(EmptyDomainsProjector.class, EmptyDomainsProjector::new)
                    .build());
    assertThat(ex.getCode()).isEqualTo(Codes.HANDLER_FIELD_EMPTY_LIST);
    assertThat(ex.getMessage()).isEqualTo(Messages.HANDLER_FIELD_EMPTY_LIST);
    assertThat(ex.getDetails()).containsEntry(Keys.FIELD, "domains");
  }
}
