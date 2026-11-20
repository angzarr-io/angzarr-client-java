package dev.angzarr.client.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a projector consuming events from one or more source domains and producing
 * external side effects.
 *
 * <p>Equivalent to Python's {@code @projector(name, domains)}. Projector methods are annotated
 * with {@code @Handles(EventClass)}; the projector instance is reused across every event in a
 * single projection run so implementations can batch writes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Projector {
    String name();

    String[] domains();
}
