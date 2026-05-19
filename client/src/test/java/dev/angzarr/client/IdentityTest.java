package dev.angzarr.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * HIGH-2.2 — `Identity` / `compute_root` module presence and byte-equality with Python/Rust/C#.
 *
 * <p>Pins the cross-language equality contract from Rust's `identity.rs:71-75`:
 * `compute_root("player", "alice@x.com") = 8cf1fb5d-45ce-58c2-a7e4-34359eb42d7c`.
 *
 * <p>UUID v5 with namespace OID `6ba7b812-9dad-11d1-80b4-00c04fd430c8` (RFC 4122) and seed
 * `"angzarr" + domain + business_key`.
 */
class IdentityTest {

  @Test
  void computeRootMatchesRustAndPython() {
    assertThat(Identity.computeRoot("player", "alice@x.com").toString())
        .isEqualTo("8cf1fb5d-45ce-58c2-a7e4-34359eb42d7c");
  }

  @Test
  void computeRootIsDeterministic() {
    UUID a = Identity.computeRoot("order", "o-1");
    UUID b = Identity.computeRoot("order", "o-1");
    assertThat(a).isEqualTo(b);
  }

  @Test
  void computeRootVariesByDomain() {
    UUID a = Identity.computeRoot("customer", "x");
    UUID b = Identity.computeRoot("product", "x");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void inventoryProductNamespaceIsDns() {
    // 6ba7b810-9dad-11d1-80b4-00c04fd430c8 — RFC 4122 DNS namespace
    assertThat(Identity.INVENTORY_PRODUCT_NAMESPACE.toString())
        .isEqualTo("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
  }

  @Test
  void namespaceOidIsRfc4122() {
    // 6ba7b812-9dad-11d1-80b4-00c04fd430c8 — RFC 4122 OID namespace
    assertThat(Identity.NAMESPACE_OID.toString()).isEqualTo("6ba7b812-9dad-11d1-80b4-00c04fd430c8");
  }

  @Test
  void inventoryProductRootUsesDnsNamespace() {
    // Should equal uuid5(DNS_NAMESPACE, "sku-1") — distinct from
    // computeRoot("inventory", "sku-1") which uses the OID namespace.
    UUID viaInventoryRoot = Identity.inventoryProductRoot("sku-1");
    UUID viaComputeRoot = Identity.computeRoot("inventory", "sku-1");
    assertThat(viaInventoryRoot).isNotEqualTo(viaComputeRoot);
  }

  @Test
  void domainHelpersDelegateToComputeRoot() {
    assertThat(Identity.customerRoot("e")).isEqualTo(Identity.computeRoot("customer", "e"));
    assertThat(Identity.productRoot("s")).isEqualTo(Identity.computeRoot("product", "s"));
    assertThat(Identity.orderRoot("o")).isEqualTo(Identity.computeRoot("order", "o"));
    assertThat(Identity.inventoryRoot("p")).isEqualTo(Identity.computeRoot("inventory", "p"));
    assertThat(Identity.cartRoot("c")).isEqualTo(Identity.computeRoot("cart", "c"));
    assertThat(Identity.fulfillmentRoot("o")).isEqualTo(Identity.computeRoot("fulfillment", "o"));
  }

  @Test
  void toProtoBytesReturnsSixteen() {
    UUID id = Identity.computeRoot("x", "y");
    byte[] bytes = Identity.toProtoBytes(id);
    assertThat(bytes).hasSize(16);
  }

  @Test
  void toProtoBytesRoundTripsThroughHelpersUuidToProto() {
    // The 16-byte big-endian representation Identity.toProtoBytes returns
    // must match Helpers.uuidToProto(uuid).getValue() byte-for-byte —
    // otherwise cross-language wire payloads diverge.
    UUID id = Identity.computeRoot("player", "alice@x.com");
    byte[] viaIdentity = Identity.toProtoBytes(id);
    byte[] viaHelpers = Helpers.uuidToProto(id).getValue().toByteArray();
    assertThat(viaIdentity).containsExactly(viaHelpers);
  }
}
