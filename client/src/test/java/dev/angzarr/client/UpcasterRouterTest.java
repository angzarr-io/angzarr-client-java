package dev.angzarr.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import dev.angzarr.EventPage;
import dev.angzarr.PageHeader;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** R16 — UpcasterRouter API parity with client-rust's canonical implementation. */
class UpcasterRouterTest {

    private static Any anyWith(String typeUrl, byte[] bytes) {
        return Any.newBuilder().setTypeUrl(typeUrl).setValue(ByteString.copyFrom(bytes)).build();
    }

    private static EventPage pageOf(Any event, int seq) {
        return EventPage.newBuilder()
                .setHeader(PageHeader.newBuilder().setSequence(seq).build())
                .setEvent(event)
                .build();
    }

    @Test
    void newRouterHasNoEventTypes() {
        UpcasterRouter router = new UpcasterRouter("order");
        assertThat(router.domain()).isEqualTo("order");
        assertThat(router.eventTypes()).isEmpty();
    }

    @Test
    void registrationListsEventTypesAndHandles() {
        UpcasterRouter router =
                new UpcasterRouter("order")
                        .on("OrderCreatedV1", e -> e)
                        .on("OrderShippedV1", e -> e);
        assertThat(router.eventTypes()).containsExactly("OrderCreatedV1", "OrderShippedV1");
        assertThat(router.handles("type.googleapis.com/examples.OrderCreatedV1")).isTrue();
        assertThat(router.handles("type.googleapis.com/examples.OrderShippedV1")).isTrue();
        assertThat(router.handles("type.googleapis.com/examples.OrderCompleted")).isFalse();
    }

    @Test
    void passthroughWhenNoHandlerMatches() {
        UpcasterRouter router = new UpcasterRouter("order").on("OrderCreatedV1", e -> e);
        Any event = anyWith("type.googleapis.com/examples.OrderCompleted", new byte[] {1, 2, 3});
        Any result = router.upcastEvent(event);
        assertThat(result).isSameAs(event);
    }

    @Test
    void transformsMatchingEvent() {
        UpcasterRouter router =
                new UpcasterRouter("order")
                        .on(
                                "OrderCreatedV1",
                                old ->
                                        anyWith(
                                                "type.googleapis.com/examples.OrderCreated",
                                                new byte[] {4, 5, 6}));
        Any event = anyWith("type.googleapis.com/examples.OrderCreatedV1", new byte[] {1, 2, 3});
        Any result = router.upcastEvent(event);
        assertThat(result.getTypeUrl()).isEqualTo("type.googleapis.com/examples.OrderCreated");
        assertThat(result.getValue().toByteArray()).containsExactly(4, 5, 6);
    }

    @Test
    void upcastBatchTransformsMatchingAndPassesThroughOthers() {
        UpcasterRouter router =
                new UpcasterRouter("order")
                        .on(
                                "OrderCreatedV1",
                                old ->
                                        anyWith(
                                                "type.googleapis.com/examples.OrderCreated",
                                                new byte[] {}));

        EventPage v1 =
                pageOf(anyWith("type.googleapis.com/examples.OrderCreatedV1", new byte[] {}), 0);
        EventPage completed =
                pageOf(anyWith("type.googleapis.com/examples.OrderCompleted", new byte[] {}), 1);

        List<EventPage> result = router.upcast(List.of(v1, completed));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEvent().getTypeUrl())
                .isEqualTo("type.googleapis.com/examples.OrderCreated");
        assertThat(result.get(1).getEvent().getTypeUrl())
                .isEqualTo("type.googleapis.com/examples.OrderCompleted");
        // Non-transformed pages are returned as-is (identity-preserving).
        assertThat(result.get(1)).isSameAs(completed);
    }

    @Test
    void onWithInvokesFactoryPerEvent() {
        AtomicInteger factoryCalls = new AtomicInteger();
        UpcasterRouter router =
                new UpcasterRouter("order")
                        .onWith(
                                "OrderCreatedV1",
                                () -> {
                                    factoryCalls.incrementAndGet();
                                    return old ->
                                            anyWith("type.googleapis.com/examples.OrderCreated", new byte[] {});
                                });

        router.upcastEvent(anyWith("type.googleapis.com/examples.OrderCreatedV1", new byte[] {}));
        router.upcastEvent(anyWith("type.googleapis.com/examples.OrderCreatedV1", new byte[] {}));

        assertThat(factoryCalls).hasValue(2);
    }

    @Test
    void upcastPreservesHeaderAndCreatedAt() {
        UpcasterRouter router =
                new UpcasterRouter("order")
                        .on(
                                "OrderCreatedV1",
                                old ->
                                        anyWith(
                                                "type.googleapis.com/examples.OrderCreated",
                                                new byte[] {}));
        com.google.protobuf.Timestamp ts =
                com.google.protobuf.Timestamp.newBuilder().setSeconds(123).build();
        EventPage page =
                EventPage.newBuilder()
                        .setHeader(PageHeader.newBuilder().setSequence(42).build())
                        .setEvent(
                                anyWith("type.googleapis.com/examples.OrderCreatedV1", new byte[] {}))
                        .setCreatedAt(ts)
                        .build();

        EventPage transformed = router.upcast(List.of(page)).get(0);
        assertThat(transformed.getHeader().getSequence()).isEqualTo(42);
        assertThat(transformed.getCreatedAt().getSeconds()).isEqualTo(123);
    }
}
