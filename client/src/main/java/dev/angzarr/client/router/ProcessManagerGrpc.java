package dev.angzarr.client.router;

import dev.angzarr.ProcessManagerHandleRequest;
import dev.angzarr.ProcessManagerHandleResponse;
import dev.angzarr.ProcessManagerPrepareRequest;
import dev.angzarr.ProcessManagerPrepareResponse;
import dev.angzarr.ProcessManagerServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.util.Objects;

/**
 * gRPC servicer adapter for a unified {@link ProcessManagerRouter}.
 *
 * <p>{@code handle} delegates to the router. {@code prepare} returns an empty response —
 * destinations are config-driven in the Tier 5 model, so there is nothing to prepare at runtime.
 *
 * <p>Mirrors Python's {@code ProcessManagerGrpc} in {@code router/server.py}.
 */
public class ProcessManagerGrpc extends ProcessManagerServiceGrpc.ProcessManagerServiceImplBase {

    private final ProcessManagerRouter<?> router;

    public ProcessManagerGrpc(ProcessManagerRouter<?> router) {
        this.router = Objects.requireNonNull(router, "router");
    }

    public ProcessManagerRouter<?> getRouter() {
        return router;
    }

    @Override
    public void prepare(
            ProcessManagerPrepareRequest request,
            StreamObserver<ProcessManagerPrepareResponse> responseObserver) {
        GrpcAdapters.invoke(
                responseObserver, () -> ProcessManagerPrepareResponse.getDefaultInstance());
    }

    @Override
    public void handle(
            ProcessManagerHandleRequest request,
            StreamObserver<ProcessManagerHandleResponse> responseObserver) {
        GrpcAdapters.invoke(responseObserver, () -> router.dispatch(request));
    }
}
