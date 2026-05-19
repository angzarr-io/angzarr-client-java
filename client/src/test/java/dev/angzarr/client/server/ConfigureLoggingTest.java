// Phase D-Java MED-5.10 — TDD pins for Server.configureLogging.
//
// Spec contract (cross-language-spec.md MED-5.10):
//   "Logging configure_* helper is Py/Rs only. Add to ... Java (slf4j-simple init)."
//
// Python (`angzarr_client/server.py:38`) wires structlog with JSON output +
// ISO-8601 timestamps. Rust (`src/server.rs:57`) wires `tracing-subscriber` with
// JSON output + the RUST_LOG env filter (falling back to "info").
//
// Java parity choice: configure slf4j-simple via its documented system properties,
// matching the precedent set by the other two: ISO-8601 timestamps, level reads
// from env (`ANGZARR_LOG_LEVEL`, default "info"). slf4j-simple reads these on
// first logger creation, so we must set them *before* any logger is constructed.
//
// Idempotency: callers may invoke at every entry point (test, main, server runner).
// Re-invocation must not throw, must not regress level config, must be a no-op
// if the level matches.
package dev.angzarr.client.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Server.configureLogging — MED-5.10 cross-language parity")
class ConfigureLoggingTest {

  private static final String SLF4J_SHOW_DATETIME = "org.slf4j.simpleLogger.showDateTime";
  private static final String SLF4J_DATETIME_FORMAT = "org.slf4j.simpleLogger.dateTimeFormat";
  private static final String SLF4J_LEVEL = "org.slf4j.simpleLogger.defaultLogLevel";
  private static final String SLF4J_LEVEL_IN_BRACKETS = "org.slf4j.simpleLogger.levelInBrackets";
  private static final String SLF4J_SHOW_THREAD = "org.slf4j.simpleLogger.showThreadName";
  private static final String ENV_LOG_LEVEL = "ANGZARR_LOG_LEVEL";

  private Properties saved;

  @BeforeEach
  void snapshotProps() {
    saved = new Properties();
    for (String k :
        new String[] {
          SLF4J_SHOW_DATETIME,
          SLF4J_DATETIME_FORMAT,
          SLF4J_LEVEL,
          SLF4J_LEVEL_IN_BRACKETS,
          SLF4J_SHOW_THREAD,
        }) {
      String v = System.getProperty(k);
      if (v != null) saved.setProperty(k, v);
      System.clearProperty(k);
    }
  }

  @AfterEach
  void restoreProps() {
    for (String k :
        new String[] {
          SLF4J_SHOW_DATETIME,
          SLF4J_DATETIME_FORMAT,
          SLF4J_LEVEL,
          SLF4J_LEVEL_IN_BRACKETS,
          SLF4J_SHOW_THREAD,
        }) {
      String v = saved.getProperty(k);
      if (v == null) System.clearProperty(k);
      else System.setProperty(k, v);
    }
  }

  @Test
  @DisplayName(
      "sets showDateTime=true so ISO timestamps appear in output (parity with Py structlog"
          + " TimeStamper)")
  void enablesDateTimeColumn() {
    Server.configureLogging();
    assertThat(System.getProperty(SLF4J_SHOW_DATETIME)).isEqualTo("true");
  }

  @Test
  @DisplayName("sets ISO-8601 dateTimeFormat (parity with Python `iso` and Rust ChronoLocal)")
  void usesIso8601Format() {
    Server.configureLogging();
    // SimpleDateFormat ISO-8601 with millis + zone, matching Python's
    // structlog ISO output ("2025-01-15T10:30:00.123Z").
    assertThat(System.getProperty(SLF4J_DATETIME_FORMAT)).isEqualTo("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
  }

  @Test
  @DisplayName(
      "default level is 'info' when ANGZARR_LOG_LEVEL is unset (parity with Rust default `info`)")
  void defaultLevelInfo() {
    Server.configureLogging(name -> null);
    assertThat(System.getProperty(SLF4J_LEVEL)).isEqualTo("info");
  }

  @Test
  @DisplayName("honors ANGZARR_LOG_LEVEL env var when present")
  void honorsAngzarrLogLevelEnv() {
    Server.configureLogging(name -> "ANGZARR_LOG_LEVEL".equals(name) ? "debug" : null);
    assertThat(System.getProperty(SLF4J_LEVEL)).isEqualTo("debug");
  }

  @Test
  @DisplayName("lower-cases env var value (slf4j-simple expects lowercase level names)")
  void lowercasesLevelValue() {
    Server.configureLogging(name -> "ANGZARR_LOG_LEVEL".equals(name) ? "WARN" : null);
    assertThat(System.getProperty(SLF4J_LEVEL)).isEqualTo("warn");
  }

  @Test
  @DisplayName("trims whitespace from env value")
  void trimsLevelValue() {
    Server.configureLogging(name -> "ANGZARR_LOG_LEVEL".equals(name) ? "  trace  " : null);
    assertThat(System.getProperty(SLF4J_LEVEL)).isEqualTo("trace");
  }

  @Test
  @DisplayName(
      "rejects unknown level names by falling back to info (defensive: avoid slf4j parse warnings)")
  void unknownLevelFallsBackToInfo() {
    Server.configureLogging(name -> "ANGZARR_LOG_LEVEL".equals(name) ? "verbose" : null);
    assertThat(System.getProperty(SLF4J_LEVEL)).isEqualTo("info");
  }

  @Test
  @DisplayName("empty env value falls back to info (treats blank as unset)")
  void emptyLevelFallsBackToInfo() {
    Server.configureLogging(name -> "ANGZARR_LOG_LEVEL".equals(name) ? "" : null);
    assertThat(System.getProperty(SLF4J_LEVEL)).isEqualTo("info");
  }

  @Test
  @DisplayName("idempotent: second invocation does not throw and preserves the chosen level")
  void idempotent() {
    Server.configureLogging(name -> "ANGZARR_LOG_LEVEL".equals(name) ? "debug" : null);
    assertThatCode(
            () ->
                Server.configureLogging(name -> "ANGZARR_LOG_LEVEL".equals(name) ? "debug" : null))
        .doesNotThrowAnyException();
    assertThat(System.getProperty(SLF4J_LEVEL)).isEqualTo("debug");
  }

  @Test
  @DisplayName("does not overwrite explicitly user-set slf4j-simple properties (caller-set wins)")
  void respectsExplicitUserOverride() {
    System.setProperty(SLF4J_LEVEL, "error");
    Server.configureLogging(name -> "ANGZARR_LOG_LEVEL".equals(name) ? "debug" : null);
    // User-set value should remain — configureLogging only fills in defaults.
    assertThat(System.getProperty(SLF4J_LEVEL)).isEqualTo("error");
  }

  @Test
  @DisplayName("accepts all canonical slf4j-simple level names (trace, debug, info, warn, error)")
  void acceptsAllCanonicalLevels() {
    for (String level : new String[] {"trace", "debug", "info", "warn", "error"}) {
      System.clearProperty(SLF4J_LEVEL);
      Server.configureLogging(name -> "ANGZARR_LOG_LEVEL".equals(name) ? level : null);
      assertThat(System.getProperty(SLF4J_LEVEL))
          .as("level %s should be accepted", level)
          .isEqualTo(level);
    }
  }

  @Test
  @DisplayName(
      "no-arg overload reads from process env (smoke-test, does not assert specific value)")
  void noArgOverloadCompiles() {
    // Just exercise the no-arg path. The actual env-read value depends on the
    // process; we only assert the call doesn't throw and produces a non-null
    // level (defaulting to "info" if ANGZARR_LOG_LEVEL is not set in this JVM).
    assertThatCode(Server::configureLogging).doesNotThrowAnyException();
    assertThat(System.getProperty(SLF4J_LEVEL)).isNotNull();
  }
}
