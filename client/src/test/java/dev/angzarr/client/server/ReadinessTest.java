package dev.angzarr.client.server;

import static org.assertj.core.api.Assertions.assertThat;

import dev.angzarr.client.server.Readiness.Endpoint;
import dev.angzarr.client.server.Readiness.HealthSink;
import dev.angzarr.client.server.Readiness.Probe;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Cross-language parity for the readiness supervisor + probes — port of Python {@code
 * tests/test_readiness.py} and Rust {@code src/readiness.rs::tests}.
 *
 * <p>Audits: #68 (NOT_SERVING initial state), #74 (per-target probe selection), #77 (bind address
 * override), #82 (cause-distinct warnings, probe exception isolation), #83 (NOT_SERVING on
 * shutdown), #89/#90 (cross-language log shape).
 */
class ReadinessTest {

  // ---------------------------------------------------------------------------
  // Readiness.probeConfigFromEnv
  // ---------------------------------------------------------------------------

  @Test
  void probeConfigDefaultsWhenEnvUnset() {
    var cfg =
        Readiness.probeConfigFromEnv(
            var -> null, Readiness.DEFAULT_PROBE_INTERVAL, Readiness.DEFAULT_PROBE_TIMEOUT);
    assertThat(cfg.interval()).isEqualTo(Readiness.DEFAULT_PROBE_INTERVAL);
    assertThat(cfg.timeout()).isEqualTo(Readiness.DEFAULT_PROBE_TIMEOUT);
  }

  @Test
  void probeConfigReadsFromEnv() {
    var env =
        (java.util.function.Function<String, String>)
            name ->
                switch (name) {
                  case Readiness.ENV_INTERVAL -> "5";
                  case Readiness.ENV_TIMEOUT -> "1";
                  default -> null;
                };
    var cfg =
        Readiness.probeConfigFromEnv(
            env, Readiness.DEFAULT_PROBE_INTERVAL, Readiness.DEFAULT_PROBE_TIMEOUT);
    assertThat(cfg.interval()).isEqualTo(Duration.ofSeconds(5));
    assertThat(cfg.timeout()).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void probeConfigFallsBackOnGarbage() {
    var env =
        (java.util.function.Function<String, String>)
            name ->
                switch (name) {
                  case Readiness.ENV_INTERVAL -> "thirty";
                  case Readiness.ENV_TIMEOUT -> "two";
                  default -> null;
                };
    var cfg =
        Readiness.probeConfigFromEnv(
            env, Readiness.DEFAULT_PROBE_INTERVAL, Readiness.DEFAULT_PROBE_TIMEOUT);
    assertThat(cfg.interval()).isEqualTo(Readiness.DEFAULT_PROBE_INTERVAL);
    assertThat(cfg.timeout()).isEqualTo(Readiness.DEFAULT_PROBE_TIMEOUT);
  }

  // ---------------------------------------------------------------------------
  // TransportProbe / TransportSignal
  // ---------------------------------------------------------------------------

  @Test
  void transportProbeStartsUnbound() throws Exception {
    var paired = Readiness.TransportProbe.newPair();
    assertThat(paired.probe().name()).isEqualTo("transport");
    assertThat(paired.probe().check()).isFalse();
    assertThat(paired.signal().isBound()).isFalse();
  }

  @Test
  void transportProbeFlipsAfterMarkBound() throws Exception {
    var paired = Readiness.TransportProbe.newPair();
    paired.signal().markBound();
    assertThat(paired.probe().check()).isTrue();
  }

  @Test
  void transportSignalIsOneWay() throws Exception {
    var paired = Readiness.TransportProbe.newPair();
    paired.signal().markBound();
    paired.signal().markBound(); // idempotent — stays bound
    assertThat(paired.probe().check()).isTrue();
    assertThat(paired.signal().isBound()).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Endpoint parsing
  // ---------------------------------------------------------------------------

  @Test
  void parseEndpointTcpHostPort() {
    var ep = Readiness.parseEndpoint("inventory:1310");
    assertThat(ep).isInstanceOf(Endpoint.Tcp.class);
    assertThat(((Endpoint.Tcp) ep).addr()).isEqualTo("inventory:1310");
  }

  @Test
  void parseEndpointUnixPrefixIsUds() {
    var ep = Readiness.parseEndpoint("unix:/tmp/sock");
    assertThat(ep).isInstanceOf(Endpoint.Uds.class);
    assertThat(((Endpoint.Uds) ep).path()).isEqualTo("/tmp/sock");
  }

  @Test
  void parseEndpointUnixRelativeIsUds() {
    var ep = Readiness.parseEndpoint("unix:relative.sock");
    assertThat(ep).isInstanceOf(Endpoint.Uds.class);
    assertThat(((Endpoint.Uds) ep).path()).isEqualTo("relative.sock");
  }

  @Test
  void parseEndpointLeadingSlashIsUds() {
    var ep = Readiness.parseEndpoint("/var/run/angzarr.sock");
    assertThat(ep).isInstanceOf(Endpoint.Uds.class);
    assertThat(((Endpoint.Uds) ep).path()).isEqualTo("/var/run/angzarr.sock");
  }

  // ---------------------------------------------------------------------------
  // OutputDomainProbe (TCP path)
  // ---------------------------------------------------------------------------

  @Test
  void tcpProbeSucceedsWhenListenerBound() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      var probe =
          new Readiness.OutputDomainProbe(
              "downstream", new Endpoint.Tcp("127.0.0.1:" + server.getLocalPort()));
      assertThat(probe.name()).isEqualTo("downstream");
      assertThat(probe.check()).isTrue();
    }
  }

  @Test
  void tcpProbeFailsWhenNothingListening() throws Exception {
    int unusedPort;
    try (ServerSocket scratch = new ServerSocket(0)) {
      unusedPort = scratch.getLocalPort();
    }
    var probe =
        new Readiness.OutputDomainProbe("downstream", new Endpoint.Tcp("127.0.0.1:" + unusedPort));
    assertThat(probe.check()).isFalse();
  }

  @Test
  void tcpProbeFailsOnGarbageAddress() throws Exception {
    var probe = new Readiness.OutputDomainProbe("garbage", new Endpoint.Tcp("not-a-host:notaport"));
    assertThat(probe.check()).isFalse();
  }

  // ---------------------------------------------------------------------------
  // BusProbe.fromEnv
  // ---------------------------------------------------------------------------

  @Test
  void busProbeFromEnvReturnsNullWhenUnset() {
    assertThat(Readiness.BusProbe.fromEnv(var -> null)).isNull();
  }

  @Test
  void busProbeFromEnvReturnsNullWhenBlank() {
    assertThat(Readiness.BusProbe.fromEnv(var -> "")).isNull();
  }

  @Test
  void busProbeFromEnvParsesTcp() {
    var probe = Readiness.BusProbe.fromEnv(name -> "kafka.bus.svc:9092");
    assertThat(probe).isNotNull();
    assertThat(probe.name()).isEqualTo("bus");
    assertThat(probe.endpointForTest()).isInstanceOf(Endpoint.Tcp.class);
    assertThat(((Endpoint.Tcp) probe.endpointForTest()).addr()).isEqualTo("kafka.bus.svc:9092");
  }

  @Test
  void busProbeFromEnvParsesUds() {
    var probe = Readiness.BusProbe.fromEnv(name -> "unix:/var/run/bus.sock");
    assertThat(probe).isNotNull();
    assertThat(probe.endpointForTest()).isInstanceOf(Endpoint.Uds.class);
    assertThat(((Endpoint.Uds) probe.endpointForTest()).path()).isEqualTo("/var/run/bus.sock");
  }

  // ---------------------------------------------------------------------------
  // runSupervisor
  // ---------------------------------------------------------------------------

  private static final class FakeProbe implements Probe {
    final String n;
    final List<Boolean> results;
    final AtomicInteger calls = new AtomicInteger();

    FakeProbe(String n, List<Boolean> results) {
      this.n = n;
      this.results = new ArrayList<>(results);
    }

    @Override
    public String name() {
      return n;
    }

    @Override
    public boolean check() {
      calls.incrementAndGet();
      if (results.size() > 1) return results.remove(0);
      if (results.size() == 1) return results.get(0); // sticky last
      return false;
    }
  }

  private static final class SlowProbe implements Probe {
    final String n;
    final Duration delay;

    SlowProbe(String n, Duration delay) {
      this.n = n;
      this.delay = delay;
    }

    @Override
    public String name() {
      return n;
    }

    @Override
    public boolean check() {
      try {
        Thread.sleep(delay.toMillis());
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
      return true;
    }
  }

  private static final class RecordingHealth implements HealthSink {
    final ConcurrentLinkedQueue<String> calls = new ConcurrentLinkedQueue<>();

    @Override
    public void setStatus(String service, ServingStatus status) {
      calls.add(service + "=" + status.name());
    }
  }

  @Test
  void supervisorPublishesServingWhenAllProbesPass() throws Exception {
    var probe = new FakeProbe("p", List.of(true, true, true, true, true));
    var sink = new RecordingHealth();
    try (var supervisor =
        Readiness.runSupervisor(
            List.of(probe),
            sink,
            List.of("", "MyService"),
            Duration.ofMillis(10),
            Duration.ofSeconds(1))) {
      Thread.sleep(60);
    }
    assertThat(probe.calls.get()).isGreaterThanOrEqualTo(1);
    assertThat(sink.calls).contains("=SERVING", "MyService=SERVING");
    assertThat(sink.calls).noneMatch(s -> s.contains("NOT_SERVING"));
  }

  @Test
  void supervisorPublishesNotServingWhenAnyProbeFails() throws Exception {
    var ok = new FakeProbe("a", List.of(true, true, true, true, true));
    var bad = new FakeProbe("b", List.of(false, false, false, false, false));
    var sink = new RecordingHealth();
    try (var supervisor =
        Readiness.runSupervisor(
            List.of(ok, bad), sink, List.of(""), Duration.ofMillis(10), Duration.ofSeconds(1))) {
      Thread.sleep(60);
    }
    assertThat(ok.calls.get()).isGreaterThanOrEqualTo(1);
    assertThat(bad.calls.get()).isGreaterThanOrEqualTo(1);
    assertThat(sink.calls).allMatch(s -> s.contains("NOT_SERVING"));
  }

  @Test
  void supervisorTreatsTimeoutAsFailure() throws Exception {
    var slow = new SlowProbe("slow", Duration.ofSeconds(1));
    var sink = new RecordingHealth();
    try (var supervisor =
        Readiness.runSupervisor(
            List.of(slow), sink, List.of(""), Duration.ofMillis(20), Duration.ofMillis(10))) {
      Thread.sleep(100);
    }
    assertThat(sink.calls).isNotEmpty();
    assertThat(sink.calls).allMatch(s -> s.contains("NOT_SERVING"));
  }

  @Test
  void supervisorLoopsUntilClosed() throws Exception {
    var probe =
        new FakeProbe(
            "p", java.util.stream.IntStream.range(0, 100).mapToObj(i -> Boolean.TRUE).toList());
    var sink = new RecordingHealth();
    try (var supervisor =
        Readiness.runSupervisor(
            List.of(probe), sink, List.of(""), Duration.ofMillis(5), Duration.ofSeconds(1))) {
      Thread.sleep(50);
      int first = probe.calls.get();
      Thread.sleep(50);
      int second = probe.calls.get();
      assertThat(second).isGreaterThan(first);
    }
  }

  @Test
  void supervisorSurvivesRaisingProbe() throws Exception {
    final var counter = new AtomicInteger();
    Probe raiser =
        new Probe() {
          @Override
          public String name() {
            return "raiser";
          }

          @Override
          public boolean check() {
            counter.incrementAndGet();
            throw new RuntimeException("boom");
          }
        };
    var sink = new RecordingHealth();
    try (var supervisor =
        Readiness.runSupervisor(
            List.of(raiser), sink, List.of(""), Duration.ofMillis(10), Duration.ofSeconds(1))) {
      Thread.sleep(60);
      assertThat(supervisor.isRunning()).isTrue();
    }
    assertThat(counter.get()).isGreaterThanOrEqualTo(2);
    assertThat(sink.calls).allMatch(s -> s.contains("NOT_SERVING"));
  }

  // ---------------------------------------------------------------------------
  // publishShutdownStatus (audit #83)
  // ---------------------------------------------------------------------------

  @Test
  void publishShutdownStatusFlipsEveryNameToNotServing() {
    var sink = new RecordingHealth();
    var names = List.of("", "svc.A", "svc.B");
    for (var n : names) {
      sink.setStatus(n, ServingStatus.SERVING);
    }
    sink.calls.clear();
    Readiness.publishShutdownStatus(sink, names);
    assertThat(sink.calls)
        .containsExactly("=NOT_SERVING", "svc.A=NOT_SERVING", "svc.B=NOT_SERVING");
  }

  @Test
  void publishShutdownStatusNoNamesIsNoop() {
    var sink = new RecordingHealth();
    Readiness.publishShutdownStatus(sink, List.of());
    assertThat(sink.calls).isEmpty();
  }
}
