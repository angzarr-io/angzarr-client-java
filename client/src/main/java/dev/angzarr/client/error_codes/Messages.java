package dev.angzarr.client.error_codes;

/**
 * Static human-readable messages — the value of {@code message} on every error this client
 * constructs. Constant names match the corresponding {@link Codes} constant.
 *
 * <p>Mirrors {@code client-rust/main/src/error_codes.rs::messages} and {@code
 * client-python/main/angzarr_client/error_codes/messages.py} — same constant names, same string
 * values. Callers MUST NOT interpolate runtime values into these strings; put runtime values in
 * {@code details} instead.
 */
public final class Messages {

  private Messages() {}

  // Validation
  public static final String VALUE_NOT_POSITIVE = "value must be positive";
  public static final String VALUE_NOT_NON_NEGATIVE = "value must be non-negative";
  public static final String VALUE_EMPTY = "value must not be empty";
  public static final String COLLECTION_EMPTY = "collection must not be empty";
  public static final String ENTITY_NOT_FOUND = "entity not found";
  public static final String ENTITY_ALREADY_EXISTS = "entity already exists";
  public static final String STATUS_MISMATCH = "status does not match expected";
  public static final String STATUS_FORBIDDEN = "status is the forbidden value";

  // Builder
  public static final String COMMAND_TYPE_URL_MISSING = "command type_url not set";
  public static final String COMMAND_PAYLOAD_MISSING = "command payload not set";
  public static final String COMMAND_SEQUENCE_MISSING = "sequence not set (call with_sequence)";

  // Conversion
  public static final String ANY_TYPE_MISMATCH =
      "Any type_url does not match expected message type";
  public static final String ANY_DECODE_FAILED =
      "failed to decode Any payload into expected message type";
  public static final String PROTO_UUID_INVALID = "proto UUID bytes are not a valid 16-byte UUID";
  public static final String TIMESTAMP_PARSE_FAILED = "failed to parse RFC3339 timestamp";

  // Transport / connection
  public static final String ENDPOINT_PARSE_FAILED = "failed to parse fallback endpoint";
  public static final String ENDPOINT_INVALID_URI = "endpoint URI is invalid";
  public static final String CONNECTION_FAILED = "connection failed";
  public static final String CONNECTION_FAILED_MAX_RETRIES = "connection failed after max retries";
  public static final String TRANSPORT_ERROR = "transport error";
  public static final String GRPC_ERROR = "grpc error";
  public static final String INVALID_TRANSPORT_MODE = "invalid transport mode env value";
  public static final String INVALID_PORT = "invalid port env value";

  // Dispatch — common
  public static final String HANDLER_WRONG_RESPONSE_KIND = "handler returned wrong response kind";
  public static final String HANDLER_WRONG_REQUEST_KIND =
      "handler dispatched with wrong request kind";
  public static final String NO_HANDLER_REGISTERED =
      "no handler registered for the given (domain, type_url)";
  public static final String MISSING_COMMAND_BOOK = "missing command book";
  public static final String MISSING_COMMAND_PAGE = "missing command page";
  public static final String MISSING_COMMAND_PAYLOAD = "missing command payload";
  public static final String NOTIFICATION_DECODE_FAILED = "failed to decode Notification payload";
  public static final String REJECTION_NOTIFICATION_DECODE_FAILED =
      "failed to decode RejectionNotification payload";

  // Dispatch — saga
  public static final String MISSING_SAGA_SOURCE = "missing saga source";
  public static final String EMPTY_SAGA_SOURCE = "empty saga source";
  public static final String MISSING_SAGA_EVENT_PAYLOAD = "missing event payload";
  public static final String SAGA_INVALID_TYPE_URL = "saga trigger has invalid type_url";
  public static final String SAGA_HANDLER_UNSUPPORTED_RETURN_TYPE =
      "saga handler returned unsupported type";

  // Dispatch — process manager
  public static final String MISSING_PM_TRIGGER = "missing PM trigger";
  public static final String EMPTY_PM_TRIGGER = "empty PM trigger";
  public static final String MISSING_PM_EVENT_PAYLOAD = "missing event payload on PM trigger";
  public static final String PM_INVALID_TYPE_URL = "PM trigger has invalid type_url";
  public static final String PM_HANDLER_WRONG_RETURN_TYPE =
      "PM handler must return ProcessManagerResponse";

  // Dispatch — upcaster
  public static final String UPCASTER_WRONG_RESPONSE_KIND =
      "upcaster handler returned non-Upcaster response";

  // Build-time validation
  public static final String HANDLER_FIELD_EMPTY_STRING =
      "handler field must be a non-empty string";
  public static final String HANDLER_FIELD_EMPTY_LIST = "handler field must be a non-empty list";
  public static final String HANDLER_STATE_NOT_TYPE = "handler 'state' must be a type";
  public static final String HANDLER_UNKNOWN_KIND = "unknown handler kind";
  public static final String ROUTER_NO_HANDLERS = "no handlers registered on Router";
  public static final String DUPLICATE_COMMAND_HANDLER =
      "duplicate command handler registration for (domain, type_url)";
  public static final String MIXED_HANDLER_KINDS =
      "cannot mix handler kinds in one Router — all handlers must share a kind";

  // Saga / PM destinations
  public static final String MISSING_DESTINATION_SEQUENCE = "no sequence for destination domain";

  // Examples — Table (PR #12 SEATS_IDENTICAL; canonical text from Py/Rs)
  public static final String SEATS_IDENTICAL = "current_seat and requested_seat are identical";
}
