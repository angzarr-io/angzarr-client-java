package dev.angzarr.client.error_codes;

/**
 * Detail-map key constants — the keys used in the {@code details} mapping on every error this
 * client constructs. Use these at insertion sites AND when reading {@code details[key]} in tests.
 *
 * <p>Mirrors {@code client-rust/main/src/error_codes.rs::keys} and {@code
 * client-python/main/angzarr_client/error_codes/keys.py} — same constant names, same string values.
 */
public final class Keys {

  private Keys() {}

  public static final String FIELD = "field";
  public static final String CONTEXT = "context";
  public static final String CAUSE = "cause";
  public static final String ENDPOINT = "endpoint";
  public static final String INPUT = "input";
  public static final String EXPECTED = "expected";
  public static final String ACTUAL = "actual";
  public static final String EXPECTED_KIND = "expected_kind";
  public static final String DOMAIN = "domain";
  public static final String TYPE_URL = "type_url";
  public static final String ROUTER_NAME = "router_name";
  public static final String HANDLER_CLASS = "handler_class";
  public static final String HANDLER_KIND = "handler_kind";
  public static final String ACTUAL_RETURN_TYPE = "actual_return_type";
  public static final String ENV_VAR = "env_var";

  /**
   * Audit #72: paired with {@link #HANDLER_KIND} for {@code MIXED_HANDLER_KINDS} errors — {@code
   * details["handler_kind"]} is the first kind found, {@code details["other_kind"]} is the
   * conflicting one.
   */
  public static final String OTHER_KIND = "other_kind";
}
