package dev.angzarr.client;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import dev.angzarr.AngzarrDeferredSequence;
import dev.angzarr.CommandResponse;
import dev.angzarr.Cover;
import dev.angzarr.DomainDivergence;
import dev.angzarr.Edition;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.UUID;
import io.grpc.Metadata;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

/**
 * Helper methods for working with Angzarr types.
 *
 * <p>Mirrors Python's {@code angzarr_client/helpers.py} and Rust's {@code proto_ext::*}. Pure
 * utility surface — proto construction, accessor fallbacks, type-URL helpers.
 */
public final class Helpers {

  private Helpers() {}

  // Constants matching Rust proto_ext::constants
  public static final String UNKNOWN_DOMAIN = "unknown";
  public static final String WILDCARD_DOMAIN = "*";
  public static final String DEFAULT_EDITION = "";
  public static final String META_ANGZARR_DOMAIN = "_angzarr";
  public static final String PROJECTION_DOMAIN_PREFIX = "_projection";
  public static final String PROJECTION_TYPE_URL = "angzarr_client.proto.angzarr.v1.Projection";
  public static final String CORRELATION_ID_HEADER = "x-correlation-id";
  public static final String TYPE_URL_PREFIX = "type.googleapis.com/";

  // ----- UUID helpers -----

  /** Convert a java.util.UUID to an Angzarr UUID proto. */
  public static UUID uuidToProto(java.util.UUID uuid) {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return UUID.newBuilder().setValue(ByteString.copyFrom(bb.array())).build();
  }

  /** Convert an Angzarr UUID proto to a java.util.UUID. */
  public static java.util.UUID protoToUuid(UUID uuid) {
    ByteBuffer bb = ByteBuffer.wrap(uuid.getValue().toByteArray());
    long msb = bb.getLong();
    long lsb = bb.getLong();
    return new java.util.UUID(msb, lsb);
  }

  /**
   * Convert raw bytes to a standard UUID text format.
   *
   * <p>16-byte input formats as the canonical 8-4-4-4-12 UUID; other lengths fall back to hex.
   * Audit #48 / Rust {@code proto_ext::to_uuid_text}.
   */
  public static String bytesToUuidText(byte[] raw) {
    if (raw == null) {
      return "";
    }
    if (raw.length == 16) {
      ByteBuffer bb = ByteBuffer.wrap(raw);
      long msb = bb.getLong();
      long lsb = bb.getLong();
      return new java.util.UUID(msb, lsb).toString();
    }
    StringBuilder sb = new StringBuilder(raw.length * 2);
    for (byte b : raw) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  /** Convert a proto UUID to its standard text format (dashed when 16 bytes). */
  public static String protoUuidToText(UUID u) {
    if (u == null) {
      return "";
    }
    return bytesToUuidText(u.getValue().toByteArray());
  }

  // ----- Domain / edition accessors -----

  /**
   * Get the domain from an EventBook's cover, falling back to {@link #UNKNOWN_DOMAIN} when the
   * cover is missing or its {@code domain} field is the empty string.
   *
   * <p>Audit #54 / MED-2.5: tolerant accessor matching Rust {@code proto_ext::cover::domain()} and
   * Python {@code wrappers.domain()}. For an explicit-fallback variant, see {@link
   * #domainOr(EventBook, String)}.
   */
  public static String domain(EventBook book) {
    if (book == null || !book.hasCover()) {
      return UNKNOWN_DOMAIN;
    }
    String d = book.getCover().getDomain();
    return d.isEmpty() ? UNKNOWN_DOMAIN : d;
  }

  /**
   * Get the domain from an EventBook's cover, falling back to {@code fallback} when the cover is
   * missing OR its {@code domain} is the empty string.
   *
   * <p>Audit #54: tolerant accessor matching Rust {@code proto_ext::domain()}.
   */
  public static String domainOr(EventBook book, String fallback) {
    if (book == null || !book.hasCover()) {
      return fallback;
    }
    String d = book.getCover().getDomain();
    return d.isEmpty() ? fallback : d;
  }

  /** Get the correlation ID from an EventBook. */
  public static String correlationId(EventBook book) {
    return book.hasCover() ? book.getCover().getCorrelationId() : "";
  }

  /** Check if an EventBook has a non-empty correlation ID. */
  public static boolean hasCorrelationId(EventBook book) {
    return book.hasCover() && !book.getCover().getCorrelationId().isEmpty();
  }

  /** Get the root UUID from an EventBook, or null. */
  public static UUID rootUuid(EventBook book) {
    return book.hasCover() ? book.getCover().getRoot() : null;
  }

  /** Get the root UUID as hex string from an EventBook (no dashes). */
  public static String rootIdHex(EventBook book) {
    if (!book.hasCover() || !book.getCover().hasRoot()) return "";
    byte[] bytes = book.getCover().getRoot().getValue().toByteArray();
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  /** Get the edition from an EventBook, or null if not set or empty-named. */
  public static Edition edition(EventBook book) {
    if (!book.hasCover() || !book.getCover().hasEdition()) {
      return null;
    }
    Edition e = book.getCover().getEdition();
    return e.getName().isEmpty() ? null : e;
  }

  /**
   * Return the divergence sequence for a domain on an edition, or empty.
   *
   * <p>Audit #49: returns {@link OptionalLong}, not a magic value. Matches Python's {@code
   * divergence_for(...) -> int | None} and Rust's {@code divergence_for(...) -> Option<u64>}.
   */
  public static OptionalLong divergenceFor(Edition e, String domainName) {
    if (e == null) {
      return OptionalLong.empty();
    }
    for (DomainDivergence d : e.getDivergencesList()) {
      if (d.getDomain().equals(domainName)) {
        return OptionalLong.of(d.getSequence());
      }
    }
    return OptionalLong.empty();
  }

  /** Calculate the next sequence number from an EventBook. */
  public static int nextSequence(EventBook book) {
    if (book == null) {
      return 0;
    }
    return (int) book.getNextSequence();
  }

  // ----- Idempotency / destinations -----

  /**
   * Build the composite idempotency key for a saga-produced deferred sequence.
   *
   * <p>Format: {@code "{edition}:{domain}:{root_hex}:{source_seq}"}. Returns empty when the
   * deferred sequence has no source cover — a malformed wire input is a missing-key signal, not an
   * exception. Audit finding #55.
   */
  public static Optional<String> idempotencyKey(AngzarrDeferredSequence deferred) {
    if (deferred == null || !deferred.hasSource()) {
      return Optional.empty();
    }
    Cover source = deferred.getSource();
    String editionName = source.hasEdition() ? source.getEdition().getName() : "";
    String rootHex = "";
    if (source.hasRoot()) {
      byte[] bytes = source.getRoot().getValue().toByteArray();
      StringBuilder sb = new StringBuilder();
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      rootHex = sb.toString();
    }
    return Optional.of(
        editionName + ":" + source.getDomain() + ":" + rootHex + ":" + deferred.getSourceSeq());
  }

  /**
   * Build a map from root UUID hex to EventBook for destination lookup.
   *
   * <p>Used in multi-destination sagas to look up the correct EventBook by aggregate root when
   * setting command sequences. Entries without a root are skipped. Insertion order is preserved.
   *
   * <p>Cross-language alias for Rust's {@code proto_ext::destination_map} / Python's {@code
   * helpers.destination_map}.
   */
  public static Map<String, EventBook> destinationMap(List<EventBook> destinations) {
    Map<String, EventBook> result = new LinkedHashMap<>();
    if (destinations == null) {
      return result;
    }
    for (EventBook dest : destinations) {
      if (!dest.hasCover() || !dest.getCover().hasRoot()) {
        continue;
      }
      byte[] bytes = dest.getCover().getRoot().getValue().toByteArray();
      StringBuilder sb = new StringBuilder();
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      result.put(sb.toString(), dest);
    }
    return result;
  }

  // ----- Type URL helpers -----

  /** Get the type URL for a protobuf message. */
  public static String typeUrl(Message message) {
    return TYPE_URL_PREFIX + message.getDescriptorForType().getFullName();
  }

  /** Get the fully-qualified protobuf type name from a Java proto message class. */
  public static String protoFullName(Class<? extends Message> messageClass) {
    try {
      Message instance =
          (Message) messageClass.getDeclaredMethod("getDefaultInstance").invoke(null);
      return instance.getDescriptorForType().getFullName();
    } catch (Exception e) {
      throw new RuntimeException("Failed to get proto full name for " + messageClass.getName(), e);
    }
  }

  /** Extract the type name from a type URL (part after the last '/'). */
  public static String typeNameFromUrl(String typeUrl) {
    int idx = typeUrl.lastIndexOf('/');
    return idx >= 0 ? typeUrl.substring(idx + 1) : typeUrl;
  }

  /**
   * Check if a type URL matches the given fully-qualified type name.
   *
   * @param typeUrl Full type URL (e.g., "type.googleapis.com/angzarr.Cover")
   * @param typeName Fully-qualified type name (e.g., "angzarr.Cover")
   */
  public static boolean typeUrlMatches(String typeUrl, String typeName) {
    return typeUrl.equals(TYPE_URL_PREFIX + typeName);
  }

  // ----- Timestamps -----

  /** Get the current timestamp as a protobuf Timestamp. */
  public static Timestamp now() {
    Instant now = Instant.now();
    return Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();
  }

  // ----- Any / EventPage packing -----

  /** Pack a protobuf message into an Any with the canonical type.googleapis.com prefix. */
  public static Any packAny(Message message) {
    return Any.pack(message, "type.googleapis.com/");
  }

  /**
   * Pack an event into an EventPage. The {@link Any#getTypeUrl()} is derived from {@code
   * eventMessage}'s descriptor (audit #47).
   */
  public static EventPage packEvent(Message eventMessage) {
    return EventPage.newBuilder().setEvent(packAny(eventMessage)).build();
  }

  /** Pack multiple events into EventPages. */
  public static List<EventPage> packEvents(Message... events) {
    return Arrays.stream(events).map(Helpers::packEvent).collect(Collectors.toList());
  }

  /** Create a new EventBook with the given events. */
  public static EventBook newEventBook(Message... events) {
    return EventBook.newBuilder().addAllPages(packEvents(events)).build();
  }

  /** Create a new EventBook with multiple events. */
  public static EventBook newEventBookMulti(List<Message> events) {
    return EventBook.newBuilder()
        .addAllPages(events.stream().map(Helpers::packEvent).collect(Collectors.toList()))
        .build();
  }

  // ----- Cover-bearer accessors (MED-2.3 / MED-2.4) -----

  /**
   * Compute the bus routing key for an EventBook (currently the domain).
   *
   * <p>Audit #54 / MED-2.4: the routing key is a transport concern used for bus subscription
   * matching. Edition filtering happens at the handler level, not the bus level. Mirrors Python
   * {@code wrappers.routing_key()} and Rust {@code proto_ext::cover::CoverExt::routing_key}.
   */
  public static String routingKey(EventBook book) {
    return domain(book);
  }

  /**
   * Compute the cache key for an EventBook: {@code "{edition}:{domain}:{root_hex}"}.
   *
   * <p>Audit #54 / MED-2.3: used for caching aggregate state during saga retry to avoid redundant
   * fetches. Includes edition to prevent collision between aggregates in different timelines.
   * Mirrors Python {@code wrappers.cache_key()} and Rust {@code
   * proto_ext::cover::CoverExt::cache_key}.
   */
  public static String cacheKey(EventBook book) {
    Edition e = edition(book);
    String editionName = e == null ? "" : e.getName();
    String dom = "";
    if (book != null && book.hasCover()) {
      dom = book.getCover().getDomain();
    }
    String rootHex = book == null ? "" : rootIdHex(book);
    return editionName + ":" + dom + ":" + rootHex;
  }

  // ----- correlated_metadata (MED-2.10) -----

  /**
   * Build a gRPC {@link Metadata} object carrying the {@link #CORRELATION_ID_HEADER
   * x-correlation-id} header.
   *
   * <p>Audit #69 / MED-2.10: an empty or null correlation ID skips the header entirely — never
   * sends a blank value. Mirrors Python's {@code helpers.correlated_metadata}, Rust's {@code
   * correlated_request}, and C++'s {@code helpers::correlated_metadata}.
   */
  public static Metadata correlatedMetadata(String correlationId) {
    Metadata md = new Metadata();
    if (correlationId == null || correlationId.isEmpty()) {
      return md;
    }
    Metadata.Key<String> key =
        Metadata.Key.of(CORRELATION_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER);
    md.put(key, correlationId);
    return md;
  }

  // ----- Free-function helpers (MED-3.11) -----

  /**
   * Extract the root UUID from a {@link Cover}, if any.
   *
   * <p>Mirrors Rust's {@code builder::root_from_cover}: returns {@link Optional#empty()} when the
   * cover has no root field set.
   */
  public static Optional<java.util.UUID> rootFromCover(Cover cover) {
    if (cover == null || !cover.hasRoot()) {
      return Optional.empty();
    }
    return Optional.of(protoToUuid(cover.getRoot()));
  }

  /**
   * Extract the events pages list from a {@link CommandResponse}.
   *
   * <p>Mirrors Rust's {@code builder::events_from_response}: returns an empty list when {@code
   * response} is null or has no events book.
   */
  public static List<EventPage> eventsFromResponse(CommandResponse response) {
    if (response == null || !response.hasEvents()) {
      return Collections.emptyList();
    }
    return response.getEvents().getPagesList();
  }

  /**
   * Decode a page's event payload if its type URL matches the given fully-qualified type name.
   *
   * <p>Audit #25 / MED-3.11: exact type URL match — a hostile {@code legacy.OrderCreated} payload
   * must not decode as {@code orders.OrderCreated}. Returns {@link Optional#empty()} on mismatch,
   * missing payload, or decode failure. Mirrors Rust's {@code builder::decode_event} and Python's
   * {@code helpers.decode_event}.
   */
  public static <M extends Message> Optional<M> decodeEvent(
      EventPage page, String fullTypeName, Class<M> messageClass) {
    if (page == null || !page.hasEvent()) {
      return Optional.empty();
    }
    Any event = page.getEvent();
    if (!event.getTypeUrl().equals(TYPE_URL_PREFIX + fullTypeName)) {
      return Optional.empty();
    }
    try {
      java.lang.reflect.Method parseFrom = messageClass.getMethod("parseFrom", byte[].class);
      @SuppressWarnings("unchecked")
      M msg = (M) parseFrom.invoke(null, event.getValue().toByteArray());
      return Optional.of(msg);
    } catch (java.lang.reflect.InvocationTargetException ite) {
      if (ite.getCause() instanceof InvalidProtocolBufferException) {
        return Optional.empty();
      }
      return Optional.empty();
    } catch (ReflectiveOperationException roe) {
      return Optional.empty();
    }
  }
}
