package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.Cover;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.PageHeader;
import dev.angzarr.SagaHandleRequest;
import dev.angzarr.SagaResponse;
import dev.angzarr.client.Destinations;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.Saga;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R11 — SagaRouter.dispatch: trigger-driven, destinations-aware, multi-handler merge. */
class SagaDispatchTest {

    @Saga(name = "order-to-inventory", source = "order", target = "inventory")
    public static class OrderSaga {
        @Handles(Cover.class)
        public SagaHandlerResponse onOrderCreated(Cover evt, Destinations destinations) {
            int seq = destinations.sequenceFor("inventory").orElse(0);
            CommandBook cmd =
                    CommandBook.newBuilder()
                            .setCover(Cover.newBuilder().setDomain("inventory").build())
                            .addPages(
                                    CommandPage.newBuilder()
                                            .setHeader(
                                                    PageHeader.newBuilder()
                                                            .setSequence(seq)
                                                            .build())
                                            .build())
                            .build();
            return SagaHandlerResponse.withCommands(List.of(cmd));
        }
    }

    private static SagaHandleRequest buildRequest(String sourceDomain) {
        Any packed =
                Any.newBuilder()
                        .setTypeUrl("type.googleapis.com/angzarr.Cover")
                        .setValue(Cover.getDefaultInstance().toByteString())
                        .build();
        EventPage page = EventPage.newBuilder().setEvent(packed).build();
        EventBook book =
                EventBook.newBuilder()
                        .setCover(Cover.newBuilder().setDomain(sourceDomain).build())
                        .addPages(page)
                        .build();
        return SagaHandleRequest.newBuilder()
                .setSource(book)
                .putDestinationSequences("inventory", 42)
                .build();
    }

    @Test
    void sagaDispatchProducesCommandStampedWithDestinationSeq() {
        SagaRouter router =
                (SagaRouter)
                        Router.newBuilder("sagas")
                                .withHandler(OrderSaga.class, OrderSaga::new)
                                .build();

        SagaResponse resp = router.dispatch(buildRequest("order"));

        assertThat(resp.getCommandsCount()).isEqualTo(1);
        assertThat(resp.getCommands(0).getPages(0).getHeader().getSequence()).isEqualTo(42);
    }

    @Test
    void mismatchedSourceYieldsEmptyResponse() {
        SagaRouter router =
                (SagaRouter)
                        Router.newBuilder("sagas")
                                .withHandler(OrderSaga.class, OrderSaga::new)
                                .build();

        SagaResponse resp = router.dispatch(buildRequest("something-else"));

        assertThat(resp.getCommandsCount()).isEqualTo(0);
    }
}
