package dev.angzarr.client;

import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.PageHeader;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Provides access to destination sequences for command stamping.
 *
 * <p>Sagas and process managers receive destination sequences from the framework
 * based on output_domains configured in the component config.
 *
 * <p>Design Philosophy:
 * <ul>
 *   <li>Sagas/PMs are translators, NOT decision makers</li>
 *   <li>They should NOT rebuild destination state to make business decisions</li>
 *   <li>Business logic belongs in aggregates</li>
 *   <li>Destinations provide only sequences for command stamping</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * public SagaHandlerResponse execute(EventBook source, Any event, Destinations destinations)
 *         throws CommandRejectedError {
 *     CommandBook cmd = buildCommand("fulfillment", createShipment);
 *     CommandBook stamped = destinations.stampCommand(cmd, "fulfillment");
 *     return SagaHandlerResponse.withCommands(List.of(stamped));
 * }
 * }</pre>
 */
public class Destinations {

    private final Map<String, Integer> sequences;

    /**
     * Create Destinations from a sequences map.
     *
     * <p>The sequences map comes from the gRPC request's destination event books,
     * keyed by domain name with the next sequence number as value.
     *
     * @param sequences Map of domain name to next sequence number
     */
    public Destinations(Map<String, Integer> sequences) {
        this.sequences = sequences != null
                ? new HashMap<>(sequences)
                : new HashMap<>();
    }

    /**
     * Get the next sequence number for a domain.
     *
     * @param domain The domain name
     * @return The next sequence number, or empty if the domain is not in the sequences map
     */
    public OptionalInt sequenceFor(String domain) {
        Integer seq = sequences.get(domain);
        return seq != null ? OptionalInt.of(seq) : OptionalInt.empty();
    }

    /**
     * Stamp all command pages with the sequence for the given domain.
     *
     * <p>Returns a new CommandBook with all pages stamped with the sequence header.
     * Unlike other language clients which modify in-place, Java protobuf objects
     * are immutable after build, so this returns a rebuilt copy. Callers MUST
     * use the return value.
     *
     * @param cmd The command book to stamp
     * @param domain The destination domain
     * @return A new CommandBook with stamped page headers
     * @throws IllegalArgumentException if no sequence exists for the domain
     *         (indicates a configuration error - the domain should be listed in output_domains)
     */
    public CommandBook stampCommand(CommandBook cmd, String domain) {
        Integer seq = sequences.get(domain);
        if (seq == null) {
            throw new Errors.InvalidArgumentError(
                    "No sequence for domain '" + domain + "' - check output_domains config");
        }

        CommandBook.Builder builder = cmd.toBuilder().clearPages();
        for (CommandPage page : cmd.getPagesList()) {
            builder.addPages(page.toBuilder()
                    .setHeader(PageHeader.newBuilder()
                            .setSequence(seq)
                            .build())
                    .build());
        }
        return builder.build();
    }

    /**
     * Check if a sequence exists for the given domain.
     *
     * @param domain The domain name
     * @return true if a sequence exists for the domain
     */
    public boolean has(String domain) {
        return sequences.containsKey(domain);
    }

    /**
     * Get all domain names that have sequences.
     *
     * @return Unmodifiable set of domain names
     */
    public Set<String> domains() {
        return Collections.unmodifiableSet(sequences.keySet());
    }
}
