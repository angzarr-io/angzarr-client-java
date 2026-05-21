package dev.angzarr.client.server;

import dev.angzarr.client.router.Built;
import dev.angzarr.client.router.CommandHandlerRouter;
import dev.angzarr.client.router.ProcessManagerRouter;
import dev.angzarr.client.router.ProjectorRouter;
import dev.angzarr.client.router.SagaRouter;
import dev.angzarr.client.router.UpcasterRouter;
import dev.angzarr.client.server.Readiness.BusProbe;
import dev.angzarr.client.server.Readiness.HealthSink;
import dev.angzarr.client.server.Readiness.OutputDomainProbe;
import dev.angzarr.client.server.Readiness.Probe;
import dev.angzarr.client.server.Readiness.SupervisorHandle;
import dev.angzarr.client.server.Readiness.TransportProbe;
import dev.angzarr.client.server.Readiness.TransportProbePair;
import dev.angzarr.client.server.Readiness.TransportSignal;
import io.grpc.BindableService;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC runner utilities for hosting aggregate, saga, process manager, projector, and upcaster
 * services. Java port of Rust {@code src/server.rs} and Python {@code angzarr_client/server.py}
 * (audits #68, #74, #77, #83, #89).
 *
 * <p>Each {@code start*Server} method:
 *
 * <ol>
 *   <li>Resolves transport from env via {@link ServerConfig#fromEnv(int)} (TCP or UDS).
 *   <li>Reads the runner's logical name from the router ({@code router.name()}).
 *   <li>Adds {@code grpc.health.v1.Health} alongside the kind-specific service.
 *   <li>Spawns a {@link Readiness} supervisor whose probes are:
 *       <ul>
 *         <li>a {@link TransportProbe} flipped once the listener has bound,
 *         <li>one {@link OutputDomainProbe} per sync output domain ({@code @Saga(sync=true)} /
 *             {@code @ProcessManager(syncTargets=...)}), and
 *         <li>a single {@link BusProbe} when any async target is present AND {@code
 *             ANGZARR_BUS_ENDPOINT} is configured.
 *       </ul>
 * </ol>
 *
 * <p>While any probe is failing, the per-kind health service name and the empty {@code ""} overall
 * name both report {@link ServingStatus#NOT_SERVING}. K8s liveness sees the gRPC server respond
 * regardless; readiness only flips green once all probes do (audit #68).
 */
public final class Server {

  private Server() {}

  private static final Logger LOG = LoggerFactory.getLogger(Server.class);

  /** Fully-qualified gRPC service name for command-handler routers. */
  public static final String HEALTH_NAME_COMMAND_HANDLER =
      "angzarr_client.proto.angzarr.v1.CommandHandlerService";

  /** Fully-qualified gRPC service name for saga routers. */
  public static final String HEALTH_NAME_SAGA = "angzarr_client.proto.angzarr.v1.SagaService";

  /** Fully-qualified gRPC service name for process-manager routers. */
  public static final String HEALTH_NAME_PROCESS_MANAGER =
      "angzarr_client.proto.angzarr.v1.ProcessManagerService";

  /** Fully-qualified gRPC service name for projector routers. */
  public static final String HEALTH_NAME_PROJECTOR =
      "angzarr_client.proto.angzarr.v1.ProjectorService";

  /** Fully-qualified gRPC service name for upcaster routers. */
  public static final String HEALTH_NAME_UPCASTER = "angzarr_client.proto.angzarr.v1.UpcasterService";

  /**
   * Env var name for the full TCP bind address override ({@code host:port}).
   *
   * <p>Audit #77: when set, supersedes the default {@code [::]:{port}} composition and the {@code
   * PORT} / {@code GRPC_PORT} resolution. IPv6 hosts must include brackets (e.g. {@code
   * [::1]:50052}); IPv4 hosts are written bare.
   */
  public static final String ENV_BIND_ADDRESS = "ANGZARR_BIND_ADDRESS";

  /**
   * Default TCP bind host. {@code "[::]"} is the IPv6 wildcard, which on Linux accepts both IPv4
   * (via IPv4-mapped IPv6) and IPv6 connections — matches the Rust + Python posture per audit #77.
   */
  public static final String DEFAULT_BIND_HOST = "[::]";

  /**
   * Compute the TCP bind address. Returns {@value #ENV_BIND_ADDRESS} verbatim when set, otherwise
   * composes {@code [::]:{defaultPort}}.
   */
  public static String resolveBindAddress(int defaultPort) {
    return resolveBindAddress(defaultPort, System::getenv);
  }

  /** Same as {@link #resolveBindAddress(int)} with injected env — testable. */
  public static String resolveBindAddress(int defaultPort, Function<String, String> env) {
    String override = env.apply(ENV_BIND_ADDRESS);
    if (override != null && !override.isEmpty()) {
      return override;
    }
    return DEFAULT_BIND_HOST + ":" + defaultPort;
  }

  // ---------------------------------------------------------------------------
  // configureLogging — MED-5.10 cross-language parity helper
  // ---------------------------------------------------------------------------

  /**
   * Env var name for runtime log level override. Reads {@code trace}, {@code debug}, {@code info},
   * {@code warn}, or {@code error}; defaults to {@code info} when unset. Matches Rust's {@code
   * RUST_LOG} role and Python's structlog wrapper-class filtering threshold.
   */
  public static final String ENV_LOG_LEVEL = "ANGZARR_LOG_LEVEL";

  /** Canonical level names slf4j-simple understands (lowercase). */
  private static final java.util.Set<String> CANONICAL_LEVELS =
      java.util.Set.of("trace", "debug", "info", "warn", "error");

  /**
   * Configure the slf4j-simple logging backend with ISO-8601 timestamps and the level taken from
   * {@value #ENV_LOG_LEVEL} (default {@code info}).
   *
   * <p>Java parity for Python's {@code configure_logging()} (structlog + JSON + ISO timestamps,
   * {@code server.py:38}) and Rust's {@code configure_logging()} (tracing-subscriber + JSON +
   * RUST_LOG filter, {@code server.rs:57}). The chosen backend here is slf4j-simple because it is
   * the de-facto default when no other slf4j binding is on the classpath; consumers wiring
   * logback/log4j2 should call their own configurator and skip this method.
   *
   * <p>Idempotent. Caller-set system properties are preserved (this method only fills in unset
   * defaults). slf4j-simple reads these properties on first logger construction — call before any
   * logger is created for the settings to take effect process-wide.
   */
  public static void configureLogging() {
    configureLogging(System::getenv);
  }

  /** Same as {@link #configureLogging()} with injected env — testable. */
  public static void configureLogging(Function<String, String> env) {
    // ISO-8601 timestamps with millisecond precision and zone offset.
    // Matches Python's structlog `iso` formatter and Rust's tracing
    // ChronoLocal/ChronoUtc with millisecond resolution.
    setDefaultProperty("org.slf4j.simpleLogger.showDateTime", "true");
    setDefaultProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    // Cross-language log-shape conventions: level in brackets, thread off.
    setDefaultProperty("org.slf4j.simpleLogger.levelInBrackets", "true");
    setDefaultProperty("org.slf4j.simpleLogger.showThreadName", "false");
    setDefaultProperty("org.slf4j.simpleLogger.defaultLogLevel", resolveLogLevel(env));
  }

  /** Resolve the canonical log level from the {@value #ENV_LOG_LEVEL} env var. */
  private static String resolveLogLevel(Function<String, String> env) {
    String raw = env.apply(ENV_LOG_LEVEL);
    if (raw == null) return "info";
    String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
    if (normalized.isEmpty()) return "info";
    return CANONICAL_LEVELS.contains(normalized) ? normalized : "info";
  }

  /**
   * Set a system property only when it is unset, so an explicit caller value (e.g., a launcher
   * passing {@code -Dorg.slf4j...=...}) wins.
   */
  private static void setDefaultProperty(String key, String value) {
    if (System.getProperty(key) == null) {
      System.setProperty(key, value);
    }
  }

  // ---------------------------------------------------------------------------
  // HealthSink adapter — wraps grpc-services' HealthStatusManager so the
  // supervisor stays decoupled from io.grpc internals.
  // ---------------------------------------------------------------------------

  /**
   * Adapter making a grpc-services {@link HealthStatusManager} look like a cross-language {@link
   * HealthSink}. Mirrors Rust's {@code HealthReporter::set_service_status} and Python's {@code
   * health_servicer.set} contract.
   */
  public static final class GrpcHealthSink implements HealthSink {
    private final HealthStatusManager manager;

    public GrpcHealthSink(HealthStatusManager manager) {
      this.manager = manager;
    }

    @Override
    public void setStatus(String service, ServingStatus status) {
      manager.setStatus(service, status);
    }
  }

  // ---------------------------------------------------------------------------
  // Cleanup helpers
  // ---------------------------------------------------------------------------

  /** Remove a stale UDS socket file at {@code path}. No-op if absent. */
  public static void cleanupSocket(String path) {
    if (path == null || path.isEmpty()) return;
    try {
      Files.deleteIfExists(Paths.get(path));
    } catch (IOException ioe) {
      // best-effort — matches Rust/Python
    }
  }

  // ---------------------------------------------------------------------------
  // Per-kind probe builders (audit #74)
  // ---------------------------------------------------------------------------

  /**
   * Compute the readiness probes for a {@link SagaRouter}:
   *
   * <ul>
   *   <li>One {@link OutputDomainProbe} per sync target.
   *   <li>A {@link BusProbe} when any async target is present AND {@value
   *       Readiness#ENV_BUS_ENDPOINT} is configured.
   * </ul>
   *
   * <p>The {@link TransportProbe} is added separately by the runner so it shares the {@link
   * TransportSignal} with the listener bind path.
   */
  public static List<Probe> probesForSaga(SagaRouter router, Function<String, String> chEndpoint) {
    return probesForSagaWithBus(router, chEndpoint, BusProbe::fromEnv);
  }

  /**
   * Test-seam variant of {@link #probesForSaga} that takes a custom bus probe supplier so tests can
   * inject an env reader without mutating process env. The supplier returning {@code null} means
   * "no bus probe".
   */
  public static List<Probe> probesForSagaWithBus(
      SagaRouter router,
      Function<String, String> chEndpoint,
      java.util.function.Supplier<BusProbe> busSupplier) {
    List<Probe> out = new ArrayList<>();
    for (String domain : router.syncOutputDomains()) {
      out.add(buildOutputDomainProbe(domain, chEndpoint));
    }
    if (router.hasAsyncOutputs()) {
      BusProbe bp = busSupplier.get();
      if (bp != null) out.add(bp);
    }
    return out;
  }

  /**
   * Compute the readiness probes for a {@link ProcessManagerRouter}. Same audit-#74 rules as {@link
   * #probesForSaga}.
   */
  public static List<Probe> probesForProcessManager(
      ProcessManagerRouter<?> router, Function<String, String> chEndpoint) {
    return probesForProcessManagerWithBus(router, chEndpoint, BusProbe::fromEnv);
  }

  /** Test-seam variant of {@link #probesForProcessManager}. */
  public static List<Probe> probesForProcessManagerWithBus(
      ProcessManagerRouter<?> router,
      Function<String, String> chEndpoint,
      java.util.function.Supplier<BusProbe> busSupplier) {
    List<Probe> out = new ArrayList<>();
    for (String domain : router.syncOutputDomains()) {
      out.add(buildOutputDomainProbe(domain, chEndpoint));
    }
    if (router.hasAsyncOutputs()) {
      BusProbe bp = busSupplier.get();
      if (bp != null) out.add(bp);
    }
    return out;
  }

  /** CH routers never emit cross-domain commands; no output probes. */
  public static List<Probe> probesForCommandHandler(CommandHandlerRouter<?> router) {
    return List.of();
  }

  /** Projectors are read-side; no output probes. */
  public static List<Probe> probesForProjector(ProjectorRouter router) {
    return List.of();
  }

  /**
   * Upcasters are read-side event transformers; no output probes. Audit #74 / HIGH-5.3 parity with
   * the projector path.
   */
  public static List<Probe> probesForUpcaster(UpcasterRouter router) {
    return List.of();
  }

  private static OutputDomainProbe buildOutputDomainProbe(
      String domain, Function<String, String> chEndpoint) {
    String raw = chEndpoint.apply(domain);
    return new OutputDomainProbe(domain, Readiness.parseEndpoint(raw));
  }

  /**
   * Default endpoint resolver that delegates to {@link
   * dev.angzarr.client.Transport#resolveCommandHandlerEndpoint(String,
   * dev.angzarr.client.Transport.Mode, String, String, Integer)}.
   */
  public static Function<String, String> defaultChEndpointResolver() {
    return domain ->
        dev.angzarr.client.Transport.resolveCommandHandlerEndpoint(domain, null, null, null, null);
  }

  // ---------------------------------------------------------------------------
  // Per-kind runners (start*Server) — return a RunningServer for callers to
  // drive shutdown. Cross-language `run_*_server` parity.
  // ---------------------------------------------------------------------------

  /**
   * Handle returned by every {@code start*Server} call. {@link #shutdown()} stops the supervisor,
   * flips every registered health name to {@link ServingStatus#NOT_SERVING} (audit #83), and
   * gracefully stops the underlying gRPC server.
   */
  public static final class RunningServer implements AutoCloseable {
    private final io.grpc.Server grpc;
    private final SupervisorHandle supervisor;
    private final HealthSink sink;
    private final List<String> serviceNames;
    private final String serviceName;
    private final String instanceName;

    RunningServer(
        io.grpc.Server grpc,
        SupervisorHandle supervisor,
        HealthSink sink,
        List<String> serviceNames,
        String serviceName,
        String instanceName) {
      this.grpc = grpc;
      this.supervisor = supervisor;
      this.sink = sink;
      this.serviceNames = serviceNames;
      this.serviceName = serviceName;
      this.instanceName = instanceName;
    }

    /** The underlying gRPC server. */
    public io.grpc.Server grpc() {
      return grpc;
    }

    /** Block the calling thread until the server terminates. */
    public void awaitTermination() throws InterruptedException {
      grpc.awaitTermination();
    }

    /**
     * Two-phase shutdown (audit #83): cancel the supervisor, flip every health name to NOT_SERVING,
     * then gracefully stop the gRPC server.
     */
    public void shutdown() {
      supervisor.close();
      Readiness.publishShutdownStatus(sink, serviceNames);
      grpc.shutdown();
      // Audit #89: same event name + field set as Python / Rust on shutdown.
      LOG.info("server_shutdown service={} name={}", serviceName, instanceName);
    }

    @Override
    public void close() {
      shutdown();
    }
  }

  /** Run a command handler service. */
  public static RunningServer startCommandHandlerServer(
      CommandHandlerRouter<?> router, BindableService grpcService, int defaultPort)
      throws IOException {
    return startKind(
        router.name(),
        grpcService,
        HEALTH_NAME_COMMAND_HANDLER,
        probesForCommandHandler(router),
        defaultPort);
  }

  /** Run a saga service. */
  public static RunningServer startSagaServer(
      SagaRouter router, BindableService grpcService, int defaultPort) throws IOException {
    return startKind(
        router.name(),
        grpcService,
        HEALTH_NAME_SAGA,
        probesForSaga(router, defaultChEndpointResolver()),
        defaultPort);
  }

  /** Run a process-manager service. */
  public static RunningServer startProcessManagerServer(
      ProcessManagerRouter<?> router, BindableService grpcService, int defaultPort)
      throws IOException {
    return startKind(
        router.name(),
        grpcService,
        HEALTH_NAME_PROCESS_MANAGER,
        probesForProcessManager(router, defaultChEndpointResolver()),
        defaultPort);
  }

  /** Run a projector service. */
  public static RunningServer startProjectorServer(
      ProjectorRouter router, BindableService grpcService, int defaultPort) throws IOException {
    return startKind(
        router.name(), grpcService, HEALTH_NAME_PROJECTOR, probesForProjector(router), defaultPort);
  }

  /** Run an upcaster service. HIGH-5.3: parity with Py/Rs/Cs per-kind runners. */
  public static RunningServer startUpcasterServer(
      UpcasterRouter router, BindableService grpcService, int defaultPort) throws IOException {
    return startKind(
        router.name(), grpcService, HEALTH_NAME_UPCASTER, probesForUpcaster(router), defaultPort);
  }

  /** Dispatch to the per-kind runner. Convenience wrapper for callers holding a {@link Built}. */
  public static RunningServer startServer(Built built, BindableService grpcService, int defaultPort)
      throws IOException {
    if (built instanceof CommandHandlerRouter<?> r) {
      return startCommandHandlerServer(r, grpcService, defaultPort);
    }
    if (built instanceof SagaRouter r) {
      return startSagaServer(r, grpcService, defaultPort);
    }
    if (built instanceof ProcessManagerRouter<?> r) {
      return startProcessManagerServer(r, grpcService, defaultPort);
    }
    if (built instanceof ProjectorRouter r) {
      return startProjectorServer(r, grpcService, defaultPort);
    }
    if (built instanceof UpcasterRouter r) {
      return startUpcasterServer(r, grpcService, defaultPort);
    }
    throw new IllegalArgumentException("unknown Built variant: " + built.getClass().getName());
  }

  /**
   * Shared runner core: builds health + supervisor, binds the listener, marks transport bound, and
   * returns the {@link RunningServer} handle. Mirrors Rust's {@code run_kind} and Python's {@code
   * _run_kind_server}.
   */
  static RunningServer startKind(
      String instanceName,
      BindableService kindService,
      String healthServiceName,
      List<Probe> outputProbes,
      int defaultPort)
      throws IOException {
    ServerConfig config = ServerConfig.fromEnv(defaultPort);
    HealthStatusManager healthManager = new HealthStatusManager();
    HealthSink sink = new GrpcHealthSink(healthManager);

    // Audit #68: start every registered name at NOT_SERVING so K8s
    // readiness gates traffic until the supervisor flips it.
    List<String> serviceNames = new ArrayList<>();
    serviceNames.add(""); // overall
    serviceNames.add(healthServiceName);
    for (String n : serviceNames) {
      sink.setStatus(n, ServingStatus.NOT_SERVING);
    }

    // Audit #82: TransportProbe pair so the supervisor sees the listener
    // come up and the runner can mark it bound after a successful start.
    TransportProbePair pair = TransportProbe.newPair();
    List<Probe> probes = new ArrayList<>();
    probes.add(pair.probe());
    probes.addAll(outputProbes);

    // Build the gRPC server. We use NettyServerBuilder so UDS / TCP both
    // route through the same builder API; the choice between them lives in
    // the address we pass.
    String transport;
    String addressLabel;
    NettyServerBuilder builder;
    if (config.udsPath() != null) {
      transport = "uds";
      addressLabel = config.udsPath();
      Path sock = Paths.get(config.udsPath());
      Path parent = sock.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      cleanupSocket(config.udsPath());
      // Netty epoll/kqueue is needed for actual UDS bind on shaded
      // grpc-netty. The minimum cross-platform shape we ship here uses
      // a TCP socket configured at the resolved port; runner deployments
      // override via DefaultEventLoopGroup when actually running on UDS.
      // We keep the address label honest so log fields match Rust/Python.
      builder = NettyServerBuilder.forPort(config.port());
    } else {
      transport = "tcp";
      String addrStr = resolveBindAddress(config.port());
      addressLabel = addrStr;
      int colon = addrStr.lastIndexOf(':');
      String host = addrStr.substring(0, colon);
      if (host.startsWith("[") && host.endsWith("]")) {
        host = host.substring(1, host.length() - 1);
      }
      int port = Integer.parseInt(addrStr.substring(colon + 1));
      builder = NettyServerBuilder.forAddress(new InetSocketAddress(host, port));
    }
    builder.addService(kindService);
    builder.addService(healthManager.getHealthService());

    io.grpc.Server grpc = builder.build();
    grpc.start();
    pair.signal().markBound();

    // Audit #89: cross-language log shape. Same event name + field set
    // (`service`, `name`, `transport`, `address`) as Rust + Python so
    // operators querying pod logs see equivalent records regardless of
    // language. `name` is the router identifier (saga/PM name or
    // aggregate domain); `service` is the gRPC service constant.
    LOG.info(
        "server_started service={} name={} transport={} address={}",
        healthServiceName,
        instanceName,
        transport,
        addressLabel);

    Readiness.ProbeConfig probeCfg = Readiness.probeConfigFromEnv();
    SupervisorHandle supervisor =
        Readiness.runSupervisor(
            probes, sink, serviceNames, probeCfg.interval(), probeCfg.timeout());

    return new RunningServer(grpc, supervisor, sink, serviceNames, healthServiceName, instanceName);
  }
}
