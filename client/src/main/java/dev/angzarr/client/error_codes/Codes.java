package dev.angzarr.client.error_codes;

/**
 * SCREAMING_SNAKE event-type identifiers — the value of {@code code} on every error this client
 * constructs.
 *
 * <p>Mirrors {@code client-rust/main/src/error_codes.rs::codes} and {@code
 * client-python/main/angzarr_client/error_codes/codes.py} — same constant names, same string
 * values. Audit findings #59 / #75-#86 / #40.
 */
public final class Codes {

  private Codes() {}

  // Validation
  public static final String VALUE_NOT_POSITIVE = "VALUE_NOT_POSITIVE";
  public static final String VALUE_NOT_NON_NEGATIVE = "VALUE_NOT_NON_NEGATIVE";
  public static final String VALUE_EMPTY = "VALUE_EMPTY";
  public static final String COLLECTION_EMPTY = "COLLECTION_EMPTY";
  public static final String ENTITY_NOT_FOUND = "ENTITY_NOT_FOUND";
  public static final String ENTITY_ALREADY_EXISTS = "ENTITY_ALREADY_EXISTS";
  public static final String STATUS_MISMATCH = "STATUS_MISMATCH";
  public static final String STATUS_FORBIDDEN = "STATUS_FORBIDDEN";

  // Builder
  public static final String COMMAND_TYPE_URL_MISSING = "COMMAND_TYPE_URL_MISSING";
  public static final String COMMAND_PAYLOAD_MISSING = "COMMAND_PAYLOAD_MISSING";
  public static final String COMMAND_SEQUENCE_MISSING = "COMMAND_SEQUENCE_MISSING";

  // Conversion
  public static final String ANY_TYPE_MISMATCH = "ANY_TYPE_MISMATCH";
  public static final String ANY_DECODE_FAILED = "ANY_DECODE_FAILED";
  public static final String PROTO_UUID_INVALID = "PROTO_UUID_INVALID";
  public static final String TIMESTAMP_PARSE_FAILED = "TIMESTAMP_PARSE_FAILED";

  // Transport / connection
  public static final String ENDPOINT_PARSE_FAILED = "ENDPOINT_PARSE_FAILED";
  public static final String ENDPOINT_INVALID_URI = "ENDPOINT_INVALID_URI";
  public static final String CONNECTION_FAILED = "CONNECTION_FAILED";
  public static final String CONNECTION_FAILED_MAX_RETRIES = "CONNECTION_FAILED_MAX_RETRIES";
  public static final String TRANSPORT_ERROR = "TRANSPORT_ERROR";
  public static final String GRPC_ERROR = "GRPC_ERROR";

  /** Audit #40: bad ANGZARR_MODE env value. */
  public static final String INVALID_TRANSPORT_MODE = "INVALID_TRANSPORT_MODE";

  /** Audit #40: bad ANGZARR_CH_PORT env value. */
  public static final String INVALID_PORT = "INVALID_PORT";

  // Dispatch — common
  public static final String HANDLER_WRONG_RESPONSE_KIND = "HANDLER_WRONG_RESPONSE_KIND";
  public static final String HANDLER_WRONG_REQUEST_KIND = "HANDLER_WRONG_REQUEST_KIND";
  public static final String NO_HANDLER_REGISTERED = "NO_HANDLER_REGISTERED";
  public static final String MISSING_COMMAND_BOOK = "MISSING_COMMAND_BOOK";
  public static final String MISSING_COMMAND_PAGE = "MISSING_COMMAND_PAGE";
  public static final String MISSING_COMMAND_PAYLOAD = "MISSING_COMMAND_PAYLOAD";
  public static final String NOTIFICATION_DECODE_FAILED = "NOTIFICATION_DECODE_FAILED";
  public static final String REJECTION_NOTIFICATION_DECODE_FAILED =
      "REJECTION_NOTIFICATION_DECODE_FAILED";

  // Dispatch — saga
  public static final String MISSING_SAGA_SOURCE = "MISSING_SAGA_SOURCE";
  public static final String EMPTY_SAGA_SOURCE = "EMPTY_SAGA_SOURCE";
  public static final String MISSING_SAGA_EVENT_PAYLOAD = "MISSING_SAGA_EVENT_PAYLOAD";
  public static final String SAGA_INVALID_TYPE_URL = "SAGA_INVALID_TYPE_URL";
  public static final String SAGA_HANDLER_UNSUPPORTED_RETURN_TYPE =
      "SAGA_HANDLER_UNSUPPORTED_RETURN_TYPE";

  // Dispatch — process manager
  public static final String MISSING_PM_TRIGGER = "MISSING_PM_TRIGGER";
  public static final String EMPTY_PM_TRIGGER = "EMPTY_PM_TRIGGER";
  public static final String MISSING_PM_EVENT_PAYLOAD = "MISSING_PM_EVENT_PAYLOAD";
  public static final String PM_INVALID_TYPE_URL = "PM_INVALID_TYPE_URL";
  public static final String PM_HANDLER_WRONG_RETURN_TYPE = "PM_HANDLER_WRONG_RETURN_TYPE";

  // Dispatch — upcaster
  public static final String UPCASTER_WRONG_RESPONSE_KIND = "UPCASTER_WRONG_RESPONSE_KIND";

  // Build-time validation
  public static final String HANDLER_FIELD_EMPTY_STRING = "HANDLER_FIELD_EMPTY_STRING";
  public static final String HANDLER_FIELD_EMPTY_LIST = "HANDLER_FIELD_EMPTY_LIST";
  public static final String HANDLER_STATE_NOT_TYPE = "HANDLER_STATE_NOT_TYPE";
  public static final String HANDLER_UNKNOWN_KIND = "HANDLER_UNKNOWN_KIND";
  public static final String ROUTER_NO_HANDLERS = "ROUTER_NO_HANDLERS";

  /** Audit #62 / #18: two CommandHandlers claim the same {@code (domain, type_url)} pair. */
  public static final String DUPLICATE_COMMAND_HANDLER = "DUPLICATE_COMMAND_HANDLER";

  /** Audit #72: Router built with handlers of different kinds. */
  public static final String MIXED_HANDLER_KINDS = "MIXED_HANDLER_KINDS";

  // Saga / PM destinations
  /** Audit #64: tried to stamp a command for a domain not in destination_sequences. */
  public static final String MISSING_DESTINATION_SEQUENCE = "MISSING_DESTINATION_SEQUENCE";

  // Domain — table seating
  /**
   * PR #12 design decision #2 — ChangeSeats rejected when current_seat == requested_seat.
   * Cross-language constant; matches Py/Rs/Go/C#/Cpp error_codes inventories.
   */
  public static final String SEATS_IDENTICAL = "SEATS_IDENTICAL";
}
