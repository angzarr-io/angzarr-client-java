package dev.angzarr.client;

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * Retries with exponential backoff and optional jitter.
 *
 * <p>Default configuration matches the cross-language spec: 10 attempts, 100ms-5s delay, with
 * jitter. Mirrors Python's {@code ExponentialBackoffRetry} and Rust's {@code
 * ExponentialBackoffRetry}. Audit findings #29 (deterministic-jitter via PRNG, not clock-modulo)
 * and #44 (single source of truth for retry semantics).
 */
public class ExponentialBackoffRetry implements RetryPolicy {

  private final long minDelayMs;
  private final long maxDelayMs;
  private final int maxAttempts;
  private final boolean jitter;
  private final BiConsumer<Integer, Throwable> onRetry;

  /** Create with default configuration (10 attempts, 100ms-5s, jitter enabled). */
  public ExponentialBackoffRetry() {
    this(100, 5000, 10, true, null);
  }

  /** Backward-compatible 4-arg constructor — no on-retry callback. */
  public ExponentialBackoffRetry(
      long minDelayMs, long maxDelayMs, int maxAttempts, boolean jitter) {
    this(minDelayMs, maxDelayMs, maxAttempts, jitter, null);
  }

  /**
   * Create with custom configuration including an on-retry callback.
   *
   * @param onRetry called before each backoff sleep with {@code (zeroIndexedAttempt, error)}. Not
   *     invoked before the first attempt nor after the final failure.
   */
  public ExponentialBackoffRetry(
      long minDelayMs,
      long maxDelayMs,
      int maxAttempts,
      boolean jitter,
      BiConsumer<Integer, Throwable> onRetry) {
    this.minDelayMs = minDelayMs;
    this.maxDelayMs = maxDelayMs;
    this.maxAttempts = maxAttempts;
    this.jitter = jitter;
    this.onRetry = onRetry;
  }

  /** Fluent builder mirroring Rust's {@code with_*} chain. */
  public static class Builder {
    private long minDelayMs = 100;
    private long maxDelayMs = 5000;
    private int maxAttempts = 10;
    private boolean jitter = true;
    private BiConsumer<Integer, Throwable> onRetry;

    public Builder minDelayMs(long v) {
      this.minDelayMs = v;
      return this;
    }

    public Builder maxDelayMs(long v) {
      this.maxDelayMs = v;
      return this;
    }

    public Builder maxAttempts(int v) {
      this.maxAttempts = v;
      return this;
    }

    public Builder jitter(boolean v) {
      this.jitter = v;
      return this;
    }

    public Builder onRetry(BiConsumer<Integer, Throwable> cb) {
      this.onRetry = cb;
      return this;
    }

    public ExponentialBackoffRetry build() {
      return new ExponentialBackoffRetry(minDelayMs, maxDelayMs, maxAttempts, jitter, onRetry);
    }
  }

  @Override
  public void execute(Runnable operation) throws Exception {
    execute(
        () -> {
          operation.run();
          return null;
        });
  }

  @Override
  public <T> T execute(Callable<T> operation) throws Exception {
    Exception lastError = null;
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        return operation.call();
      } catch (Exception e) {
        lastError = e;
        boolean isLast = attempt + 1 >= maxAttempts;
        if (!isLast) {
          if (onRetry != null) {
            onRetry.accept(attempt, e);
          }
          long delay = computeDelay(attempt);
          if (delay > 0) {
            Thread.sleep(delay);
          }
        }
      }
    }
    throw lastError;
  }

  /**
   * Compute the delay for the given zero-indexed attempt number.
   *
   * <p>Matches Python {@code _compute_delay} and Rust {@code compute_delay}: {@code minDelay *
   * 2^attempt}, capped at {@code maxDelay}, optionally multiplied by {@code 0.5 + rand()*0.5} when
   * {@code jitter} is true. Audit #29: PRNG-based jitter so concurrent retries decorrelate.
   */
  public long computeDelay(int attempt) {
    // Cap exponent to prevent overflow on large attempts.
    int safeAttempt = Math.min(attempt, 30);
    double delay = minDelayMs * Math.pow(2, safeAttempt);
    delay = Math.min(delay, maxDelayMs);
    if (jitter) {
      delay = delay * (0.5 + ThreadLocalRandom.current().nextDouble() * 0.5);
    }
    return (long) delay;
  }

  /** Returns the default retry policy matching cross-language defaults. */
  public static RetryPolicy defaultPolicy() {
    return new ExponentialBackoffRetry();
  }
}
