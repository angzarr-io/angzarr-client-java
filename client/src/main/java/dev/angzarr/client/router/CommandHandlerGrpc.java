package dev.angzarr.client.router;

import dev.angzarr.BusinessResponse;
import dev.angzarr.CommandHandlerServiceGrpc;
import dev.angzarr.ContextualCommand;
import io.grpc.stub.StreamObserver;
import java.util.Objects;

/**
 * gRPC servicer adapter for a unified {@link CommandHandlerRouter}.
 *
 * <p>Implements {@code CommandHandlerService.handle} by delegating to the router's
 * {@link CommandHandlerRouter#dispatch(ContextualCommand)}. {@code handleFact} and {@code replay}
 * fall through to the generated default ({@code UNIMPLEMENTED}) — add overrides in a subclass if
 * your aggregate needs them.
 *
 * <p>Mirrors Python's {@code CommandHandlerGrpc} in {@code router/server.py}.
 */
public class CommandHandlerGrpc extends CommandHandlerServiceGrpc.CommandHandlerServiceImplBase {

    private final CommandHandlerRouter<?> router;

    public CommandHandlerGrpc(CommandHandlerRouter<?> router) {
        this.router = Objects.requireNonNull(router, "router");
    }

    public CommandHandlerRouter<?> getRouter() {
        return router;
    }

    @Override
    public void handle(
            ContextualCommand request, StreamObserver<BusinessResponse> responseObserver) {
        GrpcAdapters.invoke(responseObserver, () -> router.dispatch(request));
    }
}
