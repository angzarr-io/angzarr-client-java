package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.ContextualCommand;
import dev.angzarr.Cover;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.error_codes.Codes;
import dev.angzarr.client.error_codes.Keys;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

/**
 * Audit #87 (Python audit #74): decode failures surface as {@code INVALID_ARGUMENT} with the {@code
 * ANY_DECODE_FAILED} stable code and structured details. Mirrors Python's {@code
 * test_saga_dispatch_garbage_payload_surfaces_invalid_argument}.
 */
class DispatchDecodeErrorTest {

  @Aggregate(domain = "order", state = OrderState.class)
  static final class OrderHandler {
    @Handles(Cover.class)
    public Object handle(Cover c, OrderState s, long seq) {
      return null;
    }
  }

  public static final class OrderState {}

  @Test
  void garbagePayloadProducesInvalidArgumentWithAnyDecodeFailedCode() {
    CommandHandlerRouter<?> router =
        (CommandHandlerRouter<?>)
            Router.newBuilder("agg").withHandler(OrderHandler.class, OrderHandler::new).build();

    // Build a syntactically-valid request — Cover type URL, but
    // payload bytes are garbage that can't deserialize.
    Any garbage =
        Any.newBuilder()
            .setTypeUrl("type.googleapis.com/angzarr_client.proto.angzarr.Cover")
            .setValue(
                ByteString.copyFrom(
                    new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff}))
            .build();
    CommandBook book =
        CommandBook.newBuilder()
            .setCover(Cover.newBuilder().setDomain("order").build())
            .addPages(CommandPage.newBuilder().setCommand(garbage).build())
            .build();
    ContextualCommand req = ContextualCommand.newBuilder().setCommand(book).build();

    assertThatThrownBy(() -> router.dispatch(req))
        .isInstanceOf(DispatchException.class)
        .satisfies(
            ex -> {
              DispatchException de = (DispatchException) ex;
              assertThat(de.code()).isEqualTo(Status.Code.INVALID_ARGUMENT);
              assertThat(de.getErrorCode()).isEqualTo(Codes.ANY_DECODE_FAILED);
              assertThat(de.getDetails())
                  .containsEntry(
                      Keys.TYPE_URL, "type.googleapis.com/angzarr_client.proto.angzarr.Cover");
              assertThat(de.getDetails()).containsKey(Keys.CAUSE);
            });
  }
}
