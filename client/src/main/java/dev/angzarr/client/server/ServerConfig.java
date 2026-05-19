package dev.angzarr.client.server;

import java.util.function.Function;

/**
 * Configuration for a gRPC runner. Cross-language alias for Rust's {@code ServerConfig { port,
 * uds_path }} and Python's {@code ServerConfig(port, uds_path)}.
 *
 * <p>UDS mode is selected when all three of {@code UDS_BASE_PATH}, {@code SERVICE_NAME}, and {@code
 * DOMAIN} are present; otherwise TCP, with port read from {@code PORT} or {@code GRPC_PORT},
 * falling back to the supplied {@code defaultPort}.
 *
 * <p>This record is <b>pure</b> — no filesystem side effects. The runner is responsible for
 * creating the parent directory and removing any stale socket file at the chosen path.
 */
public record ServerConfig(int port, String udsPath) {

  /** Resolve from process env. See {@link #fromEnv(int, Function)} for details. */
  public static ServerConfig fromEnv(int defaultPort) {
    return fromEnv(defaultPort, System::getenv);
  }

  /**
   * Resolve from an injected env reader (test seam). UDS triad ({@code UDS_BASE_PATH} + {@code
   * SERVICE_NAME} + {@code DOMAIN}) wins when all three are set; otherwise {@code PORT} / {@code
   * GRPC_PORT}; otherwise {@code defaultPort}.
   */
  public static ServerConfig fromEnv(int defaultPort, Function<String, String> env) {
    String base = env.apply("UDS_BASE_PATH");
    String svc = env.apply("SERVICE_NAME");
    String domain = env.apply("DOMAIN");
    if (notBlank(base) && notBlank(svc) && notBlank(domain)) {
      String socketName = svc + "-" + domain + ".sock";
      return new ServerConfig(defaultPort, base + "/" + socketName);
    }
    String portStr = env.apply("PORT");
    if (portStr == null || portStr.isEmpty()) portStr = env.apply("GRPC_PORT");
    if (portStr != null && !portStr.isEmpty()) {
      try {
        return new ServerConfig(Integer.parseInt(portStr), null);
      } catch (NumberFormatException nfe) {
        // fall through to default
      }
    }
    return new ServerConfig(defaultPort, null);
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isEmpty();
  }
}
