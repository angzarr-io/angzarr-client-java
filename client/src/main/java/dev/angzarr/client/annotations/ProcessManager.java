package dev.angzarr.client.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a process manager correlating events across {@code sources} and emitting
 * commands to {@code targets}, with its own state stored in {@code pmDomain}.
 *
 * <p>Equivalent to Python's {@code @process_manager(name, pm_domain, sources, targets, state)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ProcessManager {
    String name();

    String pmDomain();

    String[] sources();

    String[] targets();

    Class<?> state();
}
