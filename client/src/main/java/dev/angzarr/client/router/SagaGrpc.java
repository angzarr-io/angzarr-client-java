package dev.angzarr.client.router;

import dev.angzarr.SagaHandleRequest;
import dev.angzarr.SagaResponse;
import dev.angzarr.SagaServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.util.Objects;

/**
 * gRPC servicer adapter for a unified {@link SagaRouter}.
 *
 * <p>Mirrors Python's {@code SagaGrpc} in {@code router/server.py}.
 */
public class SagaGrpc extends SagaServiceGrpc.SagaServiceImplBase {

    private final SagaRouter router;

    public SagaGrpc(SagaRouter router) {
        this.router = Objects.requireNonNull(router, "router");
    }

    public SagaRouter getRouter() {
        return router;
    }

    @Override
    public void handle(SagaHandleRequest request, StreamObserver<SagaResponse> responseObserver) {
        GrpcAdapters.invoke(responseObserver, () -> router.dispatch(request));
    }
}
