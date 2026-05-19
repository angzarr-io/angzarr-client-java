package dev.angzarr.client;

import dev.angzarr.client.error_codes.Codes;
import dev.angzarr.client.error_codes.Keys;
import dev.angzarr.client.error_codes.Messages;
import java.util.Collection;
import java.util.Map;

/**
 * Validation helpers for command-handler precondition checks.
 *
 * <p>Audit findings #59 / #60: every error carries a stable {@code code} (from {@link Codes}), a
 * static {@code message} (from {@link Messages}), and structured {@code details} (keyed by {@link
 * Keys}). Mirrors Python's {@code angzarr_client/validation.py} and Rust's {@code validation.rs}.
 */
public final class Validation {

  private Validation() {}

  /**
   * Require that an aggregate exists (caller-supplied predicate).
   *
   * <p>Raises {@code NOT_FOUND} (audit #18) — not retryable, refetching events cannot change the
   * outcome.
   */
  public static void requireExists(boolean exists, String context) {
    if (!exists) {
      throw Errors.CommandRejectedError.notFound(
          Codes.ENTITY_NOT_FOUND, Messages.ENTITY_NOT_FOUND, Map.of(Keys.CONTEXT, context));
    }
  }

  /** Back-compat overload (no context). */
  public static void requireExists(boolean exists) {
    requireExists(exists, "");
  }

  /** Require that an aggregate does NOT exist (caller-supplied predicate). */
  public static void requireNotExists(boolean exists, String context) {
    if (exists) {
      throw Errors.CommandRejectedError.preconditionFailed(
          Codes.ENTITY_ALREADY_EXISTS,
          Messages.ENTITY_ALREADY_EXISTS,
          Map.of(Keys.CONTEXT, context));
    }
  }

  /** Back-compat overload (no context). */
  public static void requireNotExists(boolean exists) {
    requireNotExists(exists, "");
  }

  /** Require that a value is greater than zero. */
  public static void requirePositive(long value, String fieldName) {
    if (value <= 0) {
      throw Errors.CommandRejectedError.invalidArgument(
          Codes.VALUE_NOT_POSITIVE, Messages.VALUE_NOT_POSITIVE, Map.of(Keys.FIELD, fieldName));
    }
  }

  public static void requirePositive(double value, String fieldName) {
    if (value <= 0) {
      throw Errors.CommandRejectedError.invalidArgument(
          Codes.VALUE_NOT_POSITIVE, Messages.VALUE_NOT_POSITIVE, Map.of(Keys.FIELD, fieldName));
    }
  }

  /** Require that a value is zero or greater. */
  public static void requireNonNegative(long value, String fieldName) {
    if (value < 0) {
      throw Errors.CommandRejectedError.invalidArgument(
          Codes.VALUE_NOT_NON_NEGATIVE,
          Messages.VALUE_NOT_NON_NEGATIVE,
          Map.of(Keys.FIELD, fieldName));
    }
  }

  public static void requireNonNegative(double value, String fieldName) {
    if (value < 0) {
      throw Errors.CommandRejectedError.invalidArgument(
          Codes.VALUE_NOT_NON_NEGATIVE,
          Messages.VALUE_NOT_NON_NEGATIVE,
          Map.of(Keys.FIELD, fieldName));
    }
  }

  /** Require that a string is not null and non-empty. */
  public static void requireNotEmpty(String value, String fieldName) {
    if (value == null || value.isEmpty()) {
      throw Errors.CommandRejectedError.invalidArgument(
          Codes.VALUE_EMPTY, Messages.VALUE_EMPTY, Map.of(Keys.FIELD, fieldName));
    }
  }

  /** Require that a collection is not null and non-empty. */
  public static void requireNotEmpty(Collection<?> collection, String fieldName) {
    if (collection == null || collection.isEmpty()) {
      throw Errors.CommandRejectedError.invalidArgument(
          Codes.COLLECTION_EMPTY, Messages.COLLECTION_EMPTY, Map.of(Keys.FIELD, fieldName));
    }
  }

  /** Require that the current status matches the expected value. */
  public static void requireStatus(String actual, String expected, String context) {
    if (!java.util.Objects.equals(actual, expected)) {
      throw Errors.CommandRejectedError.preconditionFailed(
          Codes.STATUS_MISMATCH,
          Messages.STATUS_MISMATCH,
          Map.of(
              Keys.CONTEXT, context,
              Keys.EXPECTED, expected,
              Keys.ACTUAL, actual));
    }
  }

  /** Enum-overloaded variant — converts to String via {@code .name()}. */
  public static <T extends Enum<T>> void requireStatus(T actual, T expected, String context) {
    requireStatus(
        actual == null ? "" : actual.name(), expected == null ? "" : expected.name(), context);
  }

  /** Require that the current status is NOT the forbidden value. */
  public static void requireStatusNot(String actual, String forbidden, String context) {
    if (java.util.Objects.equals(actual, forbidden)) {
      throw Errors.CommandRejectedError.preconditionFailed(
          Codes.STATUS_FORBIDDEN,
          Messages.STATUS_FORBIDDEN,
          Map.of(Keys.CONTEXT, context, Keys.ACTUAL, actual));
    }
  }

  public static <T extends Enum<T>> void requireStatusNot(T actual, T forbidden, String context) {
    requireStatusNot(
        actual == null ? "" : actual.name(), forbidden == null ? "" : forbidden.name(), context);
  }
}
