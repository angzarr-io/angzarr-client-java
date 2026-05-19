package dev.angzarr.client.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Cross-language parity for {@code Server.resolveBindAddress} and {@code ServerConfig.fromEnv} —
 * port of Rust {@code src/server.rs::tests} (audits #77, #83).
 */
class ServerConfigTest {

  @Test
  void resolveBindAddressDefaultIsDualStack() {
    String addr = Server.resolveBindAddress(50052, name -> null);
    assertThat(addr).isEqualTo("[::]:50052");
  }

  @Test
  void resolveBindAddressEnvOverrideIpv4() {
    String addr =
        Server.resolveBindAddress(
            50052, name -> Server.ENV_BIND_ADDRESS.equals(name) ? "127.0.0.1:9090" : null);
    assertThat(addr).isEqualTo("127.0.0.1:9090");
  }

  @Test
  void resolveBindAddressEnvOverrideIpv6Loopback() {
    String addr =
        Server.resolveBindAddress(
            50052, name -> Server.ENV_BIND_ADDRESS.equals(name) ? "[::1]:8080" : null);
    assertThat(addr).isEqualTo("[::1]:8080");
  }

  @Test
  void resolveBindAddressEnvOverrideIgnoresDefaultPort() {
    String addr =
        Server.resolveBindAddress(
            50052, name -> Server.ENV_BIND_ADDRESS.equals(name) ? "0.0.0.0:1234" : null);
    assertThat(addr).isEqualTo("0.0.0.0:1234");
  }

  @Test
  void serverConfigFromEnvDefaultsToTcp() {
    var cfg = ServerConfig.fromEnv(50052, name -> null);
    assertThat(cfg.port()).isEqualTo(50052);
    assertThat(cfg.udsPath()).isNull();
  }

  @Test
  void serverConfigFromEnvReadsPort() {
    var cfg = ServerConfig.fromEnv(50052, name -> "PORT".equals(name) ? "1234" : null);
    assertThat(cfg.port()).isEqualTo(1234);
    assertThat(cfg.udsPath()).isNull();
  }

  @Test
  void serverConfigFromEnvFallsBackOnGarbagePort() {
    var cfg = ServerConfig.fromEnv(50052, name -> "PORT".equals(name) ? "garbage" : null);
    assertThat(cfg.port()).isEqualTo(50052);
  }

  @Test
  void serverConfigFromEnvSelectsUdsWhenAllThreeSet() {
    var cfg =
        ServerConfig.fromEnv(
            50052,
            name ->
                switch (name) {
                  case "UDS_BASE_PATH" -> "/tmp/angzarr";
                  case "SERVICE_NAME" -> "business";
                  case "DOMAIN" -> "order";
                  default -> null;
                });
    assertThat(cfg.udsPath()).isEqualTo("/tmp/angzarr/business-order.sock");
  }

  @Test
  void serverConfigFromEnvFallsBackToTcpWhenUdsTriadIncomplete() {
    // Only two of the three UDS env vars — fall through to TCP.
    var cfg =
        ServerConfig.fromEnv(
            50052,
            name ->
                switch (name) {
                  case "UDS_BASE_PATH" -> "/tmp/angzarr";
                  case "SERVICE_NAME" -> "business";
                  default -> null;
                });
    assertThat(cfg.udsPath()).isNull();
  }
}
