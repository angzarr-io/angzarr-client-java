package dev.angzarr.client.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a zero-argument instance method as the state factory for an aggregate or process manager.
 *
 * <p>Equivalent to Python's {@code @state_factory}. Used when a fresh state instance requires more
 * setup than {@code new StateClass()}. At most one {@code @StateFactory} method per handler class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface StateFactory {}
