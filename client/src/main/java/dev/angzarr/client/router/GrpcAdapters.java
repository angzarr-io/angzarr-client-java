package dev.angzarr.client.router;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.function.Supplier;

/**
 * Shared plumbing for the four gRPC servicer adapters.
 *
 * <p>Runs a router dispatch inside a try/catch, forwards the result to the response observer on
 * success, and translates known exceptions into gRPC {@link Status}es on failure:
 *
 * <ul>
 *   <li>{@link CommandRejectedError} → {@link Status.Code#FAILED_PRECONDITION}
 *   <li>{@link DispatchException} → whatever {@link DispatchException#code()} carries
 *   <li>Anything else → {@link Status.Code#INTERNAL}
 * </ul>
 */
final class GrpcAdapters {

    private GrpcAdapters() {}

    /** Execute {@code dispatch} and deliver to {@code observer}, translating exceptions. */
    static <T> void invoke(StreamObserver<T> observer, Supplier<T> dispatch) {
        try {
            T result = dispatch.get();
            observer.onNext(result);
            observer.onCompleted();
        } catch (Throwable t) {
            observer.onError(toStatus(t).asRuntimeException());
        }
    }

    static Status toStatus(Throwable t) {
        // A user-thrown rejection may arrive wrapped by MethodHandle.invoke and re-wrapped as a
        // DispatchException(INTERNAL). Walk the cause chain first and let the most specific
        // rejection signal win. Errors.CommandRejectedError (legacy client API) carries its own
        // Status.Code — honor it. The router-package CommandRejectedError maps to
        // FAILED_PRECONDITION by convention.
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof dev.angzarr.client.Errors.CommandRejectedError cre) {
                return cre.toGrpcStatus();
            }
            if (cur instanceof CommandRejectedError cre) {
                return Status.FAILED_PRECONDITION.withDescription(cre.getReason());
            }
        }
        if (t instanceof DispatchException de) {
            return de.toStatus();
        }
        return Status.INTERNAL.withDescription(t.getMessage()).withCause(t);
    }
}
