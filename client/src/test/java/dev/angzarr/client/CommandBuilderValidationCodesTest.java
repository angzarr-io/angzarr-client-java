package dev.angzarr.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.angzarr.Cover;
import dev.angzarr.client.error_codes.Codes;
import dev.angzarr.client.error_codes.Keys;
import dev.angzarr.client.error_codes.Messages;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * HIGH-3.2 — CommandBuilder validation errors must stamp the cross-language SCREAMING_SNAKE code +
 * canonical static message + structured details.
 *
 * <p>Pre-fix, Java threw {@code InvalidArgumentError("command type_url not set")} with no code. The
 * 47-code inventory exists precisely so cross-language cucumber assertions can match on a stable
 * identifier; Java callers were the odd one out among Py/Rs/Ja/Cs/Cpp.
 *
 * <p>LOW-3.12 — also pin the canonical {@code "sequence not set (call with_sequence)"} message text
 * (snake_case) matching Py/Rs/Cpp.
 */
class CommandBuilderValidationCodesTest {

  @Test
  void missingTypeUrlStampsCommandTypeUrlMissingCode() {
    CommandBuilder b = new CommandBuilder(null, "orders", UUID.randomUUID()).withSequence(0);
    // payload-bearing call deliberately omitted so type_url is missing.
    Errors.InvalidArgumentError err = assertThrows(Errors.InvalidArgumentError.class, b::build);
    assertThat(err.getMessage()).isEqualTo(Messages.COMMAND_TYPE_URL_MISSING);
    assertThat(err.getCode()).isEqualTo(Codes.COMMAND_TYPE_URL_MISSING);
    assertThat(err.getDetails()).containsEntry(Keys.FIELD, "type_url");
    assertThat(err.getDetails()).containsEntry(Keys.DOMAIN, "orders");
  }

  @Test
  void missingPayloadStampsCommandPayloadMissingCode() {
    // Only type_url present; no payload. We cheat by constructing a
    // typeUrl-only builder via direct field access through a real call
    // and intentionally omitting message via a workaround. The simplest
    // way is to set typeUrl through withCommand and clear payload — but
    // withCommand sets both. The builder requires both via withCommand;
    // so to exercise the payload check we need to bypass. Instead we
    // assert the message+code on a separately-constructed test path:
    // construct a builder that has type_url but not payload by using
    // reflection.
    CommandBuilder b = new CommandBuilder(null, "orders", UUID.randomUUID()).withSequence(0);
    // Use reflection to set typeUrl but not payload.
    try {
      java.lang.reflect.Field tu = CommandBuilder.class.getDeclaredField("typeUrl");
      tu.setAccessible(true);
      tu.set(b, "type.googleapis.com/test.Foo");
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    Errors.InvalidArgumentError err = assertThrows(Errors.InvalidArgumentError.class, b::build);
    assertThat(err.getMessage()).isEqualTo(Messages.COMMAND_PAYLOAD_MISSING);
    assertThat(err.getCode()).isEqualTo(Codes.COMMAND_PAYLOAD_MISSING);
    assertThat(err.getDetails()).containsEntry(Keys.FIELD, "payload");
    assertThat(err.getDetails()).containsEntry(Keys.DOMAIN, "orders");
  }

  @Test
  void missingSequenceStampsCommandSequenceMissingCode() {
    CommandBuilder b =
        new CommandBuilder(null, "orders", UUID.randomUUID())
            .withCommand(
                "type.googleapis.com/angzarr_client.proto.angzarr.Cover",
                Cover.getDefaultInstance());
    // sequence not set
    Errors.InvalidArgumentError err = assertThrows(Errors.InvalidArgumentError.class, b::build);
    // LOW-3.12: canonical snake_case message text
    assertThat(err.getMessage()).isEqualTo(Messages.COMMAND_SEQUENCE_MISSING);
    assertThat(err.getCode()).isEqualTo(Codes.COMMAND_SEQUENCE_MISSING);
    assertThat(err.getDetails()).containsEntry(Keys.FIELD, "sequence");
    assertThat(err.getDetails()).containsEntry(Keys.DOMAIN, "orders");
  }

  @Test
  void canonicalCommandSequenceMissingMessageIsSnakeCase() {
    // LOW-3.12 — Py/Rs/Cpp use "with_sequence"; Java had "withSequence".
    // Cross-language byte equality on Messages.* is the contract.
    assertThat(Messages.COMMAND_SEQUENCE_MISSING)
        .isEqualTo("sequence not set (call with_sequence)");
  }
}
