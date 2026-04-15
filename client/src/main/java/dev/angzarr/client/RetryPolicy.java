package dev.angzarr.client;

/**
 * Strategy for retrying failed operations.
 *
 * <p>Implement this interface to provide custom retry behavior.
 * The default implementation is {@link ExponentialBackoffRetry}.
 */
public interface RetryPolicy {

    /**
     * Run the operation, retrying on failure according to the policy.
     *
     * @param operation The operation to execute
     * @throws Exception the last exception if all attempts fail
     */
    void execute(Runnable operation) throws Exception;
}
