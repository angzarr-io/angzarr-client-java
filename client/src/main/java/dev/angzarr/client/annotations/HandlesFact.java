package dev.angzarr.client.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an aggregate method as a <em>fact handler</em> — a handler that applies an
 * already-validated event injected by a saga or process manager directly, without going through
 * command validation.
 *
 * <p>Equivalent to Python's {@code @handles_fact(EventType)} decorator and Rust's {@code
 * #[handles_fact]} attribute. Audit finding #45.
 *
 * <p>Fact handlers run inside the aggregate transaction the same way {@code @Handles} handlers do,
 * but the framework skips the command validation step because the source aggregate has already
 * vouched for the payload. Use this for cross-domain fact-injection patterns where a saga/PM has
 * decided the event is canonical.
 *
 * <p>LOW-4.16 — added for cross-language parity.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HandlesFact {
  /** The event class this fact handler accepts. */
  Class<?> value();
}
