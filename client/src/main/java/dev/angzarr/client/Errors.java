package dev.angzarr.client;

import io.grpc.Status;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exception types for Angzarr client errors.
 *
 * <p>Audit finding #59 (structural error model). Every error carries:
 *
 * <ul>
 *   <li>A static {@code message} — same exact string for the same predicate failure across all call
 *       sites and all six languages.
 *   <li>A stable {@code code} identifier (SCREAMING_SNAKE) — programmatic dispatch and cucumber
 *       assertions key off this. See {@link dev.angzarr.client.error_codes.Codes}.
 *   <li>Structured {@code details} — runtime context (field name, type URL, domain, status code)
 *       that varies per call site. See {@link dev.angzarr.client.error_codes.Keys}.
 * </ul>
 *
 * <p>Callers MUST NOT interpolate runtime values into {@code message}; that defeats the
 * static-string contract. Put runtime values in {@code details}.
 *
 * <p>Mirrors Python's {@code errors.py} and Rust's {@code error::ClientError} / {@code
 * error::CommandRejectedError}.
 */
public class Errors {

  /** Base exception for all Angzarr client errors. */
  public static class ClientError extends RuntimeException {

    private final String code;
    private final Map<String, String> details;

    public ClientError(String message) {
      this(message, null, "", null);
    }

    public ClientError(String message, Throwable cause) {
      this(message, cause, "", null);
    }

    /**
     * Audit #59: full constructor carrying static {@code message}, stable {@code code}, and
     * structured {@code details}.
     */
    public ClientError(String message, Throwable cause, String code, Map<String, ?> details) {
      super(message, cause);
      this.code = code == null ? "" : code;
      if (details == null || details.isEmpty()) {
        this.details = Collections.emptyMap();
      } else {
        // Stringify values so the contract matches Python's
        // `dict[str, str]` and Rust's `BTreeMap<String, String>`.
        Map<String, String> m = new LinkedHashMap<>(details.size());
        for (Map.Entry<String, ?> e : details.entrySet()) {
          m.put(e.getKey(), e.getValue() == null ? "" : e.getValue().toString());
        }
        this.details = Collections.unmodifiableMap(m);
      }
    }

    /** SCREAMING_SNAKE stable identifier (audit #59). Empty string when not set. */
    public String getCode() {
      return code;
    }

    /** Structured runtime context (audit #59). Empty map when not set. */
    public Map<String, String> getDetails() {
      return details;
    }

    /** Returns true if this is a "not found" error. */
    public boolean isNotFound() {
      return false;
    }

    /** Returns true if this is a "precondition failed" error. */
    public boolean isPreconditionFailed() {
      return false;
    }

    /** Returns true if this is an "invalid argument" error. */
    public boolean isInvalidArgument() {
      return false;
    }

    /** Returns true if this is a connection or transport error. */
    public boolean isConnectionError() {
      return false;
    }
  }

  /**
   * Thrown when a command is rejected due to business rule violation.
   *
   * <p>Status codes and retry semantics:
   *
   * <ul>
   *   <li>{@link Status#FAILED_PRECONDITION} — state-based rejection; retryable after refreshing
   *       state.
   *   <li>{@link Status#INVALID_ARGUMENT} — bad input; not retryable.
   *   <li>{@link Status#NOT_FOUND} — aggregate does not exist; not retryable.
   * </ul>
   *
   * <p>Audit finding #59: callers pass a static {@code message}, a SCREAMING_SNAKE {@code code},
   * and structured {@code details}. The factory methods ({@link #preconditionFailed}, {@link
   * #invalidArgument}, {@link #notFound}) bind the appropriate {@code statusCode}.
   */
  public static class CommandRejectedError extends ClientError {
    private final Status.Code statusCode;

    public CommandRejectedError(String message) {
      this(message, Status.Code.FAILED_PRECONDITION);
    }

    public CommandRejectedError(String message, Status.Code statusCode) {
      super(message);
      this.statusCode = statusCode;
    }

    public CommandRejectedError(
        String message, Status.Code statusCode, String code, Map<String, ?> details) {
      super(message, null, code, details);
      this.statusCode = statusCode;
    }

    public Status.Code getStatusCode() {
      return statusCode;
    }

    /**
     * Create a FAILED_PRECONDITION error for state precondition violations.
     *
     * <p>Audit #59 structural form: pass a stable code, a static message, and structured details.
     */
    public static CommandRejectedError preconditionFailed(
        String code, String message, Map<String, ?> details) {
      return new CommandRejectedError(message, Status.Code.FAILED_PRECONDITION, code, details);
    }

    /** Back-compat overload: message only, no structured fields. */
    public static CommandRejectedError preconditionFailed(String message) {
      return new CommandRejectedError(message, Status.Code.FAILED_PRECONDITION);
    }

    /** Create an INVALID_ARGUMENT error for input validation failures. */
    public static CommandRejectedError invalidArgument(
        String code, String message, Map<String, ?> details) {
      return new CommandRejectedError(message, Status.Code.INVALID_ARGUMENT, code, details);
    }

    /** Back-compat overload: message only, no structured fields. */
    public static CommandRejectedError invalidArgument(String message) {
      return new CommandRejectedError(message, Status.Code.INVALID_ARGUMENT);
    }

    /** Create a NOT_FOUND error for missing-aggregate failures. */
    public static CommandRejectedError notFound(
        String code, String message, Map<String, ?> details) {
      return new CommandRejectedError(message, Status.Code.NOT_FOUND, code, details);
    }

    /** Convert to gRPC Status for RPC responses. */
    public Status toGrpcStatus() {
      return Status.fromCode(statusCode).withDescription(getMessage());
    }

    @Override
    public boolean isPreconditionFailed() {
      return statusCode == Status.Code.FAILED_PRECONDITION;
    }

    @Override
    public boolean isInvalidArgument() {
      return statusCode == Status.Code.INVALID_ARGUMENT;
    }

    @Override
    public boolean isNotFound() {
      return statusCode == Status.Code.NOT_FOUND;
    }
  }

  /** Thrown when a gRPC call fails. */
  public static class GrpcError extends ClientError {
    private final Status.Code statusCode;

    public GrpcError(String message, Status.Code statusCode) {
      // LOW-1.12: stamp Codes.GRPC_ERROR for cross-language parity
      // with Py / Rs / Cs ctor defaults.
      super(message, null, dev.angzarr.client.error_codes.Codes.GRPC_ERROR, null);
      this.statusCode = statusCode;
    }

    public GrpcError(String message, Status.Code statusCode, Throwable cause) {
      super(message, cause, dev.angzarr.client.error_codes.Codes.GRPC_ERROR, null);
      this.statusCode = statusCode;
    }

    public Status.Code getStatusCode() {
      return statusCode;
    }

    @Override
    public boolean isNotFound() {
      return statusCode == Status.Code.NOT_FOUND;
    }

    @Override
    public boolean isPreconditionFailed() {
      return statusCode == Status.Code.FAILED_PRECONDITION;
    }

    @Override
    public boolean isInvalidArgument() {
      return statusCode == Status.Code.INVALID_ARGUMENT;
    }

    @Override
    public boolean isConnectionError() {
      return statusCode == Status.Code.UNAVAILABLE;
    }
  }

  /** Thrown when connection to the server fails. */
  public static class ConnectionError extends ClientError {
    public ConnectionError(String message) {
      super(message);
    }

    public ConnectionError(String message, Throwable cause) {
      super(message, cause);
    }

    @Override
    public boolean isConnectionError() {
      return true;
    }
  }

  /** Thrown when transport-level errors occur. */
  public static class TransportError extends ClientError {
    public TransportError(String message) {
      super(message);
    }

    public TransportError(String message, Throwable cause) {
      super(message, cause);
    }

    @Override
    public boolean isConnectionError() {
      return true;
    }
  }

  /** Thrown when an invalid argument is provided. */
  public static class InvalidArgumentError extends ClientError {
    public InvalidArgumentError(String message) {
      super(message);
    }

    public InvalidArgumentError(String message, String code, Map<String, ?> details) {
      super(message, null, code, details);
    }

    @Override
    public boolean isInvalidArgument() {
      return true;
    }
  }

  /** Thrown when a timestamp cannot be parsed. */
  public static class InvalidTimestampError extends ClientError {
    public InvalidTimestampError(String message) {
      super(message);
    }

    public InvalidTimestampError(String message, Throwable cause) {
      super(message, cause);
    }

    public InvalidTimestampError(String message, Throwable cause, Map<String, ?> details) {
      super(message, cause, dev.angzarr.client.error_codes.Codes.TIMESTAMP_PARSE_FAILED, details);
    }
  }
}
