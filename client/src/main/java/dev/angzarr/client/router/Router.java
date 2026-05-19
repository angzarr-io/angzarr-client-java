package dev.angzarr.client.router;

import dev.angzarr.client.error_codes.Codes;
import dev.angzarr.client.error_codes.Keys;
import dev.angzarr.client.error_codes.Messages;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Builder for the unified Tier 5 router.
 *
 * <p>Each call to {@link #withHandler(Class, Supplier)} registers a handler class (annotated with
 * {@code @Aggregate}, {@code @Saga}, {@code @ProcessManager}, or {@code @Projector}) together with
 * a zero-arg factory. Factories are <b>not</b> invoked at registration or build time — only during
 * dispatch, so each request gets a fresh (or pooled) handler instance and the built router is safe
 * to share across threads.
 *
 * <p>{@link #build()} infers the kind from the registered classes and returns a typed runtime
 * router. All registered classes must share one kind; mixing kinds raises {@link BuildException}.
 */
public final class Router {

  private final String name;
  private final List<Registration<?>> registrations = new ArrayList<>();

  private Router(String name) {
    this.name = Objects.requireNonNull(name, "router name");
  }

  /** Start building a router with the given name. */
  public static Router newBuilder(String name) {
    return new Router(name);
  }

  /**
   * Register a handler class with a factory that produces a fresh instance on each dispatch.
   *
   * <p>The factory is not invoked at registration time. Validation of the class (that it carries a
   * kind annotation) is deferred to {@link #build()}; this mirrors the Python reference where
   * registration stores the tuple and build-time inference catches problems.
   */
  public <H> Router withHandler(Class<H> cls, Supplier<? extends H> factory) {
    Objects.requireNonNull(cls, "handler class");
    Objects.requireNonNull(factory, "handler factory");
    HandlerMetadata metadata;
    try {
      metadata = HandlerMetadata.of(cls);
    } catch (IllegalStateException ise) {
      // MED-4.7: stamp the cross-language HANDLER_UNKNOWN_KIND code +
      // canonical static message + structured details instead of the
      // generic "cannot register" wrap. Mirrors Py/Rs.
      Map<String, String> details = new LinkedHashMap<>();
      details.put(Keys.HANDLER_CLASS, cls.getName());
      details.put(Keys.ROUTER_NAME, name);
      details.put(Keys.CAUSE, ise.getMessage() == null ? "" : ise.getMessage());
      throw new BuildException(
          Messages.HANDLER_UNKNOWN_KIND, ise, Codes.HANDLER_UNKNOWN_KIND, details);
    }
    registrations.add(new Registration<>(cls, factory, metadata));
    return this;
  }

  /** Finalize the router. */
  public Built build() {
    if (registrations.isEmpty()) {
      throw new BuildException(
          Messages.ROUTER_NO_HANDLERS,
          null,
          Codes.ROUTER_NO_HANDLERS,
          Map.of(Keys.ROUTER_NAME, name));
    }
    EnumSet<HandlerMetadata.Kind> kinds = EnumSet.noneOf(HandlerMetadata.Kind.class);
    HandlerMetadata.Kind firstKind = null;
    HandlerMetadata.Kind otherKind = null;
    for (Registration<?> r : registrations) {
      HandlerMetadata.Kind k = r.metadata().kind();
      if (firstKind == null) {
        firstKind = k;
      } else if (k != firstKind && otherKind == null) {
        otherKind = k;
      }
      kinds.add(k);
    }
    if (kinds.size() > 1) {
      Map<String, String> details = new LinkedHashMap<>();
      details.put(Keys.HANDLER_KIND, firstKind.name());
      details.put(Keys.OTHER_KIND, otherKind == null ? "" : otherKind.name());
      details.put(Keys.ROUTER_NAME, name);
      throw new BuildException(
          Messages.MIXED_HANDLER_KINDS, null, Codes.MIXED_HANDLER_KINDS, details);
    }
    HandlerMetadata.Kind kind = firstKind;
    // MED-4.8: validate handler annotation fields are non-empty BEFORE
    // building. Stamp canonical HANDLER_FIELD_EMPTY_* codes so cross-
    // language cucumber assertions match Py/Rs behavior.
    validateHandlerFields(name, kind, registrations);
    // Audit #18 / #62: at most one CommandHandler per (domain, type_url).
    // Saga / PM / projector / upcaster fan-out is unaffected — those kinds
    // legitimately broadcast.
    if (kind == HandlerMetadata.Kind.COMMAND_HANDLER) {
      checkNoDuplicateCommandHandlers(name, registrations);
    }
    return switch (kind) {
      case COMMAND_HANDLER -> new CommandHandlerRouter<>(name, List.copyOf(registrations));
      case SAGA -> new SagaRouter(name, List.copyOf(registrations));
      case PROCESS_MANAGER -> {
        validateProcessManagerSyncTargets(name, registrations);
        yield new ProcessManagerRouter<>(name, List.copyOf(registrations));
      }
      case PROJECTOR -> new ProjectorRouter(name, List.copyOf(registrations));
    };
  }

  /**
   * MED-4.8: enforce non-empty annotation fields per kind. Mirrors Python's {@code
   * _require_non_empty_str} / {@code _require_non_empty_list} checks in {@code
   * router/validation.py}.
   */
  private static void validateHandlerFields(
      String routerName, HandlerMetadata.Kind kind, List<Registration<?>> registrations) {
    for (Registration<?> r : registrations) {
      switch (kind) {
        case COMMAND_HANDLER -> {
          dev.angzarr.client.annotations.Aggregate a =
              (dev.angzarr.client.annotations.Aggregate) r.metadata().kindAnnotation();
          requireNonEmptyString(routerName, r, "domain", a.domain());
        }
        case SAGA -> {
          dev.angzarr.client.annotations.Saga s =
              (dev.angzarr.client.annotations.Saga) r.metadata().kindAnnotation();
          requireNonEmptyString(routerName, r, "name", s.name());
          requireNonEmptyString(routerName, r, "source", s.source());
          requireNonEmptyString(routerName, r, "target", s.target());
        }
        case PROCESS_MANAGER -> {
          dev.angzarr.client.annotations.ProcessManager pm =
              (dev.angzarr.client.annotations.ProcessManager) r.metadata().kindAnnotation();
          requireNonEmptyString(routerName, r, "name", pm.name());
          requireNonEmptyString(routerName, r, "pm_domain", pm.pmDomain());
          requireNonEmptyList(routerName, r, "sources", pm.sources());
          requireNonEmptyList(routerName, r, "targets", pm.targets());
        }
        case PROJECTOR -> {
          dev.angzarr.client.annotations.Projector p =
              (dev.angzarr.client.annotations.Projector) r.metadata().kindAnnotation();
          requireNonEmptyString(routerName, r, "name", p.name());
          requireNonEmptyList(routerName, r, "domains", p.domains());
        }
      }
    }
  }

  private static void requireNonEmptyString(
      String routerName, Registration<?> r, String fieldName, String value) {
    if (value != null && !value.isEmpty()) return;
    Map<String, String> details = new LinkedHashMap<>();
    details.put(Keys.FIELD, fieldName);
    details.put(Keys.HANDLER_CLASS, r.handlerClass().getName());
    details.put(Keys.ROUTER_NAME, routerName);
    throw new BuildException(
        Messages.HANDLER_FIELD_EMPTY_STRING, null, Codes.HANDLER_FIELD_EMPTY_STRING, details);
  }

  private static void requireNonEmptyList(
      String routerName, Registration<?> r, String fieldName, String[] values) {
    if (values != null && values.length > 0) return;
    Map<String, String> details = new LinkedHashMap<>();
    details.put(Keys.FIELD, fieldName);
    details.put(Keys.HANDLER_CLASS, r.handlerClass().getName());
    details.put(Keys.ROUTER_NAME, routerName);
    throw new BuildException(
        Messages.HANDLER_FIELD_EMPTY_LIST, null, Codes.HANDLER_FIELD_EMPTY_LIST, details);
  }

  /**
   * Audit #74: every name in {@code @ProcessManager(syncTargets = {...})} must also appear in
   * {@code targets}; an entry that isn't is a wiring bug (the runner would build a probe for a
   * domain the PM never emits to). Mirrors Python's {@code @process_manager(sync_targets=...)}
   * validation.
   */
  private static void validateProcessManagerSyncTargets(
      String routerName, List<Registration<?>> registrations) {
    for (Registration<?> r : registrations) {
      dev.angzarr.client.annotations.ProcessManager pm =
          (dev.angzarr.client.annotations.ProcessManager) r.metadata().kindAnnotation();
      Set<String> targets = new HashSet<>(java.util.Arrays.asList(pm.targets()));
      for (String s : pm.syncTargets()) {
        if (s == null || s.isEmpty()) continue;
        if (!targets.contains(s)) {
          // MED-4.6: stamp canonical HANDLER_FIELD_EMPTY_LIST code +
          // static message + structured details. Pre-fix this was a
          // dynamic-interpolated string with no code, breaking
          // cross-language cucumber assertions.
          Map<String, String> details = new LinkedHashMap<>();
          details.put(Keys.FIELD, "sync_targets");
          details.put(Keys.HANDLER_CLASS, r.handlerClass().getName());
          details.put(Keys.ROUTER_NAME, routerName);
          details.put(Keys.INPUT, s);
          throw new BuildException(
              Messages.HANDLER_FIELD_EMPTY_LIST, null, Codes.HANDLER_FIELD_EMPTY_LIST, details);
        }
      }
    }
  }

  private static void checkNoDuplicateCommandHandlers(
      String routerName, List<Registration<?>> registrations) {
    Set<String> seen = new HashSet<>();
    for (Registration<?> r : registrations) {
      dev.angzarr.client.annotations.Aggregate a =
          (dev.angzarr.client.annotations.Aggregate) r.metadata().kindAnnotation();
      String domain = a.domain();
      for (Map.Entry<Class<?>, MethodHandle> e : r.metadata().handles().entrySet()) {
        String typeUrl = Dispatch.typeUrlFor(e.getKey());
        String key = domain + "\0" + typeUrl;
        if (!seen.add(key)) {
          Map<String, String> details = new LinkedHashMap<>();
          details.put(Keys.DOMAIN, domain);
          details.put(Keys.TYPE_URL, typeUrl);
          details.put(Keys.ROUTER_NAME, routerName);
          throw new BuildException(
              Messages.DUPLICATE_COMMAND_HANDLER, null, Codes.DUPLICATE_COMMAND_HANDLER, details);
        }
      }
    }
  }

  String name() {
    return name;
  }

  List<Registration<?>> registrations() {
    return registrations;
  }
}
