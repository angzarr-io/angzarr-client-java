package dev.angzarr.client;

import com.google.protobuf.Timestamp;
import dev.angzarr.Cover;
import dev.angzarr.Edition;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.Query;
import dev.angzarr.SequenceRange;
import dev.angzarr.TemporalQuery;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Fluent builder for constructing and executing event queries.
 *
 * <p>QueryBuilder supports multiple access patterns:
 *
 * <ul>
 *   <li>By root - fetch all events for a specific aggregate
 *   <li>By correlation ID - fetch events across aggregates in a workflow
 *   <li>By sequence range - fetch specific event windows for pagination
 *   <li>By temporal point - reconstruct historical state (as-of queries)
 *   <li>By edition - query from specific schema versions
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>{@code
 * EventBook events = client.query("orders", orderId)
 *     .range(10)
 *     .getEventBook();
 *
 * // Or temporal query
 * EventBook historical = client.query("orders", orderId)
 *     .asOfSequence(42)
 *     .getEventBook();
 * }</pre>
 */
public class QueryBuilder {

  private final QueryClient client;
  private final String domain;
  private UUID root;
  private String correlationId;
  // Single selection slot (P2.2 / finding #23): chained range/asOfSequence/
  // asOfTime are last-wins. Mirrors Rust's `selection: Option<Selection>`
  // and Python's `_range` / `_temporal` mutual exclusion.
  private SequenceRange rangeSelect;
  private TemporalQuery temporal;
  private String edition;

  /**
   * Create a query builder for a specific aggregate.
   *
   * @param client The query client to use
   * @param domain The aggregate domain
   * @param root The aggregate root UUID
   */
  public QueryBuilder(QueryClient client, String domain, UUID root) {
    this.client = client;
    this.domain = domain;
    this.root = root;
  }

  /**
   * Create a query builder by domain only (use with byCorrelationId).
   *
   * @param client The query client to use
   * @param domain The aggregate domain
   */
  public QueryBuilder(QueryClient client, String domain) {
    this.client = client;
    this.domain = domain;
    this.root = null;
  }

  /**
   * Query by correlation ID instead of root.
   *
   * <p>Correlation IDs link events across aggregates in a distributed workflow.
   *
   * @param id The correlation ID
   * @return This builder for chaining
   */
  public QueryBuilder byCorrelationId(String id) {
    this.correlationId = id;
    this.root = null;
    return this;
  }

  /**
   * Query events from a specific edition.
   *
   * <p>After upcasting (event schema migration), events exist in multiple editions.
   *
   * @param edition The edition name
   * @return This builder for chaining
   */
  public QueryBuilder withEdition(String edition) {
    this.edition = edition;
    return this;
  }

  /**
   * Query a range of sequences from lower (inclusive).
   *
   * <p>Use for incremental sync: "give me events since sequence N"
   *
   * @param lower The lower bound (inclusive)
   * @return This builder for chaining
   */
  public QueryBuilder range(int lower) {
    // Last-selection-wins: clear any previously-set temporal selection.
    // Mirrors Python's `self._temporal = None` after setting `_range`
    // (finding #23 / PARITY_AUDIT.md P2.2).
    this.rangeSelect = SequenceRange.newBuilder().setLower(lower).build();
    this.temporal = null;
    return this;
  }

  /**
   * Query a range of sequences with upper bound (inclusive).
   *
   * <p>Use for pagination: fetch events 100-200, then 200-300.
   *
   * @param lower The lower bound (inclusive)
   * @param upper The upper bound (inclusive)
   * @return This builder for chaining
   */
  public QueryBuilder rangeTo(int lower, int upper) {
    // Last-selection-wins. Audit #27: `upper` is INCLUSIVE — matches
    // Python `range_to` and Rust `range_to`.
    this.rangeSelect = SequenceRange.newBuilder().setLower(lower).setUpper(upper).build();
    this.temporal = null;
    return this;
  }

  /**
   * Query state as of a specific sequence number.
   *
   * <p>Essential for debugging: "What was the state when this bug occurred?"
   *
   * @param seq The sequence number
   * @return This builder for chaining
   */
  public QueryBuilder asOfSequence(int seq) {
    // Last-selection-wins (finding #23).
    this.temporal = TemporalQuery.newBuilder().setAsOfSequence(seq).build();
    this.rangeSelect = null;
    return this;
  }

  /**
   * Query state as of a specific timestamp (RFC3339 format).
   *
   * <p>Example: "2024-01-15T10:30:00Z"
   *
   * @param rfc3339 The timestamp in RFC3339 format
   * @return This builder for chaining
   */
  public QueryBuilder asOfTime(String rfc3339) {
    // Audit #34 / Python `6cd60b9`: raise synchronously, no deferred
    // sticky `_err` field. Mirrors Rust's
    // `as_of_time(...) -> Result<Self>` short-circuit at the call site.
    Instant instant;
    try {
      instant = Instant.parse(rfc3339);
    } catch (DateTimeParseException e) {
      throw new Errors.InvalidTimestampError(
          dev.angzarr.client.error_codes.Messages.TIMESTAMP_PARSE_FAILED,
          e,
          java.util.Map.of(dev.angzarr.client.error_codes.Keys.INPUT, rfc3339));
    }
    Timestamp ts =
        Timestamp.newBuilder()
            .setSeconds(instant.getEpochSecond())
            .setNanos(instant.getNano())
            .build();
    this.temporal = TemporalQuery.newBuilder().setAsOfTime(ts).build();
    this.rangeSelect = null;
    return this;
  }

  /**
   * Build the Query without executing.
   *
   * <p>Audit #34: no deferred error path — bad input on {@link #asOfTime} raises synchronously at
   * the call site.
   *
   * @return the constructed Query
   */
  public Query build() {
    Cover.Builder coverBuilder = Cover.newBuilder().setDomain(domain);

    if (correlationId != null && !correlationId.isEmpty()) {
      coverBuilder.setCorrelationId(correlationId);
    }
    if (root != null) {
      coverBuilder.setRoot(Helpers.uuidToProto(root));
    }
    if (edition != null && !edition.isEmpty()) {
      coverBuilder.setEdition(Edition.newBuilder().setName(edition).build());
    }

    Query.Builder queryBuilder = Query.newBuilder().setCover(coverBuilder.build());

    if (rangeSelect != null) {
      queryBuilder.setRange(rangeSelect);
    } else if (temporal != null) {
      queryBuilder.setTemporal(temporal);
    }

    return queryBuilder.build();
  }

  /**
   * Execute the query and return a single EventBook.
   *
   * @return The EventBook containing matching events
   * @throws Errors.GrpcError if the gRPC call fails
   */
  public EventBook getEventBook() {
    Query query = build();
    return client.getEventBook(query);
  }

  /**
   * Execute the query and return all matching EventBooks.
   *
   * @return List of EventBooks
   * @throws Errors.GrpcError if the gRPC call fails
   */
  public List<EventBook> getEvents() {
    Query query = build();
    return client.getEvents(query);
  }

  /**
   * Execute the query and return just the event pages.
   *
   * <p>Convenience method when you only need events, not metadata.
   *
   * @return List of EventPages
   * @throws Errors.GrpcError if the gRPC call fails
   */
  public List<EventPage> getPages() {
    EventBook book = getEventBook();
    return book.getPagesList();
  }
}
