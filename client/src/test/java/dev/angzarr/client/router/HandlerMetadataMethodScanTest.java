package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.Message;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Applies;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.Rejected;
import dev.angzarr.client.annotations.StateFactory;
import dev.angzarr.Cover;
import dev.angzarr.EventBook;
import dev.angzarr.Notification;
import org.junit.jupiter.api.Test;

/** R2 — method annotations and reflection scan, including MethodHandle caching. */
class HandlerMetadataMethodScanTest {

    // --- State type ---
    static class PlayerState {
        int balance = 0;
    }

    // --- Handler with @Handles + @Applies + @Rejected + @StateFactory ---
    @Aggregate(domain = "player", state = PlayerState.class)
    static class FullyAnnotatedPlayer {

        @StateFactory
        public PlayerState empty() {
            return new PlayerState();
        }

        @Applies(Cover.class)
        public void applyCover(PlayerState state, Cover evt) {
            state.balance += 1;
        }

        @Handles(Cover.class)
        public Cover handleCover(Cover cmd, PlayerState state, long seq) {
            return cmd;
        }

        @Rejected(domain = "payment", command = "ProcessPayment")
        public Object onPaymentRejected(Notification notif, PlayerState state) {
            return null;
        }
    }

    // --- Handler with multiple @Handles methods ---
    @Aggregate(domain = "multi", state = PlayerState.class)
    static class MultipleHandles {
        @Handles(Cover.class)
        public Cover first(Cover cmd, PlayerState state, long seq) {
            return cmd;
        }

        @Handles(EventBook.class)
        public EventBook second(EventBook cmd, PlayerState state, long seq) {
            return cmd;
        }
    }

    // --- Handler with conflicting method-role annotations ---
    @Aggregate(domain = "bad", state = PlayerState.class)
    static class ConflictingMethodRoles {
        @Handles(Cover.class)
        @Applies(Cover.class)
        public void both(Object a, Object b, long seq) {}
    }

    // --- Handler with two @StateFactory methods ---
    @Aggregate(domain = "dup", state = PlayerState.class)
    static class DuplicateStateFactory {
        @StateFactory
        public PlayerState one() {
            return new PlayerState();
        }

        @StateFactory
        public PlayerState two() {
            return new PlayerState();
        }
    }

    // --- Handles ---
    @Test
    void handlesMapContainsMessageClass() {
        HandlerMetadata md = HandlerMetadata.of(FullyAnnotatedPlayer.class);
        assertThat(md.handles()).containsKey(Cover.class);
    }

    @Test
    void handlesMapWithMultipleMethods() {
        HandlerMetadata md = HandlerMetadata.of(MultipleHandles.class);
        assertThat(md.handles().keySet()).contains(Cover.class, EventBook.class);
    }

    @Test
    void handlesMethodHandleIsInvocable() throws Throwable {
        HandlerMetadata md = HandlerMetadata.of(FullyAnnotatedPlayer.class);
        FullyAnnotatedPlayer instance = new FullyAnnotatedPlayer();
        Cover input = Cover.newBuilder().setDomain("x").build();
        Object result =
                md.handles().get(Cover.class).invoke(instance, input, new PlayerState(), 0L);
        assertThat(result).isInstanceOf(Cover.class);
        assertThat(((Cover) result).getDomain()).isEqualTo("x");
    }

    // --- Applies ---
    @Test
    void appliesMapContainsEventClass() {
        HandlerMetadata md = HandlerMetadata.of(FullyAnnotatedPlayer.class);
        assertThat(md.applies()).containsKey(Cover.class);
    }

    @Test
    void appliesMethodMutatesState() throws Throwable {
        HandlerMetadata md = HandlerMetadata.of(FullyAnnotatedPlayer.class);
        FullyAnnotatedPlayer instance = new FullyAnnotatedPlayer();
        PlayerState state = new PlayerState();
        md.applies().get(Cover.class).invoke(instance, state, Cover.getDefaultInstance());
        assertThat(state.balance).isEqualTo(1);
    }

    // --- Rejected ---
    @Test
    void rejectedMapKeyedByDomainAndCommand() {
        HandlerMetadata md = HandlerMetadata.of(FullyAnnotatedPlayer.class);
        assertThat(md.rejected())
                .containsKey(new HandlerMetadata.RejectedKey("payment", "ProcessPayment"));
    }

    // --- StateFactory ---
    @Test
    void stateFactoryIsFound() {
        HandlerMetadata md = HandlerMetadata.of(FullyAnnotatedPlayer.class);
        assertThat(md.stateFactory()).isPresent();
    }

    @Test
    void stateFactoryInvokesProducesInstance() throws Throwable {
        HandlerMetadata md = HandlerMetadata.of(FullyAnnotatedPlayer.class);
        FullyAnnotatedPlayer instance = new FullyAnnotatedPlayer();
        Object state = md.stateFactory().orElseThrow().invoke(instance);
        assertThat(state).isInstanceOf(PlayerState.class);
    }

    @Test
    void absentStateFactoryYieldsEmptyOptional() {
        HandlerMetadata md = HandlerMetadata.of(MultipleHandles.class);
        assertThat(md.stateFactory()).isEmpty();
    }

    // --- Conflicts ---
    @Test
    void methodWithConflictingRolesThrows() {
        assertThatThrownBy(() -> HandlerMetadata.of(ConflictingMethodRoles.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both")
                .hasMessageContaining("@Handles")
                .hasMessageContaining("@Applies");
    }

    @Test
    void duplicateStateFactoryThrows() {
        assertThatThrownBy(() -> HandlerMetadata.of(DuplicateStateFactory.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@StateFactory");
    }

    // Simulate a real Message type reference so compiler doesn't warn about unused imports
    @SuppressWarnings("unused")
    private static Class<? extends Message> unusedRef() {
        return Cover.class;
    }
}
