package dev.angzarr.client;

import dev.angzarr.client.error_codes.Codes;
import dev.angzarr.client.error_codes.Keys;
import dev.angzarr.client.error_codes.Messages;
import java.util.Map;
import java.util.Optional;

/**
 * Transport-mode resolver for command handler coordinator endpoints.
 *
 * <p>Mirrors the Python ({@code client.py}) and Rust ({@code transport.rs}) implementations so the
 * same env vars resolve to the same endpoints across languages.
 *
 * <h2>Environment variables</h2>
 *
 * <ul>
 *   <li>{@code ANGZARR_MODE}: {@code "standalone"} (UDS) or {@code "distributed"} (K8s DNS).
 *       Default: distributed.
 *   <li>{@code ANGZARR_UDS_BASE}: base path for Unix domain sockets. Default: {@code /tmp/angzarr}.
 *   <li>{@code ANGZARR_NAMESPACE}: Kubernetes namespace. Default: {@code angzarr}.
 *   <li>{@code ANGZARR_CH_PORT}: gRPC port for distributed mode. Default: {@code 1310}.
 * </ul>
 *
 * <p>Audit finding #40: env-var bad-input policy is loud-fail. An unset variable falls through to
 * its default; a SET-but-unrecognized value throws {@link Errors.InvalidArgumentError} so operator
 * typos surface at startup instead of silently misrouting.
 */
public final class Transport {

  private Transport() {}

  /** Default UDS base directory. */
  public static final String DEFAULT_UDS_BASE = "/tmp/angzarr";

  /** Default Kubernetes namespace. */
  public static final String DEFAULT_NAMESPACE = "angzarr";

  /** Default command handler coordinator port. */
  public static final int DEFAULT_CH_PORT = 1310;

  /** Default transport mode when neither arg nor env var is set. */
  public static final Mode DEFAULT_TRANSPORT_MODE = Mode.DISTRIBUTED;

  public static final String ENV_MODE = "ANGZARR_MODE";
  public static final String ENV_UDS_BASE = "ANGZARR_UDS_BASE";
  public static final String ENV_NAMESPACE = "ANGZARR_NAMESPACE";
  public static final String ENV_CH_PORT = "ANGZARR_CH_PORT";

  /** Transport mode for gRPC connections. */
  public enum Mode {
    /** Unix Domain Sockets for local-process communication. */
    STANDALONE,
    /** TCP via Kubernetes DNS for cluster communication. */
    DISTRIBUTED;

    /**
     * Parse a mode-name string. Audit #40: loud-fail on operator typos.
     *
     * @throws Errors.InvalidArgumentError if {@code s} is not exactly {@code "standalone"} or
     *     {@code "distributed"}.
     */
    public static Mode fromString(String s) {
      switch (s) {
        case "standalone":
          return STANDALONE;
        case "distributed":
          return DISTRIBUTED;
        default:
          throw new Errors.InvalidArgumentError(
              Messages.INVALID_TRANSPORT_MODE,
              Codes.INVALID_TRANSPORT_MODE,
              Map.of(Keys.INPUT, s, Keys.ENV_VAR, ENV_MODE));
      }
    }

    /**
     * Resolve mode from {@link #ENV_MODE}.
     *
     * <p>Unset → {@link #DEFAULT_TRANSPORT_MODE}. Recognized value → that variant. Anything else →
     * throws (audit #40).
     */
    public static Mode fromEnv() {
      String v = System.getenv(ENV_MODE);
      if (v == null) {
        return DEFAULT_TRANSPORT_MODE;
      }
      return fromString(v);
    }
  }

  /**
   * Detect a UDS endpoint and return the socket path, or empty for TCP.
   *
   * <p>Audit finding #39: lenient prefix detection matching Python's {@code _create_channel}.
   * Recognized forms:
   *
   * <ul>
   *   <li>{@code /abs/path} — absolute path, no scheme
   *   <li>{@code ./rel/path} — relative path, no scheme
   *   <li>{@code unix:relative/path} — gRPC URI, relative
   *   <li>{@code unix:/abs/path} — gRPC URI, absolute (single-slash)
   *   <li>{@code unix:///abs/path} — gRPC URI, absolute with empty authority
   * </ul>
   *
   * <p>Anything else is treated as TCP.
   */
  public static Optional<String> detectUdsPath(String endpoint) {
    if (endpoint == null) {
      return Optional.empty();
    }
    if (endpoint.startsWith("unix://")) {
      return Optional.of(endpoint.substring("unix://".length()));
    }
    if (endpoint.startsWith("unix:")) {
      return Optional.of(endpoint.substring("unix:".length()));
    }
    if (endpoint.startsWith("/") || endpoint.startsWith("./")) {
      return Optional.of(endpoint);
    }
    return Optional.empty();
  }

  /**
   * Parse a port number string, throwing on non-numeric or out-of-range.
   *
   * <p>Audit #40: loud-fail. {@code "1310 "} (trailing space typo) raises.
   */
  public static int parsePort(String s) {
    try {
      int p = Integer.parseInt(s);
      if (p < 0 || p > 65535) {
        throw new NumberFormatException("port out of range: " + p);
      }
      return p;
    } catch (NumberFormatException nfe) {
      throw new Errors.InvalidArgumentError(
          Messages.INVALID_PORT,
          Codes.INVALID_PORT,
          Map.of(Keys.INPUT, s, Keys.ENV_VAR, ENV_CH_PORT));
    }
  }

  /**
   * Resolve a domain name to a command handler coordinator endpoint.
   *
   * <ul>
   *   <li>{@link Mode#STANDALONE} → {@code {udsBase}/ch-{domain}.sock}
   *   <li>{@link Mode#DISTRIBUTED} → {@code ch-{domain}.{namespace}.svc:{port}}
   * </ul>
   *
   * <p>Resolution precedence for each value matches Python's {@code resolve_ch_endpoint}: <b>env
   * var &gt; explicit arg &gt; default</b>.
   *
   * @param domain Domain name (required).
   * @param mode Transport mode; if null, auto-detected from {@link #ENV_MODE}.
   * @param udsBase Override UDS base path; if null, env var or {@link #DEFAULT_UDS_BASE} is used.
   * @param namespace Override K8s namespace; if null, env var or {@link #DEFAULT_NAMESPACE} is
   *     used.
   * @param port Override TCP port; if null, env var or {@link #DEFAULT_CH_PORT} is used.
   * @return Endpoint string suitable for connection setup.
   */
  public static String resolveCommandHandlerEndpoint(
      String domain, Mode mode, String udsBase, String namespace, Integer port) {
    Mode resolvedMode = mode != null ? mode : Mode.fromEnv();
    if (resolvedMode == Mode.STANDALONE) {
      String envBase = System.getenv(ENV_UDS_BASE);
      String base = envBase != null ? envBase : (udsBase != null ? udsBase : DEFAULT_UDS_BASE);
      return base + "/ch-" + domain + ".sock";
    }
    // DISTRIBUTED
    String envNs = System.getenv(ENV_NAMESPACE);
    String ns = envNs != null ? envNs : (namespace != null ? namespace : DEFAULT_NAMESPACE);
    String envPort = System.getenv(ENV_CH_PORT);
    int p = envPort != null ? parsePort(envPort) : (port != null ? port : DEFAULT_CH_PORT);
    return "ch-" + domain + "." + ns + ".svc:" + p;
  }
}
