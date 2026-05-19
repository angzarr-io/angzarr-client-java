package dev.angzarr.client;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.CommandResponse;
import dev.angzarr.Cover;
import dev.angzarr.MergeStrategy;
import dev.angzarr.PageHeader;
import dev.angzarr.SyncMode;
import dev.angzarr.client.error_codes.Codes;
import dev.angzarr.client.error_codes.Keys;
import dev.angzarr.client.error_codes.Messages;
import java.util.Map;
import java.util.UUID;

/**
 * Fluent builder for constructing and executing commands.
 *
 * <p>CommandBuilder reduces boilerplate when creating commands:
 *
 * <ul>
 *   <li>Chain method calls instead of nested object construction
 *   <li>Type-safe methods prevent invalid field combinations
 *   <li>Auto-generates correlation IDs when not provided
 *   <li>Build incrementally, execute when ready
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>{@code
 * CommandResponse response = client.command("orders", orderId)
 *     .withCorrelationId("corr-123")
 *     .withSequence(5)
 *     .withCommand(typeUrl, createOrderCmd)
 *     .execute();
 * }</pre>
 */
public class CommandBuilder {

  private final CommandHandlerClient client;
  private final String domain;
  private final UUID root;
  private String correlationId;
  private int sequence = 0;
  private boolean sequenceSet = false;
  private MergeStrategy mergeStrategy = MergeStrategy.MERGE_COMMUTATIVE;
  private SyncMode syncMode = SyncMode.SYNC_MODE_ASYNC;
  private String typeUrl;
  private byte[] payload;
  private RuntimeException err;

  /**
   * Create a command builder for an existing entity.
   *
   * @param client The command handler client to use
   * @param domain The domain
   * @param root The root UUID
   */
  public CommandBuilder(CommandHandlerClient client, String domain, UUID root) {
    this.client = client;
    this.domain = domain;
    this.root = root;
  }

  /**
   * Create a command builder for a new entity.
   *
   * <p>Audit #20 / Rust {@code a786d1e}: aggregate roots are always client-assigned. The
   * constructor materializes a fresh UUID v4 client-side; the server never sees a missing root.
   *
   * @param client The command handler client to use
   * @param domain The domain
   */
  public CommandBuilder(CommandHandlerClient client, String domain) {
    this(client, domain, UUID.randomUUID());
  }

  /**
   * Set the correlation ID for request tracing.
   *
   * <p>Correlation IDs link related operations across services. If not set, a UUID will be
   * auto-generated on build.
   *
   * @param id The correlation ID
   * @return This builder for chaining
   */
  public CommandBuilder withCorrelationId(String id) {
    this.correlationId = id;
    return this;
  }

  /**
   * Set the expected sequence number for optimistic locking.
   *
   * <p>Defaults to 0 for new entities.
   *
   * @param seq The sequence number
   * @return This builder for chaining
   */
  public CommandBuilder withSequence(int seq) {
    this.sequence = seq;
    this.sequenceSet = true;
    return this;
  }

  /**
   * Set the expected sequence as a {@code long} for cross-language symmetry with Go's {@code
   * uint64} sequence fields and to avoid sign confusion on the wire (proto field is {@code
   * uint32}). Values out of {@code uint32} range are rejected.
   *
   * <p>LOW-3.13: prefer this overload in new code; the {@code int} form silently bit-truncates
   * negatives to large positives.
   */
  public CommandBuilder withSequence(long seq) {
    if (seq < 0L || seq > 0xFFFFFFFFL) {
      throw new Errors.InvalidArgumentError("sequence " + seq + " out of uint32 range");
    }
    this.sequence = (int) seq;
    this.sequenceSet = true;
    return this;
  }

  /**
   * Set the command type URL and message.
   *
   * @param typeUrl The fully-qualified type URL (e.g., "type.googleapis.com/orders.CreateOrder")
   * @param message The protobuf command message
   * @return This builder for chaining
   */
  /**
   * Set the merge strategy for conflict resolution.
   *
   * @param strategy The merge strategy
   * @return This builder for chaining
   */
  public CommandBuilder withMergeStrategy(MergeStrategy strategy) {
    this.mergeStrategy = strategy;
    return this;
  }

  /**
   * Set the synchronization mode used by {@link #execute()}.
   *
   * <p>MED-3.3: cross-language parity with Rust's {@code with_sync_mode} and Python's {@code
   * execute(sync_mode=...)} keyword. The {@link #execute(SyncMode)} overload still accepts an
   * explicit argument; when both are used, the explicit one wins.
   *
   * @param mode The synchronization mode (default {@code SYNC_MODE_ASYNC}).
   * @return This builder for chaining.
   */
  public CommandBuilder withSyncMode(SyncMode mode) {
    this.syncMode = mode;
    return this;
  }

  public CommandBuilder withCommand(String typeUrl, Message message) {
    try {
      this.typeUrl = typeUrl;
      this.payload = message.toByteArray();
    } catch (Exception e) {
      this.err = new Errors.InvalidArgumentError("Failed to serialize command: " + e.getMessage());
    }
    return this;
  }

  /**
   * Build the CommandBook without executing.
   *
   * @return The constructed CommandBook
   * @throws Errors.InvalidArgumentError if required fields are missing
   */
  public CommandBook build() {
    if (err != null) {
      throw err;
    }
    // HIGH-3.2: stamp the cross-language SCREAMING_SNAKE code + canonical
    // static message + structured details on every builder validation
    // error. Cucumber assertions key off these stable identifiers across
    // Py/Rs/Go/Ja/Cs/Cpp.
    if (typeUrl == null || typeUrl.isEmpty()) {
      throw new Errors.InvalidArgumentError(
          Messages.COMMAND_TYPE_URL_MISSING,
          Codes.COMMAND_TYPE_URL_MISSING,
          Map.of(Keys.FIELD, "type_url", Keys.DOMAIN, domain == null ? "" : domain));
    }
    if (payload == null) {
      throw new Errors.InvalidArgumentError(
          Messages.COMMAND_PAYLOAD_MISSING,
          Codes.COMMAND_PAYLOAD_MISSING,
          Map.of(Keys.FIELD, "payload", Keys.DOMAIN, domain == null ? "" : domain));
    }
    if (!sequenceSet) {
      throw new Errors.InvalidArgumentError(
          Messages.COMMAND_SEQUENCE_MISSING,
          Codes.COMMAND_SEQUENCE_MISSING,
          Map.of(Keys.FIELD, "sequence", Keys.DOMAIN, domain == null ? "" : domain));
    }

    String corrId = correlationId;
    if (corrId == null || corrId.isEmpty()) {
      corrId = UUID.randomUUID().toString();
    }

    Cover.Builder coverBuilder = Cover.newBuilder().setDomain(domain).setCorrelationId(corrId);

    if (root != null) {
      coverBuilder.setRoot(Helpers.uuidToProto(root));
    }

    Any commandAny =
        Any.newBuilder()
            .setTypeUrl(typeUrl)
            .setValue(com.google.protobuf.ByteString.copyFrom(payload))
            .build();

    CommandPage page =
        CommandPage.newBuilder()
            .setHeader(PageHeader.newBuilder().setSequence(sequence).build())
            .setCommand(commandAny)
            .setMergeStrategy(mergeStrategy)
            .build();

    return CommandBook.newBuilder().setCover(coverBuilder.build()).addPages(page).build();
  }

  /**
   * Build and execute the command.
   *
   * @return The command response
   * @throws Errors.InvalidArgumentError if required fields are missing
   * @throws Errors.GrpcError if the gRPC call fails
   */
  public CommandResponse execute() {
    CommandBook cmd = build();
    // MED-3.3: honor the stored syncMode (default ASYNC). When callers
    // use the SyncMode-overloaded execute, that explicit arg wins.
    if (syncMode == SyncMode.SYNC_MODE_ASYNC) {
      return client.handle(cmd);
    }
    return client.handle(cmd, syncMode);
  }

  /**
   * Build and execute the command with the specified sync mode.
   *
   * @param syncMode The synchronization mode
   * @return The command response
   * @throws Errors.InvalidArgumentError if required fields are missing
   * @throws Errors.GrpcError if the gRPC call fails
   */
  public CommandResponse execute(SyncMode syncMode) {
    CommandBook cmd = build();
    return client.handle(cmd, syncMode);
  }
}
