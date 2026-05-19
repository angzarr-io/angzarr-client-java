package dev.angzarr.client.error_codes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Cross-language error-inventory parity (audit findings #59, #75-#86, #40).
 *
 * <p>Mirrors Python's {@code angzarr_client/error_codes/} and Rust's {@code src/error_codes.rs}.
 * Every constant here must match the corresponding Python/Rust constant by name AND value, so error
 * codes / messages / detail-keys form a single cross-language vocabulary.
 */
class ErrorInventoryTest {

  @Test
  void codesUseStableScreamingSnakeIdentifiers() {
    // Audit #75-#86: same code identifier across languages.
    assertThat(Codes.VALUE_NOT_POSITIVE).isEqualTo("VALUE_NOT_POSITIVE");
    assertThat(Codes.VALUE_NOT_NON_NEGATIVE).isEqualTo("VALUE_NOT_NON_NEGATIVE");
    assertThat(Codes.COMMAND_TYPE_URL_MISSING).isEqualTo("COMMAND_TYPE_URL_MISSING");
    assertThat(Codes.COMMAND_PAYLOAD_MISSING).isEqualTo("COMMAND_PAYLOAD_MISSING");
    assertThat(Codes.COMMAND_SEQUENCE_MISSING).isEqualTo("COMMAND_SEQUENCE_MISSING");
    assertThat(Codes.MISSING_DESTINATION_SEQUENCE).isEqualTo("MISSING_DESTINATION_SEQUENCE");
    assertThat(Codes.DUPLICATE_COMMAND_HANDLER).isEqualTo("DUPLICATE_COMMAND_HANDLER");
    assertThat(Codes.MIXED_HANDLER_KINDS).isEqualTo("MIXED_HANDLER_KINDS");
    assertThat(Codes.ROUTER_NO_HANDLERS).isEqualTo("ROUTER_NO_HANDLERS");
    assertThat(Codes.NO_HANDLER_REGISTERED).isEqualTo("NO_HANDLER_REGISTERED");
    assertThat(Codes.TIMESTAMP_PARSE_FAILED).isEqualTo("TIMESTAMP_PARSE_FAILED");
    // Audit #40 symmetry.
    assertThat(Codes.INVALID_TRANSPORT_MODE).isEqualTo("INVALID_TRANSPORT_MODE");
    assertThat(Codes.INVALID_PORT).isEqualTo("INVALID_PORT");
  }

  @Test
  void messagesAreStaticHumanReadableStrings() {
    // Audit #59: static message string per code — same exact text across
    // languages so cucumber assertions and log greps work.
    assertThat(Messages.COMMAND_TYPE_URL_MISSING).isEqualTo("command type_url not set");
    assertThat(Messages.COMMAND_SEQUENCE_MISSING)
        .isEqualTo("sequence not set (call with_sequence)");
    assertThat(Messages.MISSING_DESTINATION_SEQUENCE)
        .isEqualTo("no sequence for destination domain");
    assertThat(Messages.ROUTER_NO_HANDLERS).isEqualTo("no handlers registered on Router");
    assertThat(Messages.MIXED_HANDLER_KINDS)
        .isEqualTo("cannot mix handler kinds in one Router — all handlers must share a kind");
    assertThat(Messages.DUPLICATE_COMMAND_HANDLER)
        .isEqualTo("duplicate command handler registration for (domain, type_url)");
    assertThat(Messages.NO_HANDLER_REGISTERED)
        .isEqualTo("no handler registered for the given (domain, type_url)");
    assertThat(Messages.INVALID_TRANSPORT_MODE).isEqualTo("invalid transport mode env value");
    assertThat(Messages.INVALID_PORT).isEqualTo("invalid port env value");
  }

  @Test
  void keysAreStableSnakeCase() {
    assertThat(Keys.DOMAIN).isEqualTo("domain");
    assertThat(Keys.TYPE_URL).isEqualTo("type_url");
    assertThat(Keys.ROUTER_NAME).isEqualTo("router_name");
    assertThat(Keys.HANDLER_CLASS).isEqualTo("handler_class");
    assertThat(Keys.HANDLER_KIND).isEqualTo("handler_kind");
    assertThat(Keys.OTHER_KIND).isEqualTo("other_kind");
    assertThat(Keys.ENV_VAR).isEqualTo("env_var");
  }
}
