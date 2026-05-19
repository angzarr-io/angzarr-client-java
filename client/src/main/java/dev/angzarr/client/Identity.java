package dev.angzarr.client;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Aggregate identity computation for Angzarr domains.
 *
 * <p>Provides deterministic UUID generation from business keys, ensuring consistent aggregate
 * identification across services. Mirrors the Python {@code angzarr_client.identity} module, the
 * Rust {@code identity} module, and the C# {@code Angzarr.Client.Identity} class byte-for-byte.
 *
 * <p>Byte-equality is pinned by Rust {@code identity.rs:71-75}: {@code compute_root("player",
 * "alice@x.com") = 8cf1fb5d-45ce-58c2-a7e4-34359eb42d7c}.
 *
 * <p>Algorithm: UUID v5 (RFC 4122 / SHA-1) with namespace OID {@code
 * 6ba7b812-9dad-11d1-80b4-00c04fd430c8} and seed string {@code "angzarr" + domain + business_key}.
 */
public final class Identity {

  private Identity() {}

  /**
   * RFC 4122 OID namespace UUID. Matches Python's {@code uuid.NAMESPACE_OID}, Rust's {@code
   * Uuid::NAMESPACE_OID}, and C#'s {@code Identity.NamespaceOid}.
   */
  public static final UUID NAMESPACE_OID = UUID.fromString("6ba7b812-9dad-11d1-80b4-00c04fd430c8");

  /**
   * RFC 4122 DNS namespace UUID. Used by {@link #inventoryProductRoot(String)}. Matches Python's
   * {@code INVENTORY_PRODUCT_NAMESPACE} and Rust's {@code INVENTORY_PRODUCT_NAMESPACE =
   * Uuid::NAMESPACE_DNS}.
   */
  public static final UUID INVENTORY_PRODUCT_NAMESPACE =
      UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

  /**
   * Compute a deterministic root UUID from domain and business key.
   *
   * <p>Mirrors Python's {@code compute_root}: {@code uuid5(NAMESPACE_OID, "angzarr" + domain +
   * business_key)}.
   */
  public static UUID computeRoot(String domain, String businessKey) {
    String seed = "angzarr" + domain + businessKey;
    return uuidV5(NAMESPACE_OID, seed);
  }

  /**
   * Deterministic UUID for an inventory product aggregate. Uses the DNS namespace (matches Python
   * {@code INVENTORY_PRODUCT_NAMESPACE}).
   */
  public static UUID inventoryProductRoot(String productId) {
    return uuidV5(INVENTORY_PRODUCT_NAMESPACE, productId);
  }

  /** Deterministic root UUID for a customer aggregate. */
  public static UUID customerRoot(String email) {
    return computeRoot("customer", email);
  }

  /** Deterministic root UUID for a product aggregate. */
  public static UUID productRoot(String sku) {
    return computeRoot("product", sku);
  }

  /** Deterministic root UUID for an order aggregate. */
  public static UUID orderRoot(String orderId) {
    return computeRoot("order", orderId);
  }

  /** Deterministic root UUID for an inventory aggregate. */
  public static UUID inventoryRoot(String productId) {
    return computeRoot("inventory", productId);
  }

  /** Deterministic root UUID for a cart aggregate. */
  public static UUID cartRoot(String customerId) {
    return computeRoot("cart", customerId);
  }

  /** Deterministic root UUID for a fulfillment aggregate. */
  public static UUID fulfillmentRoot(String orderId) {
    return computeRoot("fulfillment", orderId);
  }

  /**
   * Convert a UUID to its 16-byte big-endian (network-order) proto representation. Matches the wire
   * format produced by {@link Helpers#uuidToProto(UUID)}.
   */
  public static byte[] toProtoBytes(UUID id) {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(id.getMostSignificantBits());
    bb.putLong(id.getLeastSignificantBits());
    return bb.array();
  }

  // --- RFC 4122 v5 helper ---

  private static UUID uuidV5(UUID namespace, String name) {
    MessageDigest sha1;
    try {
      sha1 = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException nsae) {
      // SHA-1 is mandated by JLS — should never happen.
      throw new IllegalStateException("SHA-1 not available", nsae);
    }
    // Namespace bytes are the 16-byte big-endian UUID representation.
    ByteBuffer nsBuf = ByteBuffer.wrap(new byte[16]);
    nsBuf.putLong(namespace.getMostSignificantBits());
    nsBuf.putLong(namespace.getLeastSignificantBits());
    sha1.update(nsBuf.array());
    sha1.update(name.getBytes(StandardCharsets.UTF_8));
    byte[] hash = sha1.digest();

    // Set version (5) and variant (RFC 4122) per RFC 4122 §4.3.
    hash[6] &= 0x0f;
    hash[6] |= 0x50;
    hash[8] &= 0x3f;
    hash[8] |= (byte) 0x80;

    ByteBuffer bb = ByteBuffer.wrap(hash, 0, 16);
    long msb = bb.getLong();
    long lsb = bb.getLong();
    return new UUID(msb, lsb);
  }
}
