package dev.angzarr.client.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a command handler (aggregate) for a single domain.
 *
 * <p>Equivalent to Python's {@code @command_handler(domain, state)}. The annotated class must
 * expose at least one {@code @Handles}-annotated method and, for stateful aggregates, an
 * {@code @Applies} method per event type. State construction defaults to {@code new State()} and
 * can be overridden with {@code @StateFactory}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Aggregate {
    String domain();

    Class<?> state();
}
