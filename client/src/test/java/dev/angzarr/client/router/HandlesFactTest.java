package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.angzarr.Cover;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.HandlesFact;
import org.junit.jupiter.api.Test;

/**
 * LOW-4.16 — Java {@code @HandlesFact} annotation parity with Python's {@code @handles_fact} /
 * Rust's {@code #[handles_fact]}.
 *
 * <p>Aggregate methods marked with {@code @HandlesFact} apply an already- validated event injected
 * by a saga or PM, bypassing command validation. Audit finding #45.
 */
class HandlesFactTest {

  public static final class S {}

  @Aggregate(domain = "orders", state = S.class)
  static final class Agg {
    @HandlesFact(Cover.class)
    public void applyFact(Cover c, S s) {}
  }

  @Aggregate(domain = "orders", state = S.class)
  static final class StackedRole {
    @dev.angzarr.client.annotations.Handles(Cover.class)
    @HandlesFact(Cover.class)
    public void bad(Cover c, S s, long seq) {}
  }

  @Test
  void handlesFactPopulatesMetadataMap() {
    HandlerMetadata md = HandlerMetadata.of(Agg.class);
    assertThat(md.handlesFact()).containsKey(Cover.class);
  }

  @Test
  void handlesFactRejectsStackingWithHandles() {
    // @HandlesFact + @Handles on the same method must fail like other
    // method-role stacks per LOW-4.15.
    assertThrows(IllegalStateException.class, () -> HandlerMetadata.of(StackedRole.class));
  }
}
