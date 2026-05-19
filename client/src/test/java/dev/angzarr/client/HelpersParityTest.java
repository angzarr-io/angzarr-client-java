package dev.angzarr.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import dev.angzarr.AngzarrDeferredSequence;
import dev.angzarr.Cover;
import dev.angzarr.DomainDivergence;
import dev.angzarr.Edition;
import dev.angzarr.EventBook;
import dev.angzarr.UUID;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/**
 * Parity audits for {@link Helpers}:
 *
 * <ul>
 *   <li>#48: {@code bytesToUuidText} dashed-form helper.
 *   <li>#49: {@code divergenceFor} returns Optional, not panic.
 *   <li>#54: {@code domain()} accessor falls back when cover.domain empty.
 *   <li>#55: {@code idempotencyKey} returns Optional instead of panicking.
 *   <li>Python-parity: {@code destinationMap} keyed by root hex.
 * </ul>
 */
class HelpersParityTest {

  private static UUID protoUuid(java.util.UUID u) {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(u.getMostSignificantBits());
    bb.putLong(u.getLeastSignificantBits());
    return UUID.newBuilder().setValue(ByteString.copyFrom(bb.array())).build();
  }

  // ----- #48 bytesToUuidText -----

  @Test
  void bytesToUuidText_canonicalDashedForm_for16Bytes() {
    java.util.UUID original = java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc");
    byte[] raw = new byte[16];
    ByteBuffer.wrap(raw)
        .putLong(original.getMostSignificantBits())
        .putLong(original.getLeastSignificantBits());
    String text = Helpers.bytesToUuidText(raw);
    assertThat(text).isEqualTo(original.toString());
  }

  @Test
  void bytesToUuidText_fallbackHex_forShortInput() {
    byte[] raw = {0x01, 0x02, 0x03};
    assertThat(Helpers.bytesToUuidText(raw)).isEqualTo("010203");
  }

  @Test
  void protoUuidToText_dashedForm() {
    java.util.UUID original = java.util.UUID.fromString("aaaabbbb-cccc-dddd-eeee-ffff00001111");
    UUID proto = protoUuid(original);
    assertThat(Helpers.protoUuidToText(proto)).isEqualTo(original.toString());
  }

  // ----- #49 divergenceFor -----

  @Test
  void divergenceFor_returnsOptional_emptyWhenAbsent() {
    Edition e = Edition.newBuilder().setName("v2").build();
    assertThat(Helpers.divergenceFor(e, "orders")).isEmpty();
  }

  @Test
  void divergenceFor_returnsValueWhenDomainMatches() {
    Edition e =
        Edition.newBuilder()
            .setName("v2")
            .addDivergences(DomainDivergence.newBuilder().setDomain("orders").setSequence(42))
            .build();
    OptionalLong result = Helpers.divergenceFor(e, "orders");
    assertThat(result).hasValue(42L);
  }

  @Test
  void divergenceFor_nullEditionReturnsEmpty() {
    // Cross-language: Python `divergence_for(None, ...)` returns None.
    assertThat(Helpers.divergenceFor(null, "any")).isEmpty();
  }

  // ----- #54 domain() with empty fallback -----

  @Test
  void domain_returnsUnknownWhenCoverHasEmptyDomain() {
    // Audit #54: Cover present but `.domain == ""` falls back to "unknown"
    // — matches Rust `proto_ext::domain()` behavior.
    EventBook book = EventBook.newBuilder().setCover(Cover.newBuilder().build()).build();
    assertThat(Helpers.domainOr(book, Helpers.UNKNOWN_DOMAIN)).isEqualTo(Helpers.UNKNOWN_DOMAIN);
  }

  @Test
  void domain_returnsActualDomainWhenCoverHasIt() {
    EventBook book =
        EventBook.newBuilder().setCover(Cover.newBuilder().setDomain("orders").build()).build();
    assertThat(Helpers.domainOr(book, Helpers.UNKNOWN_DOMAIN)).isEqualTo("orders");
  }

  // ----- #55 idempotencyKey -----

  @Test
  void idempotencyKey_emptyWhenSourceMissing() {
    // Audit #55: malformed wire input is a missing-key signal, not a panic.
    AngzarrDeferredSequence d = AngzarrDeferredSequence.newBuilder().setSourceSeq(5).build();
    assertThat(Helpers.idempotencyKey(d)).isEmpty();
  }

  @Test
  void idempotencyKey_composesEditionDomainRootSeq() {
    java.util.UUID rootJ = java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc");
    Cover source =
        Cover.newBuilder()
            .setDomain("orders")
            .setRoot(protoUuid(rootJ))
            .setEdition(Edition.newBuilder().setName("v2").build())
            .build();
    AngzarrDeferredSequence d =
        AngzarrDeferredSequence.newBuilder().setSource(source).setSourceSeq(7).build();
    Optional<String> key = Helpers.idempotencyKey(d);
    assertThat(key).isPresent();
    // Format: edition:domain:root_hex:seq (hex is the 32-char no-dash UUID).
    assertThat(key.get()).isEqualTo("v2:orders:12345678123412341234123456789abc:7");
  }

  // ----- destinationMap -----

  @Test
  void destinationMap_keysByRootHex_skipsCoverless() {
    java.util.UUID rootJ = java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc");
    EventBook keyed =
        EventBook.newBuilder()
            .setCover(Cover.newBuilder().setDomain("orders").setRoot(protoUuid(rootJ)))
            .build();
    EventBook orphan = EventBook.newBuilder().build();

    Map<String, EventBook> map = Helpers.destinationMap(List.of(keyed, orphan));
    assertThat(map).hasSize(1);
    assertThat(map).containsKey("12345678123412341234123456789abc");
  }
}
