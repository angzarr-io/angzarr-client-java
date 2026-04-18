package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.Any;
import dev.angzarr.BusinessResponse;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.ContextualCommand;
import dev.angzarr.Cover;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.Notification;
import dev.angzarr.PageHeader;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Applies;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.StateFactory;
import io.grpc.Status;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** R6-R7 — CommandHandlerRouter.dispatch: single-handler + state rebuild from @Applies. */
class DispatchCommandHandlerTest {

    // --- State ---
    public static class PlayerState {
        public int applied;
        public int balance;
    }

    // --- Handler (R6): @Handles emits a Notification proxy as a fake "event" ---
    @Aggregate(domain = "player", state = PlayerState.class)
    public static class Player {
        public static final AtomicInteger FACTORY_CALLS = new AtomicInteger();

        @Handles(Cover.class)
        public Notification register(Cover cmd, PlayerState state, long seq) {
            // Return a distinct Message type so we can assert on it in the merged EventBook.
            return Notification.newBuilder().setCover(cmd).build();
        }
    }

    // --- Handler with @Applies (R7) ---
    @Aggregate(domain = "balance", state = PlayerState.class)
    public static class BalancePlayer {
        @Applies(Cover.class)
        public void onCover(PlayerState state, Cover evt) {
            state.applied += 1;
            state.balance = evt.getDomain().length();
        }

        @Handles(Cover.class)
        public Notification handle(Cover cmd, PlayerState state, long seq) {
            return Notification.newBuilder()
                    .setCover(Cover.newBuilder().setDomain("seen=" + state.applied).build())
                    .build();
        }
    }

    // --- Handler with @StateFactory (R7) ---
    @Aggregate(domain = "custom", state = PlayerState.class)
    public static class CustomStatePlayer {
        @StateFactory
        public PlayerState fresh() {
            PlayerState s = new PlayerState();
            s.balance = 42;
            return s;
        }

        @Handles(Cover.class)
        public Notification handle(Cover cmd, PlayerState state, long seq) {
            return Notification.newBuilder()
                    .setCover(Cover.newBuilder().setDomain("starting=" + state.balance).build())
                    .build();
        }
    }

    // --- Helpers ---
    private static ContextualCommand buildCmd(String domain, Cover commandBody) {
        return buildCmd(domain, commandBody, null);
    }

    private static ContextualCommand buildCmd(String domain, Cover commandBody, EventBook prior) {
        Any packed =
                Any.newBuilder()
                        .setTypeUrl("type.googleapis.com/angzarr.Cover")
                        .setValue(commandBody.toByteString())
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

    private static EventBook priorWithOneCover(String eventDomain) {
        Any packed =
                Any.newBuilder()
                        .setTypeUrl("type.googleapis.com/angzarr.Cover")
                        .setValue(Cover.newBuilder().setDomain(eventDomain).build().toByteString())
                        .build();
        EventPage page =
                EventPage.newBuilder()
                        .setHeader(PageHeader.newBuilder().setSequence(0).build())
                        .setEvent(packed)
                        .build();
        return EventBook.newBuilder()
                .setCover(Cover.newBuilder().setDomain("balance").build())
                .addPages(page)
                .setNextSequence(1)
                .build();
    }

    // --- R6 ---

    @Test
    void singleHandlerDispatchReturnsEmittedEvent() throws Exception {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg").withHandler(Player.class, Player::new).build();
        ContextualCommand cmd = buildCmd("player", Cover.newBuilder().setDomain("p1").build());

        BusinessResponse resp = router.dispatch(cmd);

        assertThat(resp.getEvents().getPagesCount()).isEqualTo(1);
        Any emitted = resp.getEvents().getPages(0).getEvent();
        assertThat(emitted.getTypeUrl()).endsWith("angzarr.Notification");
        Notification decoded = Notification.parseFrom(emitted.getValue());
        assertThat(decoded.getCover().getDomain()).isEqualTo("p1");
    }

    @Test
    void unknownTypeUrlThrowsInvalidArgument() {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg").withHandler(Player.class, Player::new).build();
        Any unknown =
                Any.newBuilder().setTypeUrl("type.googleapis.com/nope.Foo").setValue(com.google.protobuf.ByteString.EMPTY).build();
        CommandBook book =
                CommandBook.newBuilder()
                        .setCover(Cover.newBuilder().setDomain("player").build())
                        .addPages(CommandPage.newBuilder().setCommand(unknown).build())
                        .build();
        ContextualCommand cmd = ContextualCommand.newBuilder().setCommand(book).build();

        assertThatThrownBy(() -> router.dispatch(cmd))
                .isInstanceOf(DispatchException.class)
                .satisfies(
                        e ->
                                assertThat(((DispatchException) e).code())
                                        .isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    void factoryInvokedOnlyOnMatch() {
        Player.FACTORY_CALLS.set(0);
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg")
                                .withHandler(
                                        Player.class,
                                        () -> {
                                            Player.FACTORY_CALLS.incrementAndGet();
                                            return new Player();
                                        })
                                .build();
        // No dispatch yet — factory should not have been invoked.
        assertThat(Player.FACTORY_CALLS).hasValue(0);
        router.dispatch(buildCmd("player", Cover.newBuilder().setDomain("p1").build()));
        assertThat(Player.FACTORY_CALLS).hasValue(1);
    }

    @Test
    void noHandlerForDomainThrowsInvalidArgument() {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg").withHandler(Player.class, Player::new).build();
        // Same command type, wrong domain.
        ContextualCommand cmd =
                buildCmd("somewhere-else", Cover.newBuilder().setDomain("p1").build());
        assertThatThrownBy(() -> router.dispatch(cmd))
                .isInstanceOf(DispatchException.class)
                .hasMessageContaining("somewhere-else");
    }

    // --- R7 ---

    @Test
    void priorEventsReplayedThroughAppliesBeforeHandles() throws Exception {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg")
                                .withHandler(BalancePlayer.class, BalancePlayer::new)
                                .build();
        EventBook prior = priorWithOneCover("abc");
        ContextualCommand cmd =
                buildCmd("balance", Cover.newBuilder().setDomain("do-it").build(), prior);

        BusinessResponse resp = router.dispatch(cmd);

        Any emitted = resp.getEvents().getPages(0).getEvent();
        Notification decoded = Notification.parseFrom(emitted.getValue());
        assertThat(decoded.getCover().getDomain()).isEqualTo("seen=1");
    }

    @Test
    void stateFactoryOverridesDefaultConstruction() throws Exception {
        CommandHandlerRouter<?> router =
                (CommandHandlerRouter<?>)
                        Router.newBuilder("agg")
                                .withHandler(CustomStatePlayer.class, CustomStatePlayer::new)
                                .build();
        ContextualCommand cmd = buildCmd("custom", Cover.newBuilder().setDomain("go").build());

        BusinessResponse resp = router.dispatch(cmd);

        Notification decoded = Notification.parseFrom(resp.getEvents().getPages(0).getEvent().getValue());
        assertThat(decoded.getCover().getDomain()).isEqualTo("starting=42");
    }
}
