package dev.angzarr.client.compensation;

import dev.angzarr.EventBook;
import dev.angzarr.RevocationResponse;

/**
 * Result from PM compensation helpers.
 *
 * <p>Holds PM events to persist (if any) and framework revocation flags.
 * Matches the Go/Rust/C++/C#/Python PMRevocationResponse type.
 */
public class PMRevocationResponse {

    private final EventBook processEvents;
    private final RevocationResponse revocation;

    public PMRevocationResponse(EventBook processEvents, RevocationResponse revocation) {
        this.processEvents = processEvents;
        this.revocation = revocation;
    }

    /**
     * PM events to persist (may be null when delegating).
     */
    public EventBook getProcessEvents() {
        return processEvents;
    }

    /**
     * Whether process events are present.
     */
    public boolean hasProcessEvents() {
        return processEvents != null;
    }

    /**
     * Framework revocation flags.
     */
    public RevocationResponse getRevocation() {
        return revocation;
    }
}
