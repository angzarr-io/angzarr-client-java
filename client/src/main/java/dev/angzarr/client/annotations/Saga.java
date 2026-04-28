package dev.angzarr.client.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a saga translating events from a {@code source} domain into commands for a
 * {@code target} domain.
 *
 * <p>Equivalent to Python's {@code @saga(name, source, target)}. Sagas are stateless translators;
 * their handler methods receive a {@code SagaContext} so destination sequences can be stamped on
 * outbound commands.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Saga {
    String name();

    String source();

    String target();
}
