package dev.angzarr.client.server;

import static org.assertj.core.api.Assertions.assertThat;

import dev.angzarr.Cover;
import dev.angzarr.client.Destinations;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.Saga;
import dev.angzarr.client.router.Router;
import dev.angzarr.client.router.SagaGrpc;
import dev.angzarr.client.router.SagaRouter;
import io.grpc.ManagedChannel;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * End-to-end startup test: spin up a saga server with a no-output router (so probes consist of just
 * the {@link Readiness.TransportProbe}), then poll the health endpoint until it flips to {@code
 * SERVING}. Audit #68: starts at {@code NOT_SERVING}, supervisor flips it once the transport is
 * bound.
 */
class ServerStartupTest {

  @Saga(name = "noopSaga", source = "order", target = "inventory", sync = true)
  static final class NoopSaga {
    @Handles(Cover.class)
    public Object on(Cover c, Destinations d) {
      return null;
    }
  }

  private static int freePort() throws Exception {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }

  @Test
  void healthFlipsToServingOnceTransportIsBound() throws Exception {
    // Build a saga router but use a non-routable resolver so the
    // OutputDomainProbe stays red — for the SERVING flip we instead
    // build a router with no sync targets via a CH router-style.
    // Easier: build a saga router where the sync target resolves to a
    // listening socket (we can host one ourselves on a free port).
    SagaRouter router =
        (SagaRouter)
            Router.newBuilder("noopSaga").withHandler(NoopSaga.class, NoopSaga::new).build();

    // Stand up a fake "downstream" listener so the OutputDomainProbe
    // for "inventory" succeeds; this is what makes the readiness
    // supervisor publish SERVING.
    int downstreamPort = freePort();
    try (ServerSocket downstream = new ServerSocket(downstreamPort)) {
      int serverPort = freePort();
      // Provide an explicit PORT via system property so ServerConfig
      // picks it up — System::getenv can't be overridden mid-process,
      // so we go through a direct internal start instead.
      var probes = Server.probesForSaga(router, d -> "127.0.0.1:" + downstreamPort);
      io.grpc.BindableService grpcService = new SagaGrpc(router);
      // Hand-build a kind start with explicit defaultPort.
      try (var running =
          Server.startKind(
              router.name(), grpcService, Server.HEALTH_NAME_SAGA, probes, serverPort)) {
        // Connect a health client and poll.
        ManagedChannel chan =
            NettyChannelBuilder.forAddress("127.0.0.1", serverPort)
                .negotiationType(NegotiationType.PLAINTEXT)
                .build();
        try {
          var stub = HealthGrpc.newBlockingStub(chan);
          HealthCheckResponse.ServingStatus seen = null;
          long deadline = System.currentTimeMillis() + 5000;
          while (System.currentTimeMillis() < deadline) {
            var resp =
                stub.check(
                    HealthCheckRequest.newBuilder().setService(Server.HEALTH_NAME_SAGA).build());
            seen = resp.getStatus();
            if (seen == HealthCheckResponse.ServingStatus.SERVING) {
              break;
            }
            Thread.sleep(50);
          }
          assertThat(seen).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        } finally {
          chan.shutdown().awaitTermination(2, TimeUnit.SECONDS);
        }
      }
    }
  }
}
