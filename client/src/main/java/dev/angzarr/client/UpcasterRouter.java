package dev.angzarr.client;

import com.google.protobuf.Any;
import dev.angzarr.EventPage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Event version transformer — port of {@code client-rust}'s {@code UpcasterRouter}.
 *
 * <p>Matches old event type_url suffixes and transforms to new versions. Events without a
 * registered transformation pass through unchanged.
 *
 * <p>Example:
 *
 * <pre>{@code
 * UpcasterRouter router = new UpcasterRouter("order")
 *     .on("OrderCreatedV1", old -> {
 *         OrderCreatedV1 v1 = old.unpack(OrderCreatedV1.class);
 *         return Any.pack(OrderCreated.newBuilder()
 *             .setOrderId(v1.getOrderId())
 *             .build());
 *     });
 * List<EventPage> migrated = router.upcast(oldEvents);
 * }</pre>
 *
 * <p>For dependency-injected handlers (fresh closure per event, mirroring Rust's {@code onWith}):
 *
 * <pre>{@code
 * UpcasterRouter router = new UpcasterRouter("order")
 *     .onWith("OrderCreatedV1", () -> {
 *         MigrationContext ctx = MigrationContext.current();
 *         return old -> upcastWithContext(old, ctx);
 *     });
 * }</pre>
 */
public class UpcasterRouter {

    private final String domain;
    private final List<UpcasterEntry> handlers = new ArrayList<>();

    private static final class UpcasterEntry {
        final String suffix;
        final Supplier<Function<Any, Any>> handlerFactory;

        UpcasterEntry(String suffix, Supplier<Function<Any, Any>> handlerFactory) {
            this.suffix = suffix;
            this.handlerFactory = handlerFactory;
        }
    }

    public UpcasterRouter(String domain) {
        this.domain = domain;
    }

    /**
     * Register a handler for an old event type-URL suffix.
     *
     * @param suffix the type-URL suffix to match (e.g. {@code "OrderCreatedV1"})
     * @param handler pure function from old Any → new Any
     * @return this router for fluent chaining
     */
    public UpcasterRouter on(String suffix, Function<Any, Any> handler) {
        handlers.add(new UpcasterEntry(suffix, () -> handler));
        return this;
    }

    /**
     * Register a handler via a per-event factory — mirrors Rust's {@code on_with}. The factory is
     * invoked once per transformed event, enabling fresh closures with injected dependencies.
     */
    public UpcasterRouter onWith(String suffix, Supplier<Function<Any, Any>> handlerFactory) {
        handlers.add(new UpcasterEntry(suffix, handlerFactory));
        return this;
    }

    /** Domain this upcaster handles. */
    public String domain() {
        return domain;
    }

    /** Backwards-compatible alias for {@link #domain()}. */
    public String getDomain() {
        return domain;
    }

    /** Registered type-URL suffixes, in registration order. */
    public List<String> eventTypes() {
        List<String> out = new ArrayList<>(handlers.size());
        for (UpcasterEntry entry : handlers) out.add(entry.suffix);
        return out;
    }

    /** Whether this upcaster has a handler matching {@code typeUrl} (suffix match). */
    public boolean handles(String typeUrl) {
        for (UpcasterEntry entry : handlers) {
            if (typeUrl.endsWith(entry.suffix)) return true;
        }
        return false;
    }

    /**
     * Transform a single event to its current version.
     *
     * <p>Returns the transformed Any if a handler matches, otherwise returns the original Any
     * unchanged.
     */
    public Any upcastEvent(Any event) {
        String typeUrl = event.getTypeUrl();
        for (UpcasterEntry entry : handlers) {
            if (typeUrl.endsWith(entry.suffix)) {
                return entry.handlerFactory.get().apply(event);
            }
        }
        return event;
    }

    /**
     * Transform a list of events to current versions.
     *
     * <p>Events matching registered handlers are transformed; non-matching events pass through
     * unchanged. Non-event payloads (e.g. deferred-sequence markers) pass through untouched.
     */
    public List<EventPage> upcast(List<EventPage> events) {
        List<EventPage> result = new ArrayList<>(events.size());
        for (EventPage page : events) {
            if (!page.hasEvent()) {
                result.add(page);
                continue;
            }
            Any event = page.getEvent();
            Any newEvent = upcastEvent(event);
            if (newEvent == event || newEvent.getTypeUrl().equals(event.getTypeUrl())) {
                result.add(page);
                continue;
            }
            EventPage.Builder nb = EventPage.newBuilder().setEvent(newEvent);
            if (page.hasHeader()) nb.setHeader(page.getHeader());
            if (page.hasCreatedAt()) nb.setCreatedAt(page.getCreatedAt());
            result.add(nb.build());
        }
        return result;
    }
}
