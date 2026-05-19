package dev.angzarr.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.angzarr.client.error_codes.Codes;
import dev.angzarr.client.error_codes.Keys;
import dev.angzarr.client.error_codes.Messages;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Audit findings #59 / #60: validation helpers raise structural errors with stable code, static
 * message, and structured details. Mirrors Python's {@code angzarr_client/validation.py} and Rust's
 * {@code validation.rs}.
 */
class ValidationParityTest {

  @Test
  void requirePositiveRaisesWithFieldDetail() {
    assertThatThrownBy(() -> Validation.requirePositive(-1L, "amount"))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessage(Messages.VALUE_NOT_POSITIVE)
        .satisfies(
            t -> {
              Errors.CommandRejectedError cre = (Errors.CommandRejectedError) t;
              Assertions.assertThat(cre.getCode()).isEqualTo(Codes.VALUE_NOT_POSITIVE);
              Assertions.assertThat(cre.getDetails()).containsEntry(Keys.FIELD, "amount");
              Assertions.assertThat(cre.isInvalidArgument()).isTrue();
            });
  }

  @Test
  void requireNonNegativeRaisesWithFieldDetail() {
    assertThatThrownBy(() -> Validation.requireNonNegative(-1L, "balance"))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessage(Messages.VALUE_NOT_NON_NEGATIVE)
        .satisfies(
            t -> {
              Errors.CommandRejectedError cre = (Errors.CommandRejectedError) t;
              Assertions.assertThat(cre.getCode()).isEqualTo(Codes.VALUE_NOT_NON_NEGATIVE);
              Assertions.assertThat(cre.getDetails()).containsEntry(Keys.FIELD, "balance");
            });
  }

  @Test
  void requireExistsRaisesNotFound() {
    // Audit #60: NOT_FOUND, not retryable.
    assertThatThrownBy(() -> Validation.requireExists(false, "Player#42"))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessage(Messages.ENTITY_NOT_FOUND)
        .satisfies(
            t -> {
              Errors.CommandRejectedError cre = (Errors.CommandRejectedError) t;
              Assertions.assertThat(cre.getCode()).isEqualTo(Codes.ENTITY_NOT_FOUND);
              Assertions.assertThat(cre.isNotFound()).isTrue();
              Assertions.assertThat(cre.getDetails()).containsEntry(Keys.CONTEXT, "Player#42");
            });
  }

  @Test
  void requireNotExistsRaisesPreconditionFailed() {
    assertThatThrownBy(() -> Validation.requireNotExists(true, "Player#42"))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessage(Messages.ENTITY_ALREADY_EXISTS)
        .satisfies(
            t -> {
              Errors.CommandRejectedError cre = (Errors.CommandRejectedError) t;
              Assertions.assertThat(cre.getCode()).isEqualTo(Codes.ENTITY_ALREADY_EXISTS);
              Assertions.assertThat(cre.isPreconditionFailed()).isTrue();
            });
  }

  @Test
  void requireNotEmptyStrRaisesValueEmpty() {
    assertThatThrownBy(() -> Validation.requireNotEmpty("", "name"))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessage(Messages.VALUE_EMPTY)
        .satisfies(
            t -> {
              Errors.CommandRejectedError cre = (Errors.CommandRejectedError) t;
              Assertions.assertThat(cre.getCode()).isEqualTo(Codes.VALUE_EMPTY);
              Assertions.assertThat(cre.getDetails()).containsEntry(Keys.FIELD, "name");
            });
  }

  @Test
  void requireNotEmptyCollectionRaisesCollectionEmpty() {
    assertThatThrownBy(() -> Validation.requireNotEmpty(java.util.List.of(), "items"))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessage(Messages.COLLECTION_EMPTY)
        .satisfies(
            t -> {
              Errors.CommandRejectedError cre = (Errors.CommandRejectedError) t;
              Assertions.assertThat(cre.getCode()).isEqualTo(Codes.COLLECTION_EMPTY);
            });
  }

  @Test
  void requireStatusRaisesStatusMismatch() {
    assertThatThrownBy(() -> Validation.requireStatus("OPEN", "CLOSED", "Order#5"))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessage(Messages.STATUS_MISMATCH)
        .satisfies(
            t -> {
              Errors.CommandRejectedError cre = (Errors.CommandRejectedError) t;
              Assertions.assertThat(cre.getCode()).isEqualTo(Codes.STATUS_MISMATCH);
              Assertions.assertThat(cre.isPreconditionFailed()).isTrue();
            });
  }

  @Test
  void requireStatusNotRaisesStatusForbidden() {
    assertThatThrownBy(() -> Validation.requireStatusNot("CLOSED", "CLOSED", "Order#5"))
        .isInstanceOf(Errors.CommandRejectedError.class)
        .hasMessage(Messages.STATUS_FORBIDDEN)
        .satisfies(
            t -> {
              Errors.CommandRejectedError cre = (Errors.CommandRejectedError) t;
              Assertions.assertThat(cre.getCode()).isEqualTo(Codes.STATUS_FORBIDDEN);
            });
  }
}
