package dev.angzarr.client;

import static org.assertj.core.api.Assertions.assertThat;

import dev.angzarr.Query;
import org.junit.jupiter.api.Test;

/**
 * Audit #27: {@code rangeTo(lower, upper)} treats upper bound as <b>inclusive</b>. Mirrors Python
 * {@code range_to} and Rust {@code range_to}.
 */
class QueryBuilderRangeTest {

  @Test
  void rangeToSetsBothLowerAndUpper() {
    QueryBuilder qb = new QueryBuilder(null, "orders", java.util.UUID.randomUUID());
    Query q = qb.rangeTo(10, 20).build();
    assertThat(q.hasRange()).isTrue();
    assertThat(q.getRange().getLower()).isEqualTo(10);
    // Audit #27: upper is INCLUSIVE — 20 selects events through seq 20.
    assertThat(q.getRange().getUpper()).isEqualTo(20);
  }

  @Test
  void rangeToOverridesPriorTemporalSelection() {
    // Last-selection-wins (P2.2 / finding #23).
    QueryBuilder qb = new QueryBuilder(null, "orders", java.util.UUID.randomUUID());
    Query q = qb.asOfSequence(99).rangeTo(5, 10).build();
    assertThat(q.hasRange()).isTrue();
    assertThat(q.hasTemporal()).isFalse();
  }

  @Test
  void asOfSequenceOverridesPriorRange() {
    QueryBuilder qb = new QueryBuilder(null, "orders", java.util.UUID.randomUUID());
    Query q = qb.range(3).asOfSequence(99).build();
    assertThat(q.hasTemporal()).isTrue();
    assertThat(q.hasRange()).isFalse();
    assertThat(q.getTemporal().getAsOfSequence()).isEqualTo(99);
  }
}
