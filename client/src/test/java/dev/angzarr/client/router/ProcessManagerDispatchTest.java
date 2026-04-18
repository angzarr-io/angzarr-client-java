package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.Cover;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.PageHeader;
import dev.angzarr.ProcessManagerHandleRequest;
import dev.angzarr.ProcessManagerHandleResponse;
import dev.angzarr.client.Destinations;
import dev.angzarr.client.annotations.Applies;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.ProcessManager;
import java.util.List;
import org.junit.jupiter.api.Test;

/** R12 — ProcessManagerRouter.dispatch: multi-source, state rebuild, destinations stamping. */
class ProcessManagerDispatchTest {

    public static class PmState {
        public int seen;
    }

    @ProcessManager(
            name = "pm-fulfillment",
            pmDomain = "fulfillment",
            sources = {"order", "inventory"},
            targets = {"shipping"},
            state = PmState.class)
    public static class FulfillmentPm {
        @Applies(Cover.class)
        public void onCover(PmState s, Cover evt) {
            s.seen += 1;
        }

        @Handles(Cover.class)
        public ProcessManagerResponse handle(Cover evt, PmState state, Destinations destinations) {
            int shipSeq = destinations.sequenceFor("shipping").orElse(0);
            CommandBook cmd =
                    CommandBook.newBuilder()
                            .setCover(Cover.newBuilder().setDomain("shipping").build())
                            .addPages(
                                    CommandPage.newBuilder()
                                            .setHeader(
                                                    PageHeader.newBuilder()
                                                            .setSequence(shipSeq + state.seen)
                                                            .build())
                                            .build())
                            .build();
            return ProcessManagerResponse.withCommands(List.of(cmd));
        }
    }

    private static ProcessManagerHandleRequest buildRequest(
            String triggerDomain, EventBook processState) {
        Any packed =
                Any.newBuilder()
                        .setTypeUrl("type.googleapis.com/angzarr.Cover")
                        .setValue(Cover.getDefaultInstance().toByteString())
                        .build();
        EventPage page = EventPage.newBuilder().setEvent(packed).build();
        EventBook trigger =
                EventBook.newBuilder()
                        .setCover(Cover.newBuilder().setDomain(triggerDomain).build())
                        .addPages(page)
                        .build();
        ProcessManagerHandleRequest.Builder b =
                ProcessManagerHandleRequest.newBuilder()
                        .setTrigger(trigger)
                        .putDestinationSequences("shipping", 100);
        if (processState != null) b.setProcessState(processState);
        return b.build();
    }

    private static EventBook priorStateWithOneCover() {
        Any packed =
                Any.newBuilder()
                        .setTypeUrl("type.googleapis.com/angzarr.Cover")
                        .setValue(Cover.getDefaultInstance().toByteString())
                        .build();
        EventPage page = EventPage.newBuilder().setEvent(packed).build();
        return EventBook.newBuilder().addPages(page).build();
    }

    @Test
    void pmDispatchRebuildsStateAndStampsCommand() {
        ProcessManagerRouter<?> router =
                (ProcessManagerRouter<?>)
                        Router.newBuilder("pms")
                                .withHandler(FulfillmentPm.class, FulfillmentPm::new)
                                .build();

        ProcessManagerHandleResponse resp =
                router.dispatch(buildRequest("order", priorStateWithOneCover()));

        assertThat(resp.getCommandsCount()).isEqualTo(1);
        // shipping seq = 100, seen = 1 (one prior event replayed) → 101
        assertThat(resp.getCommands(0).getPages(0).getHeader().getSequence()).isEqualTo(101);
    }

    @Test
    void pmIgnoresTriggerOutsideSources() {
        ProcessManagerRouter<?> router =
                (ProcessManagerRouter<?>)
                        Router.newBuilder("pms")
                                .withHandler(FulfillmentPm.class, FulfillmentPm::new)
                                .build();

        ProcessManagerHandleResponse resp = router.dispatch(buildRequest("unrelated", null));

        assertThat(resp.getCommandsCount()).isEqualTo(0);
    }
}
