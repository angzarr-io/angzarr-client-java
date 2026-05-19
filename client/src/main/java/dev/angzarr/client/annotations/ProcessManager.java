package dev.angzarr.client.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a process manager correlating events across {@code sources} and emitting
 * commands to {@code targets}, with its own state stored in {@code pmDomain}.
 *
 * <p>Equivalent to Python's {@code @process_manager(name, pm_domain, sources, targets, state,
 * sync_targets=[])}.
 *
 * <p>Audit #74: each target listed in {@link #syncTargets()} gets an {@code OutputDomainProbe} at
 * runner startup. Targets not in {@code syncTargets} ride the async bus and are covered by the
 * operator-configured {@code BusProbe} (if any).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ProcessManager {
  String name();

  String pmDomain();

  String[] sources();

  String[] targets();

  Class<?> state();

  /**
   * Audit #74: subset of {@link #targets()} reachable synchronously. Each name appearing here gets
   * an {@code OutputDomainProbe} at runner startup; everything else rides the async bus. Every
   * entry must also appear in {@link #targets()}; the router builder rejects a sync-target name
   * that isn't in {@code targets} as a wiring bug.
   */
  String[] syncTargets() default {};
}
