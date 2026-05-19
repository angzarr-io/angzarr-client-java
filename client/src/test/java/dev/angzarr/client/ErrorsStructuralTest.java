package dev.angzarr.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.angzarr.client.error_codes.Codes;
import dev.angzarr.client.error_codes.Keys;
import dev.angzarr.client.error_codes.Messages;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Audit finding #59: every error carries a static {@code message}, a stable {@code code}, and
 * structured {@code details}. Mirrors Python's {@code errors.py::CommandRejectedError} and Rust's
 * {@code ClientError::Rejected}.
 */
class ErrorsStructuralTest {

  @Test
  void clientErrorCarriesCodeAndDetails() {
    var err =
        new Errors.ClientError(
            Messages.MISSING_DESTINATION_SEQUENCE,
            null,
            Codes.MISSING_DESTINATION_SEQUENCE,
            Map.of(Keys.DOMAIN, "inventory"));
    assertThat(err.getCode()).isEqualTo("MISSING_DESTINATION_SEQUENCE");
    assertThat(err.getDetails()).containsEntry("domain", "inventory");
    // Static message — no interpolation.
    assertThat(err.getMessage()).isEqualTo("no sequence for destination domain");
  }

  @Test
  void clientErrorEmptyByDefault() {
    var err = new Errors.ClientError("generic");
    assertThat(err.getCode()).isEmpty();
    assertThat(err.getDetails()).isEmpty();
  }

  @Test
  void commandRejectedPreconditionFailedFactory() {
    var err =
        Errors.CommandRejectedError.preconditionFailed(
            Codes.ENTITY_ALREADY_EXISTS,
            Messages.ENTITY_ALREADY_EXISTS,
            Map.of(Keys.DOMAIN, "player"));
    assertThat(err.getCode()).isEqualTo("ENTITY_ALREADY_EXISTS");
    assertThat(err.getMessage()).isEqualTo("entity already exists");
    assertThat(err.isPreconditionFailed()).isTrue();
    assertThat(err.isInvalidArgument()).isFalse();
    assertThat(err.getDetails()).containsEntry("domain", "player");
  }

  @Test
  void commandRejectedInvalidArgumentFactory() {
    var err =
        Errors.CommandRejectedError.invalidArgument(
            Codes.VALUE_NOT_POSITIVE, Messages.VALUE_NOT_POSITIVE, Map.of(Keys.FIELD, "amount"));
    assertThat(err.getCode()).isEqualTo("VALUE_NOT_POSITIVE");
    assertThat(err.isInvalidArgument()).isTrue();
    assertThat(err.isPreconditionFailed()).isFalse();
  }

  @Test
  void commandRejectedNotFoundFactory() {
    var err =
        Errors.CommandRejectedError.notFound(
            Codes.ENTITY_NOT_FOUND, Messages.ENTITY_NOT_FOUND, Map.of());
    assertThat(err.getCode()).isEqualTo("ENTITY_NOT_FOUND");
    assertThat(err.isNotFound()).isTrue();
  }

  @Test
  void commandRejectedErrorIsAClientError() {
    // P2.3: pin the IS-A contract — callers may `catch (ClientError e)` and
    // see CommandRejectedError.
    var err = new Errors.CommandRejectedError("rejected");
    assertThat(err).isInstanceOf(Errors.ClientError.class);
    assertThatThrownBy(
            () -> {
              throw err;
            })
        .isInstanceOf(Errors.ClientError.class);
  }
}
