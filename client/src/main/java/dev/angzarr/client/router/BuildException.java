package dev.angzarr.client.router;

/** Thrown by {@link Router#build()} when the registered handlers are inconsistent or invalid. */
public class BuildException extends RuntimeException {
    public BuildException(String message) {
        super(message);
    }

    public BuildException(String message, Throwable cause) {
        super(message, cause);
    }
}
