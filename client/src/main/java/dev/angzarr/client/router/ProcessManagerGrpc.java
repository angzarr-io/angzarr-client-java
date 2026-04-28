package dev.angzarr.client.router;

import dev.angzarr.ProcessManagerHandleRequest;
import dev.angzarr.ProcessManagerHandleResponse;
import dev.angzarr.ProcessManagerServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.util.Objects;

/**
 * gRPC servicer adapter for a unified {@link ProcessManagerRouter}.
 *
 * <p>{@code handle} delegates to the router. PMs translate trigger events plus their own
 * state into commands/facts and rely on destination_sequences for command stamping.
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
    public void handle(
            ProcessManagerHandleRequest request,
            StreamObserver<ProcessManagerHandleResponse> responseObserver) {
        GrpcAdapters.invoke(responseObserver, () -> router.dispatch(request));
    }
}
