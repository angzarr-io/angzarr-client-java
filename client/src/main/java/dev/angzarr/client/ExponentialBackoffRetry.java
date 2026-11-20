package dev.angzarr.client;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Retries with exponential backoff and optional jitter.
 *
 * <p>Default configuration matches Rust's backoff:
 * 10 attempts, 100ms-5s delay, with jitter.
 */
public class ExponentialBackoffRetry implements RetryPolicy {

    private final long minDelayMs;
    private final long maxDelayMs;
    private final int maxAttempts;
    private final boolean jitter;

    /**
     * Create with default configuration (10 attempts, 100ms-5s, jitter enabled).
     */
    public ExponentialBackoffRetry() {
        this(100, 5000, 10, true);
    }

    /**
     * Create with custom configuration.
     *
     * @param minDelayMs Minimum delay between retries in milliseconds
     * @param maxDelayMs Maximum delay between retries in milliseconds
     * @param maxAttempts Maximum number of attempts
     * @param jitter Whether to add randomized jitter to delays
     */
    public ExponentialBackoffRetry(long minDelayMs, long maxDelayMs, int maxAttempts, boolean jitter) {
        this.minDelayMs = minDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.maxAttempts = maxAttempts;
        this.jitter = jitter;
    }

    @Override
    public void execute(Runnable operation) throws Exception {
        Exception lastError = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                operation.run();
                return;
            } catch (Exception e) {
                lastError = e;
                if (attempt < maxAttempts - 1) {
                    Thread.sleep(computeDelay(attempt));
                }
            }
        }
        throw lastError;
    }

    private long computeDelay(int attempt) {
        double delay = minDelayMs * Math.pow(2, attempt);
        delay = Math.min(delay, maxDelayMs);
        if (jitter) {
            delay = delay * (0.5 + ThreadLocalRandom.current().nextDouble() * 0.5);
        }
        return (long) delay;
    }

    /**
     * Returns the default retry policy matching Rust's backoff config.
     */
    public static RetryPolicy defaultPolicy() {
        return new ExponentialBackoffRetry();
    }
}
