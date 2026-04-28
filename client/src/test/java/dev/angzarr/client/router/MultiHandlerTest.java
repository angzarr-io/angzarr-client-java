package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import dev.angzarr.BusinessResponse;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.ContextualCommand;
import dev.angzarr.Cover;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.Notification;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Applies;
import dev.angzarr.client.annotations.Handles;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** R8–R9 — multi-handler fan-out, state isolation, and seq increments across merged outputs. */
class MultiHandlerTest {

    public static class State {
        public int applied;
    }

    @Aggregate(domain = "shared", state = State.class)
    public static class HandlerA {
        public static final AtomicInteger FACTORY_CALLS = new AtomicInteger();

        @Applies(Cover.class)
        public void onCover(State s, Cover evt) {
            s.applied += 1;
        }

        @Handles(Cover.class)
        public List<Notification> handle(Cover cmd, State s, long seq) {
            return List.of(
                    Notification.newBuilder()
                            .setCover(Cover.newBuilder().setDomain("A1@" + seq + ",applied=" + s.applied))
                            .build(),
                    Notification.newBuilder()
                            .setCover(Cover.newBuilder().setDomain("A2@" + (seq + 1)))
                            .build());
        }
    }

    @Aggregate(domain = "shared", state = State.class)
    public static class HandlerB {
        public static final AtomicInteger FACTORY_CALLS = new AtomicInteger();

        @Applies(Cover.class)
        public void onCover(State s, Cover evt) {
            s.applied += 100;
        }

        @Handles(Cover.class)
        public Notification handle(Cover cmd, State s, long seq) {
            return Notification.newBuilder()
                    .setCover(Cover.newBuilder().setDomain("B1@" + seq + ",applied=" + s.applied))
                    .build();
        }
    }

    private static ContextualCommand cmdFor(String domain, EventBook prior) {
        Any packed =
                Any.newBuilder()
                        .setTypeUrl("type.googleapis.com/angzarr.Cover")
                        .setValue(Cover.newBuilder().setDomain(domain).build().toByteString())
                        .build();
        CommandBook book =
                CommandBook.newBuilder()
                        .setCover(Cover.newBuilder().setDomain(domain).build())
                        .addPages(CommandPage.newBuilder().setCommand(packed).build())
                        .build();
        ContextualCommand.Builder b = ContextualCommand.newBuilder().setCommand(book);
        if (prior != null) b.setEvents(prior);
        return b.build();
    }

    private static EventBook priorWithOneCoverAtSeq(int seq) {
        Any packed =
                Any.newBuilder()
                        .setTypeUrl("type.googleapis.com/angzarr.Cover")
                        .setValue(Cover.getDefaultInstance().toByteString())
                        .build();
        EventPage page =
                EventPage.newBuilder()
                        .setHeader(dev.angzarr.PageHeader.newBuilder().setSequence(seq).build())
                        .setEvent(packed)
                        .build();
        return EventBook.newBuilder().addPages(page).setNextSequence(seq + 1).build();
    }

    // --- R8: multi-handler fan-out, registration order, state isolation ---

    @Test
    void bothHandlersInvokedInRegistrationOrder() throws Exception {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg")
                                .withHandler(HandlerA.class, HandlerA::new)
                                .withHandler(HandlerB.class, HandlerB::new)
                                .build();
        BusinessResponse resp = router.dispatch(cmdFor("shared", null));

        assertThat(resp.getEvents().getPagesCount()).isEqualTo(3); // 2 from A + 1 from B
        Notification first =
                Notification.parseFrom(resp.getEvents().getPages(0).getEvent().getValue());
        Notification second =
                Notification.parseFrom(resp.getEvents().getPages(1).getEvent().getValue());
        Notification third =
                Notification.parseFrom(resp.getEvents().getPages(2).getEvent().getValue());
        assertThat(first.getCover().getDomain()).startsWith("A1");
        assertThat(second.getCover().getDomain()).startsWith("A2");
        assertThat(third.getCover().getDomain()).startsWith("B1");
    }

    @Test
    void factoriesInvokedOncePerMatchedHandlerPerDispatch() {
        HandlerA.FACTORY_CALLS.set(0);
        HandlerB.FACTORY_CALLS.set(0);
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg")
                                .withHandler(
                                        HandlerA.class,
                                        () -> {
                                            HandlerA.FACTORY_CALLS.incrementAndGet();
                                            return new HandlerA();
                                        })
                                .withHandler(
                                        HandlerB.class,
                                        () -> {
                                            HandlerB.FACTORY_CALLS.incrementAndGet();
                                            return new HandlerB();
                                        })
                                .build();
        router.dispatch(cmdFor("shared", null));
        assertThat(HandlerA.FACTORY_CALLS).hasValue(1);
        assertThat(HandlerB.FACTORY_CALLS).hasValue(1);
        router.dispatch(cmdFor("shared", null));
        assertThat(HandlerA.FACTORY_CALLS).hasValue(2);
        assertThat(HandlerB.FACTORY_CALLS).hasValue(2);
    }

    @Test
    void eachHandlerRebuildsStateIndependently() throws Exception {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg")
                                .withHandler(HandlerA.class, HandlerA::new)
                                .withHandler(HandlerB.class, HandlerB::new)
                                .build();
        BusinessResponse resp = router.dispatch(cmdFor("shared", priorWithOneCoverAtSeq(0)));

        Notification a1 =
                Notification.parseFrom(resp.getEvents().getPages(0).getEvent().getValue());
        Notification b1 =
                Notification.parseFrom(resp.getEvents().getPages(2).getEvent().getValue());
        assertThat(a1.getCover().getDomain()).contains("applied=1"); // A's +1
        assertThat(b1.getCover().getDomain()).contains("applied=100"); // B's +100
    }

    // --- R9: seq increments monotonically across the merged stream ---

    @Test
    void sequenceIncrementsAcrossMergedStream() {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg")
                                .withHandler(HandlerA.class, HandlerA::new)
                                .withHandler(HandlerB.class, HandlerB::new)
                                .build();
        BusinessResponse resp = router.dispatch(cmdFor("shared", priorWithOneCoverAtSeq(4)));

        // Prior.next_sequence = 5 → base_seq = 5
        assertThat(resp.getEvents().getPagesCount()).isEqualTo(3);
        assertThat(resp.getEvents().getPages(0).getHeader().getSequence()).isEqualTo(5);
        assertThat(resp.getEvents().getPages(1).getHeader().getSequence()).isEqualTo(6);
        assertThat(resp.getEvents().getPages(2).getHeader().getSequence()).isEqualTo(7);
    }

    @Test
    void handlerReceivesOffsetSeqBasedOnPriorHandlersOutput() throws Exception {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg")
                                .withHandler(HandlerA.class, HandlerA::new)
                                .withHandler(HandlerB.class, HandlerB::new)
                                .build();
        BusinessResponse resp = router.dispatch(cmdFor("shared", priorWithOneCoverAtSeq(4)));
        // HandlerA emits 2 events starting at seq=5; HandlerB should then be invoked with seq=7.
        Notification b1 =
                Notification.parseFrom(resp.getEvents().getPages(2).getEvent().getValue());
        assertThat(b1.getCover().getDomain()).contains("B1@7");
    }
}
