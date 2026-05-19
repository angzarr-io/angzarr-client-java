package dev.angzarr.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import dev.angzarr.CommandResponse;
import dev.angzarr.Cover;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import io.grpc.Metadata;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spec convergence tests for {@link Helpers}.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>MED-2.3 — {@code cacheKey(book)} accessor.
 *   <li>MED-2.4 — {@code routingKey(book)} accessor.
 *   <li>MED-2.5 — {@code domain(book)} returns {@code UNKNOWN_DOMAIN} fallback.
 *   <li>MED-2.10 — {@code correlatedMetadata(corrId)} helper.
 *   <li>MED-3.11 — {@code rootFromCover}, {@code eventsFromResponse}, {@code decodeEvent}
 *       free-function helpers.
 * </ul>
 */
class HelpersSpecConvergenceTest {

  // ---- MED-2.5: domain(book) should return UNKNOWN_DOMAIN fallback ----

  @Test
  void domainReturnsUnknownDomainWhenCoverMissing() {
    EventBook book = EventBook.newBuilder().build();
    assertThat(Helpers.domain(book)).isEqualTo(Helpers.UNKNOWN_DOMAIN);
  }

  @Test
  void domainReturnsUnknownDomainWhenCoverDomainEmpty() {
    EventBook book = EventBook.newBuilder().setCover(Cover.newBuilder().build()).build();
    assertThat(Helpers.domain(book)).isEqualTo(Helpers.UNKNOWN_DOMAIN);
  }

  @Test
  void domainReturnsCoverDomainWhenSet() {
    EventBook book =
        EventBook.newBuilder().setCover(Cover.newBuilder().setDomain("orders").build()).build();
    assertThat(Helpers.domain(book)).isEqualTo("orders");
  }

  @Test
  void domainOrStillRespectsExplicitFallback() {
    // domainOr unchanged: explicit fallback wins, not UNKNOWN_DOMAIN.
    EventBook book = EventBook.newBuilder().build();
    assertThat(Helpers.domainOr(book, "my-default")).isEqualTo("my-default");
  }

  // ---- MED-2.4: routingKey(book) accessor ----

  @Test
  void routingKeyEqualsDomain() {
    EventBook book =
        EventBook.newBuilder().setCover(Cover.newBuilder().setDomain("inventory").build()).build();
    assertThat(Helpers.routingKey(book)).isEqualTo("inventory");
  }

  @Test
  void routingKeyReturnsUnknownDomainFallback() {
    EventBook book = EventBook.newBuilder().build();
    assertThat(Helpers.routingKey(book)).isEqualTo(Helpers.UNKNOWN_DOMAIN);
  }

  // ---- MED-2.3: cacheKey(book) accessor ----

  @Test
  void cacheKeyFormat() {
    // Format: "{edition}:{domain}:{root_hex}" per Rust proto_ext::cover.
    UUID root = Identity.computeRoot("player", "alice@x.com");
    EventBook book =
        EventBook.newBuilder()
            .setCover(
                Cover.newBuilder().setDomain("player").setRoot(Helpers.uuidToProto(root)).build())
            .build();
    String rootHex = Helpers.rootIdHex(book);
    assertThat(Helpers.cacheKey(book)).isEqualTo(":player:" + rootHex);
  }

  @Test
  void cacheKeyIncludesEditionName() {
    UUID root = UUID.randomUUID();
    EventBook book =
        EventBook.newBuilder()
            .setCover(
                Cover.newBuilder()
                    .setDomain("orders")
                    .setRoot(Helpers.uuidToProto(root))
                    .setEdition(dev.angzarr.Edition.newBuilder().setName("shadow").build())
                    .build())
            .build();
    String rootHex = Helpers.rootIdHex(book);
    assertThat(Helpers.cacheKey(book)).isEqualTo("shadow:orders:" + rootHex);
  }

  @Test
  void cacheKeyEmptyRootProducesTrailingColon() {
    EventBook book =
        EventBook.newBuilder().setCover(Cover.newBuilder().setDomain("orders").build()).build();
    assertThat(Helpers.cacheKey(book)).isEqualTo(":orders:");
  }

  // ---- MED-2.10: correlatedMetadata helper ----

  @Test
  void correlatedMetadataAttachesHeader() {
    Metadata md = Helpers.correlatedMetadata("corr-123");
    Metadata.Key<String> key =
        Metadata.Key.of(Helpers.CORRELATION_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER);
    assertThat(md.get(key)).isEqualTo("corr-123");
  }

  @Test
  void correlatedMetadataEmptyCorrelationIdProducesEmptyMetadata() {
    Metadata md = Helpers.correlatedMetadata("");
    Metadata.Key<String> key =
        Metadata.Key.of(Helpers.CORRELATION_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER);
    assertThat(md.get(key)).isNull();
  }

  @Test
  void correlatedMetadataNullCorrelationIdProducesEmptyMetadata() {
    Metadata md = Helpers.correlatedMetadata(null);
    Metadata.Key<String> key =
        Metadata.Key.of(Helpers.CORRELATION_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER);
    assertThat(md.get(key)).isNull();
  }

  // ---- MED-3.11: rootFromCover free function ----

  @Test
  void rootFromCoverReturnsOptionalUuidWhenSet() {
    UUID expected = UUID.randomUUID();
    Cover cover = Cover.newBuilder().setRoot(Helpers.uuidToProto(expected)).build();
    Optional<UUID> result = Helpers.rootFromCover(cover);
    assertThat(result).isPresent().contains(expected);
  }

  @Test
  void rootFromCoverReturnsEmptyWhenMissing() {
    Cover cover = Cover.newBuilder().setDomain("orders").build();
    assertThat(Helpers.rootFromCover(cover)).isEmpty();
  }

  @Test
  void rootFromCoverReturnsEmptyWhenNullCover() {
    assertThat(Helpers.rootFromCover(null)).isEmpty();
  }

  // ---- MED-3.11: eventsFromResponse free function ----

  @Test
  void eventsFromResponseReturnsPagesList() {
    EventPage page1 = EventPage.newBuilder().build();
    EventPage page2 = EventPage.newBuilder().build();
    EventBook eb = EventBook.newBuilder().addPages(page1).addPages(page2).build();
    CommandResponse resp = CommandResponse.newBuilder().setEvents(eb).build();
    List<EventPage> pages = Helpers.eventsFromResponse(resp);
    assertThat(pages).hasSize(2);
  }

  @Test
  void eventsFromResponseReturnsEmptyWhenNoEvents() {
    CommandResponse resp = CommandResponse.newBuilder().build();
    assertThat(Helpers.eventsFromResponse(resp)).isEmpty();
  }

  @Test
  void eventsFromResponseReturnsEmptyWhenNullResponse() {
    assertThat(Helpers.eventsFromResponse(null)).isEmpty();
  }

  // ---- MED-3.11: decodeEvent free function (exact type match) ----

  @Test
  void decodeEventReturnsMessageWhenTypeMatches() {
    Cover payload = Cover.newBuilder().setDomain("orders").build();
    Any any =
        Any.newBuilder()
            .setTypeUrl("type.googleapis.com/angzarr_client.proto.angzarr.Cover")
            .setValue(payload.toByteString())
            .build();
    EventPage page = EventPage.newBuilder().setEvent(any).build();
    Optional<Cover> decoded =
        Helpers.decodeEvent(page, "angzarr_client.proto.angzarr.Cover", Cover.class);
    assertThat(decoded).isPresent();
    assertThat(decoded.get().getDomain()).isEqualTo("orders");
  }

  @Test
  void decodeEventReturnsEmptyWhenTypeMismatched() {
    Cover payload = Cover.newBuilder().setDomain("orders").build();
    Any any =
        Any.newBuilder()
            .setTypeUrl("type.googleapis.com/some.Other.Type")
            .setValue(payload.toByteString())
            .build();
    EventPage page = EventPage.newBuilder().setEvent(any).build();
    Optional<Cover> decoded =
        Helpers.decodeEvent(page, "angzarr_client.proto.angzarr.Cover", Cover.class);
    assertThat(decoded).isEmpty();
  }

  @Test
  void decodeEventReturnsEmptyWhenNoEventPayload() {
    EventPage page = EventPage.newBuilder().build();
    Optional<Cover> decoded =
        Helpers.decodeEvent(page, "angzarr_client.proto.angzarr.Cover", Cover.class);
    assertThat(decoded).isEmpty();
  }

  @Test
  void decodeEventReturnsEmptyOnNullInput() {
    assertThat(Helpers.decodeEvent(null, "x", Cover.class)).isEmpty();
  }

  @Test
  void decodeEventExactTypeMatchRejectsSuffix() {
    // Exact match — a hostile `legacy.Cover` must not decode as `Cover`.
    Cover payload = Cover.newBuilder().setDomain("orders").build();
    Any any =
        Any.newBuilder()
            .setTypeUrl("type.googleapis.com/legacy.Cover") // suffix match would falsely accept
            .setValue(payload.toByteString())
            .build();
    EventPage page = EventPage.newBuilder().setEvent(any).build();
    Optional<Cover> decoded =
        Helpers.decodeEvent(page, "angzarr_client.proto.angzarr.Cover", Cover.class);
    assertThat(decoded).isEmpty();
  }
}
