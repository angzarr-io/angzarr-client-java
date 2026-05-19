package dev.angzarr.client;

import java.util.concurrent.Callable;

/**
 * Strategy for retrying failed operations.
 *
 * <p>Mirrors Python's {@code retry.RetryPolicy} and Rust's {@code ExponentialBackoffRetry}. The
 * default implementation is {@link ExponentialBackoffRetry}.
 */
public interface RetryPolicy {

  /**
   * Run the operation, retrying on failure according to the policy.
   *
   * @param operation The operation to execute
   * @throws Exception the last exception if all attempts fail
   */
  void execute(Runnable operation) throws Exception;

  /**
   * Run a value-returning operation, retrying on failure. Returns the first successful result; if
   * every attempt fails, the last exception propagates.
   *
   * <p>Mirrors Python's {@code RetryPolicy.execute(operation)} and Rust's {@code
   * RetryPolicy::execute(op)}.
   */
  default <T> T execute(Callable<T> operation) throws Exception {
    // Adapter — concrete implementations should override for proper
    // multi-attempt behaviour with backoff.
    return operation.call();
  }
}
