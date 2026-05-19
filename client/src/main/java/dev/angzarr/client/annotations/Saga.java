package dev.angzarr.client.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a saga translating events from a {@code source} domain into commands for a
 * {@code target} domain.
 *
 * <p>Equivalent to Python's {@code @saga(name, source, target, sync=False)}. Sagas are stateless
 * translators; their handler methods receive a {@code SagaContext} so destination sequences can be
 * stamped on outbound commands.
 *
 * <p>Audit #74: when {@link #sync()} is true, the runner adds an {@code OutputDomainProbe} for
 * {@code target} so the pod stays {@code NOT_SERVING} until the downstream CH is reachable. When
 * false (default), the target rides the async bus and is covered by the operator-configured {@code
 * BusProbe} (if any).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Saga {
  String name();

  String source();

  String target();

  /**
   * Audit #74: when true, the saga's {@code target} domain is reachable synchronously (the runner
   * adds an {@code OutputDomainProbe} for it). When false (default), the target rides the async
   * bus. Mirrors Python's {@code @saga(sync=...)} and Rust's {@code #[saga(sync = ...)]}.
   */
  boolean sync() default false;
}
