package dev.angzarr.client.server;

import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Readiness probes and health-status supervisor for runner servers.
 *
 * <p>Java port of Rust {@code client-rust/src/readiness.rs} and Python {@code
 * angzarr_client/readiness.py} (audit #68).
 *
 * <p>A runner exposes its readiness through {@code grpc.health.v1.Health}. While any probe is
 * failing, the per-kind service name reports {@link ServingStatus#NOT_SERVING}; once every probe is
 * green, it flips to {@link ServingStatus#SERVING}. Probes are evaluated on a fixed cadence
 * (default 30s, override via {@value #ENV_INTERVAL}) with a per-probe timeout (default 2s, override
 * via {@value #ENV_TIMEOUT}).
 *
 * <p>Aggregation is binary — "all up" is {@code SERVING}, anything else is {@code NOT_SERVING}. The
 * health server itself always responds, so liveness ("the process answers") and readiness ("it's
 * safe to send traffic") share one wire surface and are distinguished by the response status.
 *
 * <p>Audit #82: each cause (timeout / probe-raised / probe-returned-false) emits its own warning;
 * there is no aggregate "failed" log on top — the cause warning is sufficient. Exceptions are
 * caught so a misbehaving probe can never kill the supervisor.
 */
public final class Readiness {

  private Readiness() {}

  /** Default cadence for re-evaluating output-domain probes. */
  public static final Duration DEFAULT_PROBE_INTERVAL = Duration.ofSeconds(30);

  /** Default per-probe timeout. */
  public static final Duration DEFAULT_PROBE_TIMEOUT = Duration.ofSeconds(2);

  public static final String ENV_INTERVAL = "ANGZARR_READINESS_PROBE_INTERVAL";
  public static final String ENV_TIMEOUT = "ANGZARR_READINESS_PROBE_TIMEOUT";

  /**
   * Audit #74: optional async-bus endpoint (Kafka / RabbitMQ / SQS / SNS / NATS / etc.). When set,
   * a single {@link BusProbe} covers reachability of the async path for every async-only saga / PM
   * target. When unset, no bus probe is added — async-only targets are simply not part of
   * readiness.
   */
  public static final String ENV_BUS_ENDPOINT = "ANGZARR_BUS_ENDPOINT";

  private static final Logger LOG = LoggerFactory.getLogger(Readiness.class);

  // ---------------------------------------------------------------------------
  // Probe interface + config
  // ---------------------------------------------------------------------------

  /** Single readiness probe — evaluated once per supervisor tick. */
  public interface Probe {
    /** Stable identifier for log lines. */
    String name();

    /** {@code true} when the underlying dependency is currently healthy. */
    boolean check() throws Exception;
  }

  /** Output of the health-status setter used by {@link #runSupervisor}. */
  public interface HealthSink {
    void setStatus(String service, ServingStatus status);
  }

  /** Tuple of supervisor cadence + per-probe timeout resolved from env. */
  public record ProbeConfig(Duration interval, Duration timeout) {}

  /**
   * Read supervisor cadence + per-probe timeout from env, falling back to {@link
   * #DEFAULT_PROBE_INTERVAL} / {@link #DEFAULT_PROBE_TIMEOUT}.
   *
   * <p>Bad values (non-numeric) silently fall back to defaults — matches Rust's {@code
   * .parse::<u64>().ok()}.
   */
  public static ProbeConfig probeConfigFromEnv() {
    return probeConfigFromEnv(System::getenv, DEFAULT_PROBE_INTERVAL, DEFAULT_PROBE_TIMEOUT);
  }

  /** Same as {@link #probeConfigFromEnv()} with injected env + defaults — testable. */
  public static ProbeConfig probeConfigFromEnv(
      Function<String, String> env, Duration defaultInterval, Duration defaultTimeout) {
    return new ProbeConfig(
        readDuration(env, ENV_INTERVAL, defaultInterval),
        readDuration(env, ENV_TIMEOUT, defaultTimeout));
  }

  private static Duration readDuration(
      Function<String, String> env, String name, Duration fallback) {
    String raw = env.apply(name);
    if (raw == null || raw.isEmpty()) return fallback;
    try {
      long seconds = Long.parseLong(raw);
      return Duration.ofSeconds(seconds);
    } catch (NumberFormatException nfe) {
      return fallback;
    }
  }

  // ---------------------------------------------------------------------------
  // TransportSignal + TransportProbe
  // ---------------------------------------------------------------------------

  /** Side of a {@link TransportProbe} used by the runner to mark "bound and serving". */
  public static final class TransportSignal {
    private final AtomicBoolean bound = new AtomicBoolean(false);

    public void markBound() {
      bound.set(true);
    }

    public boolean isBound() {
      return bound.get();
    }
  }

  /** Pair of a probe and its mark-bound signal returned by {@link TransportProbe#newPair}. */
  public record TransportProbePair(TransportProbe probe, TransportSignal signal) {}

  /**
   * One-shot transport probe — flipped {@code true} once the listener has bound and the server is
   * accepting traffic. From that point its result never changes (mirrors Rust's {@code AtomicBool}
   * set-only).
   */
  public static final class TransportProbe implements Probe {
    private final TransportSignal signal;

    public TransportProbe(TransportSignal signal) {
      this.signal = Objects.requireNonNull(signal);
    }

    /** Create a fresh ({@link TransportProbe}, {@link TransportSignal}) pair. */
    public static TransportProbePair newPair() {
      TransportSignal sig = new TransportSignal();
      return new TransportProbePair(new TransportProbe(sig), sig);
    }

    @Override
    public String name() {
      return "transport";
    }

    @Override
    public boolean check() {
      return signal.isBound();
    }
  }

  // ---------------------------------------------------------------------------
  // Endpoint parsing — sealed type mirroring Rust's `Endpoint` enum
  // ---------------------------------------------------------------------------

  /**
   * Classified endpoint a probe targets. Either a TCP {@code host:port} or a UDS filesystem path.
   */
  public sealed interface Endpoint {
    /** TCP endpoint {@code host:port}. */
    record Tcp(String addr) implements Endpoint {}

    /** UDS endpoint filesystem path. */
    record Uds(String path) implements Endpoint {}
  }

  /**
   * Classify a resolved endpoint string into TCP or UDS. {@code unix:} prefix or leading {@code /}
   * selects UDS; otherwise TCP. Mirrors Python's {@code _parse_endpoint} and Rust's inline parsing.
   */
  public static Endpoint parseEndpoint(String raw) {
    if (raw.startsWith("unix:")) {
      return new Endpoint.Uds(raw.substring("unix:".length()));
    }
    if (raw.startsWith("/")) {
      return new Endpoint.Uds(raw);
    }
    return new Endpoint.Tcp(raw);
  }

  /**
   * Per-output-domain coordinator probe — attempts to open a connection to the downstream domain's
   * command-handler coordinator endpoint.
   *
   * <p>Audit #74: built only for <b>sync</b> output domains (declared via {@code @Saga(sync =
   * true)} or {@code @ProcessManager(syncTargets = {...})}). Async-only targets ride the bus; see
   * {@link BusProbe}.
   */
  public static final class OutputDomainProbe implements Probe {
    private final String domain;
    private final Endpoint endpoint;

    public OutputDomainProbe(String domain, Endpoint endpoint) {
      this.domain = Objects.requireNonNull(domain);
      this.endpoint = Objects.requireNonNull(endpoint);
    }

    @Override
    public String name() {
      return domain;
    }

    @Override
    public boolean check() {
      return tryConnect(endpoint);
    }
  }

  /**
   * Async-bus reachability probe (audit #74) — covers the path that async-only saga / PM targets
   * ride. The endpoint is operator-supplied via {@value #ENV_BUS_ENDPOINT} and points at whatever
   * broker the deployment uses. Connection-only — confirms the broker is reachable, not that
   * publishes will succeed end-to-end. Same contract as {@link OutputDomainProbe} for sync targets.
   */
  public static final class BusProbe implements Probe {
    private final Endpoint endpoint;

    public BusProbe(Endpoint endpoint) {
      this.endpoint = Objects.requireNonNull(endpoint);
    }

    /** Build from process env. Returns {@code null} when unset / blank. */
    public static BusProbe fromEnv() {
      return fromEnv(System::getenv);
    }

    /** Same as {@link #fromEnv()} with injected env — testable. */
    public static BusProbe fromEnv(Function<String, String> env) {
      String raw = env.apply(ENV_BUS_ENDPOINT);
      if (raw == null || raw.isEmpty()) return null;
      return new BusProbe(parseEndpoint(raw));
    }

    @Override
    public String name() {
      return "bus";
    }

    @Override
    public boolean check() {
      return tryConnect(endpoint);
    }

    /** Test-only accessor; not part of the cross-language contract. */
    Endpoint endpointForTest() {
      return endpoint;
    }
  }

  /** Generic connect-and-close probe used by {@link OutputDomainProbe} and {@link BusProbe}. */
  private static boolean tryConnect(Endpoint endpoint) {
    if (endpoint instanceof Endpoint.Tcp tcp) {
      return tryTcp(tcp.addr());
    }
    if (endpoint instanceof Endpoint.Uds uds) {
      return tryUds(uds.path());
    }
    // Should be unreachable thanks to the sealed hierarchy.
    return false;
  }

  private static boolean tryTcp(String addr) {
    int colon = addr.lastIndexOf(':');
    if (colon < 0) return false;
    String host = addr.substring(0, colon);
    if (host.startsWith("[") && host.endsWith("]")) {
      host = host.substring(1, host.length() - 1);
    }
    int port;
    try {
      port = Integer.parseInt(addr.substring(colon + 1));
    } catch (NumberFormatException nfe) {
      return false;
    }
    try (Socket sock = new Socket()) {
      sock.connect(
          new InetSocketAddress(host.isEmpty() ? "localhost" : host, port), /* timeout ms */ 1000);
      return true;
    } catch (IOException ioe) {
      return false;
    }
  }

  private static boolean tryUds(String path) {
    SocketAddress addr;
    try {
      addr = UnixDomainSocketAddress.of(Paths.get(path));
    } catch (RuntimeException re) {
      return false;
    }
    try (SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX)) {
      return ch.connect(addr);
    } catch (IOException ioe) {
      return false;
    }
  }

  // ---------------------------------------------------------------------------
  // Supervisor
  // ---------------------------------------------------------------------------

  /**
   * Handle returned by {@link #runSupervisor}. Closing it cancels the background supervisor thread
   * cleanly; mirrors the Rust {@code JoinHandle::abort} / Python {@code task.cancel()} shape so
   * shutdown paths look the same across languages.
   */
  public static final class SupervisorHandle implements AutoCloseable {
    private final ExecutorService executor;
    private final Future<?> task;
    private final AtomicBoolean running;

    SupervisorHandle(ExecutorService executor, Future<?> task, AtomicBoolean running) {
      this.executor = executor;
      this.task = task;
      this.running = running;
    }

    /** {@code true} while the supervisor loop is still scheduled. */
    public boolean isRunning() {
      return running.get() && !task.isDone();
    }

    /** Cancel the supervisor loop and wait briefly for it to exit. */
    @Override
    public void close() {
      running.set(false);
      task.cancel(true);
      executor.shutdownNow();
      try {
        executor.awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Spawn the readiness supervisor on a daemon thread. Polls every probe on each tick, aggregates
   * ({@code all_ok} → {@code SERVING}, else {@code NOT_SERVING}), publishes to every service name.
   * Loops until the returned {@link SupervisorHandle} is closed.
   *
   * <p>Audit #82: each cause (timeout / raise / probe-false) emits its own warning; the supervisor
   * is hard-isolated so a misbehaving probe can never kill it.
   */
  public static SupervisorHandle runSupervisor(
      List<Probe> probes,
      HealthSink sink,
      List<String> serviceNames,
      Duration interval,
      Duration timeout) {
    ExecutorService executor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "angzarr-readiness-supervisor");
              t.setDaemon(true);
              return t;
            });
    AtomicBoolean running = new AtomicBoolean(true);
    Future<?> task =
        executor.submit(
            () -> {
              ExecutorService probeExec =
                  Executors.newCachedThreadPool(
                      r -> {
                        Thread t = new Thread(r, "angzarr-probe");
                        t.setDaemon(true);
                        return t;
                      });
              try {
                while (running.get() && !Thread.currentThread().isInterrupted()) {
                  boolean allOk = supervisorTick(probes, timeout, probeExec);
                  ServingStatus status = allOk ? ServingStatus.SERVING : ServingStatus.NOT_SERVING;
                  for (String name : serviceNames) {
                    sink.setStatus(name, status);
                  }
                  try {
                    Thread.sleep(interval.toMillis());
                  } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                  }
                }
              } finally {
                probeExec.shutdownNow();
              }
            });
    return new SupervisorHandle(executor, task, running);
  }

  /**
   * One iteration of the supervisor loop. Polls every probe with the configured timeout, returns
   * {@code true} iff all probes report healthy. Audit #82: distinct WARN per cause (timeout / panic
   * / probe-returned-false).
   */
  static boolean supervisorTick(List<Probe> probes, Duration timeout, ExecutorService probeExec) {
    boolean allOk = true;
    for (Probe probe : probes) {
      boolean ok = runOneProbe(probe, timeout, probeExec);
      if (!ok) allOk = false;
    }
    return allOk;
  }

  private static boolean runOneProbe(Probe probe, Duration timeout, ExecutorService probeExec) {
    Callable<Boolean> task = probe::check;
    Future<Boolean> fut;
    try {
      fut = probeExec.submit(task);
    } catch (RejectedExecutionException ree) {
      // Executor shutting down — treat as failure but don't log noisily.
      return false;
    }
    try {
      boolean ok = fut.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!ok) {
        // Audit #82: probe-returned-false path produces "readiness probe failed"
        // — distinct from timeout / panic.
        LOG.warn("readiness probe failed probe={}", probe.name());
      }
      return ok;
    } catch (TimeoutException te) {
      fut.cancel(true);
      // Audit #82: timeout WARN. No "failed" log on top — the cause warning suffices.
      LOG.warn("readiness probe timed out probe={}", probe.name());
      return false;
    } catch (ExecutionException ee) {
      // Audit #82 / #90: exception WARN with structured `probe` + `error` fields.
      Throwable cause = ee.getCause();
      String msg = cause == null ? "<no cause>" : String.valueOf(cause.getMessage());
      LOG.warn("readiness probe panicked probe={} error={}", probe.name(), msg);
      return false;
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /**
   * Audit #83: flip every registered health name to {@link ServingStatus#NOT_SERVING} so the K8s
   * load balancer drains the pod. Extracted so the shutdown publish can be unit-tested in isolation
   * from the runner's transport plumbing.
   */
  public static void publishShutdownStatus(HealthSink sink, List<String> serviceNames) {
    for (String name : serviceNames) {
      sink.setStatus(name, ServingStatus.NOT_SERVING);
    }
  }
}
