package dev.angzarr.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Audit findings #29 / #44: retry policy parity with Python / Rust.
 *
 * <ul>
 *   <li>{@code execute(Callable)} returns first success.
 *   <li>Last failure surfaces on exhaustion.
 *   <li>{@code on_retry(attempt, error)} callback fires between attempts only, not after the last
 *       failure.
 *   <li>{@code computeDelay} caps at {@code maxDelay}.
 * </ul>
 */
class RetryParityTest {

  @Test
  void executeReturnsFirstOk() throws Exception {
    AtomicInteger counter = new AtomicInteger(0);
    ExponentialBackoffRetry policy =
        new ExponentialBackoffRetry.Builder().maxAttempts(5).minDelayMs(1).jitter(false).build();
    Integer result =
        policy.execute(
            () -> {
              int n = counter.getAndIncrement();
              if (n < 2) throw new RuntimeException("not yet");
              return n;
            });
    assertThat(result).isEqualTo(2);
    assertThat(counter).hasValue(3);
  }

  @Test
  void executeReturnsLastErrorWhenAllFail() {
    AtomicInteger counter = new AtomicInteger(0);
    ExponentialBackoffRetry policy =
        new ExponentialBackoffRetry.Builder().maxAttempts(3).minDelayMs(1).jitter(false).build();
    assertThatThrownBy(
            () ->
                policy.execute(
                    () -> {
                      int n = counter.getAndIncrement();
                      throw new RuntimeException("fail-" + n);
                    }))
        .hasMessage("fail-2");
    assertThat(counter).hasValue(3);
  }

  @Test
  void executeStopsAfterFirstSuccess() throws Exception {
    AtomicInteger counter = new AtomicInteger(0);
    ExponentialBackoffRetry policy =
        new ExponentialBackoffRetry.Builder().maxAttempts(5).minDelayMs(1).jitter(false).build();
    Integer result =
        policy.execute(
            () -> {
              counter.incrementAndGet();
              return 42;
            });
    assertThat(result).isEqualTo(42);
    assertThat(counter).hasValue(1);
  }

  @Test
  void onRetryFiresBetweenAttemptsOnly() {
    AtomicInteger fired = new AtomicInteger(0);
    ExponentialBackoffRetry policy =
        new ExponentialBackoffRetry.Builder()
            .maxAttempts(3)
            .minDelayMs(1)
            .jitter(false)
            .onRetry((attempt, err) -> fired.incrementAndGet())
            .build();
    try {
      policy.execute(
          (Runnable)
              () -> {
                throw new RuntimeException("nope");
              });
    } catch (Exception ignored) {
      // expected
    }
    // maxAttempts=3 → callback fires after attempts 0 and 1, NOT after 2.
    assertThat(fired).hasValue(2);
  }

  @Test
  void computeDelayCapsAtMaxDelay() {
    ExponentialBackoffRetry policy =
        new ExponentialBackoffRetry.Builder()
            .minDelayMs(100)
            .maxDelayMs(1000)
            .jitter(false)
            .build();
    // 100ms * 2^20 = ~100B ms — must cap at maxDelayMs.
    assertThat(policy.computeDelay(20)).isEqualTo(1000L);
  }
}
