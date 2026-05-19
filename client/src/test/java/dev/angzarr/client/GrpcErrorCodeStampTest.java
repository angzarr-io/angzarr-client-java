package dev.angzarr.client;

import static org.assertj.core.api.Assertions.assertThat;

import dev.angzarr.client.error_codes.Codes;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

/**
 * LOW-1.12 — Java's {@link Errors.GrpcError} must stamp {@link Codes#GRPC_ERROR} on construction so
 * cross-language assertions on {@code error.code == GRPC_ERROR} pass. Pre-fix, Py / Rs / Cs all
 * stamped it; Java's ctor left the code empty.
 */
class GrpcErrorCodeStampTest {

  @Test
  void grpcErrorStampsCanonicalCodeByDefault() {
    Errors.GrpcError err = new Errors.GrpcError("grpc error", Status.Code.UNAVAILABLE);
    assertThat(err.getCode()).isEqualTo(Codes.GRPC_ERROR);
  }

  @Test
  void grpcErrorWithCauseStampsCanonicalCode() {
    Throwable cause = new RuntimeException("network blip");
    Errors.GrpcError err = new Errors.GrpcError("grpc error", Status.Code.UNAVAILABLE, cause);
    assertThat(err.getCode()).isEqualTo(Codes.GRPC_ERROR);
  }
}
