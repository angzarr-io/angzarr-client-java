package dev.angzarr.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.angzarr.CommandBook;
import dev.angzarr.Cover;
import org.junit.jupiter.api.Test;

/**
 * Audit findings for {@link CommandBuilder} / {@link QueryBuilder}:
 *
 * <ul>
 *   <li>#20: {@code commandNew} auto-generates a UUID v4 root client-side.
 *   <li>#34: {@code asOfTime} raises {@link Errors.InvalidTimestampError} synchronously, no
 *       deferred sticky error.
 *   <li>P2.4b: builder accepts standard {@code @Aggregate} state types.
 * </ul>
 */
class BuilderParityTest {

  @Test
  void commandNewMaterializesRootClientSide() {
    // Audit #20 / Rust `a786d1e`: client-assigned UUID v4 — never deferred
    // to the server. The CommandBook must have a non-empty root before
    // the request goes out.
    CommandBuilder b =
        new CommandBuilder(null, "orders") // commandNew variant
            .withSequence(0)
            .withCommand(
                "type.googleapis.com/angzarr_client.proto.angzarr.Cover",
                Cover.getDefaultInstance());
    CommandBook built = b.build();
    Cover cover = built.getCover();
    assertThat(cover.hasRoot()).isTrue();
    assertThat(cover.getRoot().getValue().size()).isEqualTo(16);
    // Two consecutive calls must produce distinct roots.
    CommandBook other =
        new CommandBuilder(null, "orders")
            .withSequence(0)
            .withCommand(
                "type.googleapis.com/angzarr_client.proto.angzarr.Cover",
                Cover.getDefaultInstance())
            .build();
    assertThat(other.getCover().getRoot()).isNotEqualTo(cover.getRoot());
  }

  @Test
  void asOfTimeRaisesSynchronouslyOnBadInput() {
    // Audit #34: bad RFC3339 throws InvalidTimestampError at the call
    // site, NOT deferred to build(). Mirrors Rust's
    // `as_of_time(...) -> Result<Self>` short-circuit.
    QueryBuilder qb = new QueryBuilder(null, "orders", java.util.UUID.randomUUID());
    assertThatThrownBy(() -> qb.asOfTime("not-a-timestamp"))
        .isInstanceOf(Errors.InvalidTimestampError.class);
  }

  @Test
  void asOfTimeChainedWithLastSelectionWinsDoesNotLeakStickyError() {
    // Pre-port: a bad asOfTime() captured into `_err` survived
    // last-call-wins setters, making a later call to build() raise the
    // stale error. Audit #34 / Python finding-#34 fixes this.
    // Synchronous raise = no sticky state to leak.
    QueryBuilder qb = new QueryBuilder(null, "orders", java.util.UUID.randomUUID());
    try {
      qb.asOfTime("not-a-timestamp");
    } catch (Errors.InvalidTimestampError ignored) {
      // expected
    }
    // Subsequent calls must succeed — no sticky error.
    qb.asOfSequence(5).build();
  }
}
