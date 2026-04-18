package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import dev.angzarr.BusinessResponse;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.ContextualCommand;
import dev.angzarr.Cover;
import dev.angzarr.Notification;
import dev.angzarr.RejectionNotification;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.Rejected;
import org.junit.jupiter.api.Test;

/** R10 — @Rejected routing: notifications mapped to compensation handlers. */
class RejectionTest {

    public static class State {}

    @Aggregate(domain = "order", state = State.class)
    public static class OrderWithCompensation {
        @Handles(Cover.class)
        public Notification handle(Cover cmd, State s, long seq) {
            return Notification.newBuilder().build();
        }

        @Rejected(domain = "payment", command = "ProcessPayment")
        public Notification onPaymentRejected(Notification notif, State s) {
            return Notification.newBuilder()
                    .setCover(Cover.newBuilder().setDomain("compensated").build())
                    .build();
        }
    }

    @Aggregate(domain = "order", state = State.class)
    public static class OtherAggregateForMerge {
        @Handles(Cover.class)
        public Notification handle(Cover cmd, State s, long seq) {
            return Notification.newBuilder().build();
        }

        @Rejected(domain = "payment", command = "ProcessPayment")
        public Notification onPaymentRejected(Notification notif, State s) {
            return Notification.newBuilder()
                    .setCover(Cover.newBuilder().setDomain("also-compensated").build())
                    .build();
        }
    }

    /** Build a ContextualCommand whose payload is a Notification wrapping a RejectionNotification. */
    private static ContextualCommand rejectionCommand(String rejectedDomain, String rejectedTypeUrl) {
        Any rejectedCmdPayload =
                Any.newBuilder().setTypeUrl(rejectedTypeUrl).setValue(com.google.protobuf.ByteString.EMPTY).build();
        CommandBook rejectedCommand =
                CommandBook.newBuilder()
                        .setCover(Cover.newBuilder().setDomain(rejectedDomain).build())
                        .addPages(CommandPage.newBuilder().setCommand(rejectedCmdPayload).build())
                        .build();
        RejectionNotification rej =
                RejectionNotification.newBuilder().setRejectedCommand(rejectedCommand).build();
        Notification notif =
                Notification.newBuilder().setPayload(Any.pack(rej)).build();
        Any notifAny =
                Any.newBuilder()
                        .setTypeUrl("type.googleapis.com/angzarr.Notification")
                        .setValue(notif.toByteString())
                        .build();
        CommandBook book =
                CommandBook.newBuilder()
                        .setCover(Cover.newBuilder().setDomain("order").build())
                        .addPages(CommandPage.newBuilder().setCommand(notifAny).build())
                        .build();
        return ContextualCommand.newBuilder().setCommand(book).build();
    }

    @Test
    void rejectedHandlerTriggeredOnNotification() throws Exception {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg")
                                .withHandler(
                                        OrderWithCompensation.class, OrderWithCompensation::new)
                                .build();

        BusinessResponse resp =
                router.dispatch(
                        rejectionCommand(
                                "payment", "type.googleapis.com/angzarr.ProcessPayment"));

        assertThat(resp.getEvents().getPagesCount()).isEqualTo(1);
        Notification emitted =
                Notification.parseFrom(resp.getEvents().getPages(0).getEvent().getValue());
        assertThat(emitted.getCover().getDomain()).isEqualTo("compensated");
    }

    @Test
    void multipleRejectedHandlersMerge() throws Exception {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg")
                                .withHandler(
                                        OrderWithCompensation.class, OrderWithCompensation::new)
                                .withHandler(
                                        OtherAggregateForMerge.class, OtherAggregateForMerge::new)
                                .build();

        BusinessResponse resp =
                router.dispatch(
                        rejectionCommand(
                                "payment", "type.googleapis.com/angzarr.ProcessPayment"));

        assertThat(resp.getEvents().getPagesCount()).isEqualTo(2);
        Notification first =
                Notification.parseFrom(resp.getEvents().getPages(0).getEvent().getValue());
        Notification second =
                Notification.parseFrom(resp.getEvents().getPages(1).getEvent().getValue());
        assertThat(first.getCover().getDomain()).isEqualTo("compensated");
        assertThat(second.getCover().getDomain()).isEqualTo("also-compensated");
    }

    @Test
    void noMatchingRejectedHandlerReturnsEmpty() {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg")
                                .withHandler(
                                        OrderWithCompensation.class, OrderWithCompensation::new)
                                .build();

        // A rejection for an unrelated (domain, command) key.
        BusinessResponse resp =
                router.dispatch(
                        rejectionCommand(
                                "inventory", "type.googleapis.com/angzarr.ReserveStock"));

        assertThat(resp.getEvents().getPagesCount()).isEqualTo(0);
    }
}
