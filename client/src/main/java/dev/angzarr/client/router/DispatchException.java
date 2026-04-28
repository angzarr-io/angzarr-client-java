package dev.angzarr.client.router;

import io.grpc.Status;

/**
 * Raised by router {@code dispatch} methods for caller-facing problems (unknown type URL, missing
 * domain, bad payload, invalid handler return value).
 *
 * <p>Carries an {@link io.grpc.Status.Code} so the gRPC wrapper layer can forward the correct
 * status to the client without additional mapping.
 */
public class DispatchException extends RuntimeException {

    private final Status.Code code;

    public DispatchException(Status.Code code, String message) {
        super(message);
        this.code = code;
    }

    public DispatchException(Status.Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Status.Code code() {
        return code;
    }

    public Status toStatus() {
        return Status.fromCode(code).withDescription(getMessage());
    }
}
