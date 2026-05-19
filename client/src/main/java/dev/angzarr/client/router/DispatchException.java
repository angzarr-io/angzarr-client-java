package dev.angzarr.client.router;

import io.grpc.Status;
import java.util.Collections;
import java.util.Map;

/**
 * Raised by router {@code dispatch} methods for caller-facing problems (unknown type URL, missing
 * domain, bad payload, invalid handler return value).
 *
 * <p>Carries an {@link io.grpc.Status.Code} so the gRPC wrapper layer can forward the correct
 * status to the client without additional mapping.
 *
 * <p>Audit #59 / #87: optionally carries a stable {@code errorCode} and structured {@code details}
 * map so downstream tooling can route on a stable identifier instead of substring- matching the
 * message. Mirrors {@link dev.angzarr.client.Errors.ClientError} + Python {@code
 * DispatchError(error_code=..., extras=...)} + Rust {@code ClientError::invalid_argument(code, msg,
 * details)}.
 */
public class DispatchException extends RuntimeException {

  private final Status.Code code;
  private final String errorCode;
  private final Map<String, String> details;

  public DispatchException(Status.Code code, String message) {
    this(code, message, "", Collections.emptyMap(), null);
  }

  public DispatchException(Status.Code code, String message, Throwable cause) {
    this(code, message, "", Collections.emptyMap(), cause);
  }

  /**
   * Audit #59 / #87: full constructor carrying static {@code message}, stable {@code errorCode},
   * and structured {@code details}. The {@code message} should be a fixed string; runtime specifics
   * go in {@code details} so log aggregation can group by code/message.
   */
  public DispatchException(
      Status.Code code,
      String message,
      String errorCode,
      Map<String, String> details,
      Throwable cause) {
    super(message, cause);
    this.code = code;
    this.errorCode = errorCode == null ? "" : errorCode;
    this.details = details == null ? Collections.emptyMap() : Map.copyOf(details);
  }

  public Status.Code code() {
    return code;
  }

  /** Stable, language-portable error code (e.g. {@code ANY_DECODE_FAILED}); empty when unset. */
  public String getErrorCode() {
    return errorCode;
  }

  /** Structured runtime context — empty map by default. */
  public Map<String, String> getDetails() {
    return details;
  }

  public Status toStatus() {
    return Status.fromCode(code).withDescription(getMessage());
  }
}
