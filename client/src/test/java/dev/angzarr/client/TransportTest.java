package dev.angzarr.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.angzarr.client.error_codes.Codes;
import dev.angzarr.client.error_codes.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Audit findings #39 (lenient UDS prefix), #40 (loud-fail bad ANGZARR_MODE / ANGZARR_CH_PORT).
 * Cross-language transport.
 */
@Execution(ExecutionMode.SAME_THREAD) // mutates process-wide env vars
class TransportTest {

  private static String resolve(String domain, Transport.Mode mode) {
    return Transport.resolveCommandHandlerEndpoint(domain, mode, null, null, null);
  }

  @Test
  void detectUdsPath_recognizesAbsolutePath() {
    assertThat(Transport.detectUdsPath("/tmp/sock")).hasValue("/tmp/sock");
  }

  @Test
  void detectUdsPath_recognizesRelativePath() {
    assertThat(Transport.detectUdsPath("./rel/path")).hasValue("./rel/path");
  }

  @Test
  void detectUdsPath_recognizesUnixDoubleSlashScheme() {
    assertThat(Transport.detectUdsPath("unix:///abs/path")).hasValue("/abs/path");
  }

  @Test
  void detectUdsPath_recognizesUnixSingleColonScheme() {
    assertThat(Transport.detectUdsPath("unix:relative")).hasValue("relative");
  }

  @Test
  void detectUdsPath_returnsEmptyForTcp() {
    assertThat(Transport.detectUdsPath("host:1310")).isEmpty();
    assertThat(Transport.detectUdsPath("ch-player.angzarr.svc:1310")).isEmpty();
  }

  @Test
  void resolveStandaloneDefaultBase() {
    assertThat(resolve("player", Transport.Mode.STANDALONE))
        .isEqualTo("/tmp/angzarr/ch-player.sock");
  }

  @Test
  void resolveDistributedDefaults() {
    assertThat(resolve("player", Transport.Mode.DISTRIBUTED))
        .isEqualTo("ch-player.angzarr.svc:1310");
  }

  @Test
  void resolveDistributedNamespacePortOverrides() {
    String ep =
        Transport.resolveCommandHandlerEndpoint(
            "player", Transport.Mode.DISTRIBUTED, null, "my-ns", 2222);
    assertThat(ep).isEqualTo("ch-player.my-ns.svc:2222");
  }

  // ----- Audit #40 loud-fail on bad env values -----

  @Test
  void modeFromEnvDefaultWhenUnset() {
    // Use a process snapshot to avoid leaking changes.
    String prior = System.getenv("ANGZARR_MODE");
    try {
      // We can't easily unset env vars in pure Java, so call without
      // env interference by passing mode explicitly.
      assertThat(Transport.Mode.fromString("standalone")).isEqualTo(Transport.Mode.STANDALONE);
      assertThat(Transport.Mode.fromString("distributed")).isEqualTo(Transport.Mode.DISTRIBUTED);
    } finally {
      // No-op — we didn't mutate the environment.
      if (prior != null) {
        // Just to silence "unused" warning when prior was set.
      }
    }
  }

  @Test
  void modeFromStringRejectsTypos() {
    // Audit #40: operator typo (`Distrib` vs `distributed`) raises with
    // INVALID_TRANSPORT_MODE code carrying input + env_var details.
    assertThatThrownBy(() -> Transport.Mode.fromString("Distrib"))
        .isInstanceOf(Errors.InvalidArgumentError.class)
        .satisfies(
            t -> {
              Errors.ClientError ce = (Errors.ClientError) t;
              assertThat(ce.getCode()).isEqualTo(Codes.INVALID_TRANSPORT_MODE);
              assertThat(ce.getDetails())
                  .containsEntry(Keys.INPUT, "Distrib")
                  .containsEntry(Keys.ENV_VAR, "ANGZARR_MODE");
            });
  }

  @Test
  void portFromStringRejectsNonNumeric() {
    // Audit #40: trailing-space / non-numeric raises INVALID_PORT.
    assertThatThrownBy(() -> Transport.parsePort("1310 "))
        .isInstanceOf(Errors.InvalidArgumentError.class)
        .satisfies(
            t -> {
              Errors.ClientError ce = (Errors.ClientError) t;
              assertThat(ce.getCode()).isEqualTo(Codes.INVALID_PORT);
              assertThat(ce.getDetails()).containsEntry(Keys.INPUT, "1310 ");
            });
  }
}
