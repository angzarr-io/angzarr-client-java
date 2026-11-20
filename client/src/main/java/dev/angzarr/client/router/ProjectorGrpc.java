package dev.angzarr.client.router;

import dev.angzarr.EventBook;
import dev.angzarr.Projection;
import dev.angzarr.ProjectorServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.util.Objects;

/**
 * gRPC servicer adapter for a unified {@link ProjectorRouter}.
 *
 * <p>Both {@code handle} and {@code handleSpeculative} route to the same dispatch path —
 * speculative runs are identical at the routing layer; the distinction lives in upstream cascade
 * semantics.
 *
 * <p>Mirrors Python's {@code ProjectorGrpc} in {@code router/server.py}.
 */
public class ProjectorGrpc extends ProjectorServiceGrpc.ProjectorServiceImplBase {

    private final ProjectorRouter router;

    public ProjectorGrpc(ProjectorRouter router) {
        this.router = Objects.requireNonNull(router, "router");
    }

    public ProjectorRouter getRouter() {
        return router;
    }

    @Override
    public void handle(EventBook request, StreamObserver<Projection> responseObserver) {
        GrpcAdapters.invoke(responseObserver, () -> router.dispatch(request));
    }

    @Override
    public void handleSpeculative(EventBook request, StreamObserver<Projection> responseObserver) {
        GrpcAdapters.invoke(responseObserver, () -> router.dispatch(request));
    }
}
