package dev.angzarr.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.angzarr.Cover;
import dev.angzarr.SyncMode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MED-3.3 — Java should expose {@code withSyncMode(SyncMode)} on the builder for cross-language
 * symmetry. The {@code execute(SyncMode)} overload keeps backward compat; the setter is the new
 * canonical form.
 */
class CommandBuilderSyncModeTest {

  @Test
  void withSyncModeReturnsBuilder() {
    // Pre-fix: only execute(SyncMode) accepted the mode. Now it can be
    // set on the builder and execute() uses the stored mode.
    CommandBuilder b =
        new CommandBuilder(null, "orders", UUID.randomUUID())
            .withSequence(0)
            .withCommand(
                "type.googleapis.com/angzarr_client.proto.angzarr.Cover",
                Cover.getDefaultInstance());
    CommandBuilder same = b.withSyncMode(SyncMode.SYNC_MODE_CASCADE);
    assertThat(same).isSameAs(b);
  }

  @Test
  void buildSucceedsWhenSyncModeSetOnBuilder() {
    // Stored sync mode doesn't affect build(); it's used at execute()
    // time. The test just confirms the setter doesn't break build().
    new CommandBuilder(null, "orders", UUID.randomUUID())
        .withSequence(0)
        .withSyncMode(SyncMode.SYNC_MODE_CASCADE)
        .withCommand(
            "type.googleapis.com/angzarr_client.proto.angzarr.Cover", Cover.getDefaultInstance())
        .build();
  }

  @Test
  void withSequenceLongOverloadAccepts() {
    // LOW-3.13: long overload added for cross-language symmetry.
    new CommandBuilder(null, "orders", UUID.randomUUID())
        .withSequence(5L)
        .withCommand(
            "type.googleapis.com/angzarr_client.proto.angzarr.Cover", Cover.getDefaultInstance())
        .build();
  }

  @Test
  void withSequenceLongRejectsNegative() {
    // LOW-3.13: long overload validates uint32 range.
    assertThrows(
        Errors.InvalidArgumentError.class,
        () -> new CommandBuilder(null, "orders", UUID.randomUUID()).withSequence(-1L));
  }

  @Test
  void withSequenceLongRejectsOverflow() {
    // LOW-3.13: > 2^32 - 1 rejected
    assertThrows(
        Errors.InvalidArgumentError.class,
        () -> new CommandBuilder(null, "orders", UUID.randomUUID()).withSequence(0x1_0000_0000L));
  }
}
