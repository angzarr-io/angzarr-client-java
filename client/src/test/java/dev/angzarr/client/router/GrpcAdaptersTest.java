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
import dev.angzarr.Projection;
import dev.angzarr.SagaHandleRequest;
import dev.angzarr.SagaResponse;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.Projector;
import dev.angzarr.client.annotations.Saga;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** End-to-end gRPC adapter tests: router + servicer wiring + exception translation. */
class GrpcAdaptersTest {

    /** Collecting StreamObserver for unit-testing servicer adapters directly. */
    static final class Collector<T> implements StreamObserver<T> {
        final List<T> values = new ArrayList<>();
        Throwable error;
        boolean completed;

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }

    public static class State {}

    @Aggregate(domain = "player", state = State.class)
    public static class Player {
        @Handles(Cover.class)
        public Notification register(Cover cmd, State s, long seq) {
            return Notification.newBuilder().setCover(cmd).build();
        }
    }

    @Aggregate(domain = "player", state = State.class)
    public static class RejectingPlayer {
        @Handles(Cover.class)
        public Notification register(Cover cmd, State s, long seq) throws CommandRejectedError {
            throw new CommandRejectedError("insufficient funds");
        }
    }

    @Saga(name = "saga", source = "order", target = "inventory")
    public static class OrderSaga {
        @Handles(Cover.class)
        public SagaHandlerResponse handle(Cover evt, dev.angzarr.client.Destinations d) {
            return SagaHandlerResponse.empty();
        }
    }

    @Projector(name = "prj", domains = {"order"})
    public static class OutputProjector {
        public boolean invoked;

        @Handles(Cover.class)
        public void onCover(Cover evt) {
            invoked = true;
        }
    }

    private static ContextualCommand cmdFor(String domain, Any payload) {
        CommandBook book =
                CommandBook.newBuilder()
                        .setCover(Cover.newBuilder().setDomain(domain).build())
                        .addPages(CommandPage.newBuilder().setCommand(payload).build())
                        .build();
        return ContextualCommand.newBuilder().setCommand(book).build();
    }

    private static Any coverAny(String domain) {
        return Any.newBuilder()
                .setTypeUrl("type.googleapis.com/angzarr_client.proto.angzarr.Cover")
                .setValue(Cover.newBuilder().setDomain(domain).build().toByteString())
                .build();
    }

    @Test
    void commandHandlerGrpcDeliversResponse() throws Exception {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg").withHandler(Player.class, Player::new).build();
        CommandHandlerGrpc svc = new CommandHandlerGrpc(router);
        Collector<BusinessResponse> c = new Collector<>();

        svc.handle(cmdFor("player", coverAny("p1")), c);

        assertThat(c.error).isNull();
        assertThat(c.completed).isTrue();
        assertThat(c.values).hasSize(1);
        Notification emitted =
                Notification.parseFrom(c.values.get(0).getEvents().getPages(0).getEvent().getValue());
        assertThat(emitted.getCover().getDomain()).isEqualTo("p1");
    }

    @Test
    void commandHandlerGrpcTranslatesDispatchException() {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg").withHandler(Player.class, Player::new).build();
        CommandHandlerGrpc svc = new CommandHandlerGrpc(router);
        Collector<BusinessResponse> c = new Collector<>();

        // Unknown type URL → DispatchException(INVALID_ARGUMENT) inside dispatch.
        Any unknown =
                Any.newBuilder()
                        .setTypeUrl("type.googleapis.com/nope.Foo")
                        .setValue(com.google.protobuf.ByteString.EMPTY)
                        .build();
        svc.handle(cmdFor("player", unknown), c);

        assertThat(c.error).isInstanceOf(StatusRuntimeException.class);
        Status st = ((StatusRuntimeException) c.error).getStatus();
        assertThat(st.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(c.completed).isFalse();
    }

    @Test
    void commandHandlerGrpcTranslatesCommandRejectedError() {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg")
                                .withHandler(RejectingPlayer.class, RejectingPlayer::new)
                                .build();
        CommandHandlerGrpc svc = new CommandHandlerGrpc(router);
        Collector<BusinessResponse> c = new Collector<>();

        svc.handle(cmdFor("player", coverAny("p1")), c);

        assertThat(c.error).isInstanceOf(StatusRuntimeException.class);
        Status st = ((StatusRuntimeException) c.error).getStatus();
        assertThat(st.getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
        assertThat(st.getDescription()).isEqualTo("insufficient funds");
    }

    @Test
    void sagaGrpcDelegatesToRouter() {
        SagaRouter router =
                (SagaRouter)
                        Router.newBuilder("sagas").withHandler(OrderSaga.class, OrderSaga::new).build();
        SagaGrpc svc = new SagaGrpc(router);
        Collector<SagaResponse> c = new Collector<>();

        EventPage page = EventPage.newBuilder().setEvent(coverAny("x")).build();
        EventBook source =
                EventBook.newBuilder()
                        .setCover(Cover.newBuilder().setDomain("order").build())
                        .addPages(page)
                        .build();
        svc.handle(SagaHandleRequest.newBuilder().setSource(source).build(), c);

        assertThat(c.error).isNull();
        assertThat(c.completed).isTrue();
        assertThat(c.values).hasSize(1);
    }

    @Test
    void projectorGrpcHandleAndHandleSpeculativeBothDispatch() {
        ProjectorRouter router =
                (ProjectorRouter)
                        Router.newBuilder("prjs")
                                .withHandler(OutputProjector.class, OutputProjector::new)
                                .build();
        ProjectorGrpc svc = new ProjectorGrpc(router);

        EventBook book =
                EventBook.newBuilder()
                        .setCover(Cover.newBuilder().setDomain("order").build())
                        .addPages(EventPage.newBuilder().setEvent(coverAny("e0")).build())
                        .build();

        Collector<Projection> c1 = new Collector<>();
        svc.handle(book, c1);
        assertThat(c1.completed).isTrue();
        assertThat(c1.values).hasSize(1);

        Collector<Projection> c2 = new Collector<>();
        svc.handleSpeculative(book, c2);
        assertThat(c2.completed).isTrue();
        assertThat(c2.values).hasSize(1);
    }
}
