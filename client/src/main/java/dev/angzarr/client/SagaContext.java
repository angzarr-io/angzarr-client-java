package dev.angzarr.client;

import com.google.protobuf.ByteString;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import java.util.Map;

/**
 * Context passed to {@code @Handles} methods on {@code @Saga}-annotated classes.
 *
 * <p>Carries the full source {@link EventBook} that triggered this saga invocation plus a
 * {@link Destinations} view for stamping outbound commands. Mirrors Python's {@code SagaContext}
 * in {@code angzarr_client/saga_context.py}.
 *
 * <p>Saga handlers that only need sequence stamping can use {@link #destinations()} directly;
 * handlers that need the source aggregate's root, correlation id, or trigger event can read
 * {@link #source()} or the convenience accessors.
 */
public final class SagaContext {

    private final EventBook source;
    private final Destinations destinations;

    public SagaContext(EventBook source, Destinations destinations) {
        this.source = source;
        this.destinations = destinations;
    }

    public SagaContext(EventBook source, Map<String, Integer> destinationSequences) {
        this(source, new Destinations(destinationSequences));
    }

    /** The full source EventBook carrying the trigger event and the source aggregate's cover. */
    public EventBook source() {
        return source;
    }

    /** Destinations view for stamping outbound commands. */
    public Destinations destinations() {
        return destinations;
    }

    /** Domain of the source aggregate, or empty string if no cover is present. */
    public String sourceDomain() {
        return source.hasCover() ? source.getCover().getDomain() : "";
    }

    /**
     * Root UUID bytes of the source aggregate, or {@link ByteString#EMPTY} if no root is set.
     * Useful for correlating emitted facts back to the triggering aggregate.
     */
    public ByteString sourceRoot() {
        if (source.hasCover() && source.getCover().hasRoot()) {
            return source.getCover().getRoot().getValue();
        }
        return ByteString.EMPTY;
    }

    /** Last event page in the source book — typically the trigger event. */
    public EventPage trigger() {
        if (source.getPagesCount() == 0) {
            throw new IllegalStateException("source book has no pages");
        }
        return source.getPages(source.getPagesCount() - 1);
    }
}
