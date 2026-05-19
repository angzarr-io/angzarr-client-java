package dev.angzarr.client;

import dev.angzarr.AngzarrDeferredSequence;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.Cover;
import dev.angzarr.PageHeader;
import dev.angzarr.client.error_codes.Codes;
import dev.angzarr.client.error_codes.Keys;
import dev.angzarr.client.error_codes.Messages;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Provides access to destination sequences for command stamping.
 *
 * <p>Sagas and process managers receive destination sequences from the framework based on
 * output_domains configured in the component config.
 *
 * <p>Design Philosophy:
 *
 * <ul>
 *   <li>Sagas/PMs are translators, NOT decision makers
 *   <li>They should NOT rebuild destination state to make business decisions
 *   <li>Business logic belongs in aggregates
 *   <li>Destinations provide only sequences for command stamping
 * </ul>
 *
 * <p>Mirrors Python's {@code angzarr_client/destinations.py::Destinations} and Rust's {@code
 * state::Destinations}.
 */
public class Destinations {

  /**
   * Insertion-order-preserving map (P2.2). Mirrors Python's {@code dict} preserved order and Rust's
   * {@code IndexMap}.
   */
  private final LinkedHashMap<String, Integer> sequences;

  /** Create Destinations from a sequences map. Preserves iteration order. */
  public Destinations(Map<String, Integer> sequences) {
    this.sequences = sequences != null ? new LinkedHashMap<>(sequences) : new LinkedHashMap<>();
  }

  /**
   * Get the next sequence number for a domain.
   *
   * @return the next sequence number, or empty if the domain is not in the sequences map
   */
  public OptionalInt sequenceFor(String domain) {
    Integer seq = sequences.get(domain);
    return seq != null ? OptionalInt.of(seq) : OptionalInt.empty();
  }

  /**
   * Stamp all command pages with the sequence for the given domain.
   *
   * <p>Returns a new CommandBook with all pages stamped with the sequence header. Unlike other
   * language clients which modify in-place, Java protobuf objects are immutable after build, so
   * this returns a rebuilt copy. Callers MUST use the return value.
   *
   * @throws Errors.InvalidArgumentError if no sequence exists for the domain. The exception carries
   *     {@code code=MISSING_DESTINATION_SEQUENCE} and {@code details["domain"]=<domain>} for
   *     cucumber assertions (audit #64).
   */
  public CommandBook stampCommand(CommandBook cmd, String domain) {
    Integer seq = sequences.get(domain);
    if (seq == null) {
      throw new Errors.InvalidArgumentError(
          Messages.MISSING_DESTINATION_SEQUENCE,
          Codes.MISSING_DESTINATION_SEQUENCE,
          Map.of(Keys.DOMAIN, domain));
    }

    CommandBook.Builder builder = cmd.toBuilder().clearPages();
    for (CommandPage page : cmd.getPagesList()) {
      builder.addPages(
          page.toBuilder().setHeader(PageHeader.newBuilder().setSequence(seq).build()).build());
    }
    return builder.build();
  }

  /**
   * Build a {@link PageHeader} carrying an {@link AngzarrDeferredSequence}.
   *
   * <p>Use this on saga-produced commands so the framework can dedupe on {@code (source.root,
   * source_seq, target.root)}. AMQP at-least-once redelivery of the trigger event then becomes a
   * no-op at the destination aggregate's pipeline (cached events returned without re-invoking
   * business logic), instead of relying on a business guard that surfaces as an
   * idempotent-failure-shaped retry storm.
   *
   * <p>Mirrors Rust's {@code Destinations::deferred_header} (audit #31) and Python's {@code
   * Destinations.deferred_header}.
   */
  public static PageHeader deferredHeader(Cover sourceCover, long sourceSeq) {
    // Proto field is uint32 — narrow with overflow check for safety. The
    // value is bounded by aggregate page count in practice, so int32 head
    // room is comfortably above realistic values.
    if (sourceSeq < 0 || sourceSeq > 0xFFFFFFFFL) {
      throw new IllegalArgumentException("sourceSeq out of uint32 range: " + sourceSeq);
    }
    AngzarrDeferredSequence d =
        AngzarrDeferredSequence.newBuilder()
            .setSource(sourceCover)
            .setSourceSeq((int) sourceSeq)
            .build();
    return PageHeader.newBuilder().setAngzarrDeferred(d).build();
  }

  /** Check if a sequence exists for the given domain. */
  public boolean has(String domain) {
    return sequences.containsKey(domain);
  }

  /**
   * Check if a sequence exists for the given domain.
   *
   * <p>Cross-language parity name (Python's {@code has_domain}, Rust's {@code has_domain}). Same
   * semantics as {@link #has}.
   */
  public boolean hasDomain(String domain) {
    return sequences.containsKey(domain);
  }

  /** Get all domain names that have sequences, in insertion order. */
  public Set<String> domains() {
    return Collections.unmodifiableSet(sequences.keySet());
  }
}
