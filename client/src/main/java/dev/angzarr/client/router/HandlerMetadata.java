package dev.angzarr.client.router;

import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Applies;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.ProcessManager;
import dev.angzarr.client.annotations.Projector;
import dev.angzarr.client.annotations.Rejected;
import dev.angzarr.client.annotations.Saga;
import dev.angzarr.client.annotations.StateFactory;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reflection-derived metadata for a handler class, cached per {@code Class}.
 *
 * <p>One instance per handler class is produced lazily on first call to {@link #of(Class)} and
 * cached in a {@link ClassValue}. Holds the {@link Kind} derived from the class-level annotation,
 * the annotated class itself, and method-level dispatch tables bound to {@link MethodHandle}s.
 */
public final class HandlerMetadata {

    /** Kinds of component a handler class can be. */
    public enum Kind {
        COMMAND_HANDLER,
        SAGA,
        PROCESS_MANAGER,
        PROJECTOR
    }

    /** Composite key for {@link Rejected} handlers. */
    public record RejectedKey(String domain, String command) {}

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final ClassValue<HandlerMetadata> CACHE =
            new ClassValue<>() {
                @Override
                protected HandlerMetadata computeValue(Class<?> cls) {
                    return extract(cls);
                }
            };

    private final Class<?> handlerClass;
    private final Kind kind;
    private final Annotation kindAnnotation;
    private final Map<Class<?>, MethodHandle> handles;
    private final Map<Class<?>, MethodHandle> applies;
    private final Map<RejectedKey, MethodHandle> rejected;
    private final MethodHandle stateFactory;

    private HandlerMetadata(
            Class<?> handlerClass,
            Kind kind,
            Annotation kindAnnotation,
            Map<Class<?>, MethodHandle> handles,
            Map<Class<?>, MethodHandle> applies,
            Map<RejectedKey, MethodHandle> rejected,
            MethodHandle stateFactory) {
        this.handlerClass = handlerClass;
        this.kind = kind;
        this.kindAnnotation = kindAnnotation;
        this.handles = Map.copyOf(handles);
        this.applies = Map.copyOf(applies);
        this.rejected = Map.copyOf(rejected);
        this.stateFactory = stateFactory;
    }

    /**
     * Return metadata for {@code cls}, computed once and cached per class.
     *
     * @throws IllegalStateException if {@code cls} is invalid (missing or conflicting kind
     *     annotations, conflicting method-role annotations, duplicate {@code @StateFactory}, etc.)
     */
    public static HandlerMetadata of(Class<?> cls) {
        return CACHE.get(cls);
    }

    public Class<?> handlerClass() {
        return handlerClass;
    }

    public Kind kind() {
        return kind;
    }

    /** The kind-specific annotation ({@link Aggregate}, {@link Saga}, etc.). */
    public Annotation kindAnnotation() {
        return kindAnnotation;
    }

    /** Map from command/event class → handler method. */
    public Map<Class<?>, MethodHandle> handles() {
        return handles;
    }

    /** Map from event class → state-applier method. */
    public Map<Class<?>, MethodHandle> applies() {
        return applies;
    }

    /** Map from (source domain, command) → compensation method. */
    public Map<RejectedKey, MethodHandle> rejected() {
        return rejected;
    }

    /** Zero-arg state factory method, if declared with {@code @StateFactory}. */
    public Optional<MethodHandle> stateFactory() {
        return Optional.ofNullable(stateFactory);
    }

    // --- extraction ---

    private static HandlerMetadata extract(Class<?> cls) {
        Annotation kindAnnotation = inferKind(cls);
        Kind kind = kindFor(kindAnnotation);

        Map<Class<?>, MethodHandle> handles = new LinkedHashMap<>();
        Map<Class<?>, MethodHandle> applies = new LinkedHashMap<>();
        Map<RejectedKey, MethodHandle> rejected = new LinkedHashMap<>();
        MethodHandle stateFactory = null;

        for (Method method : cls.getDeclaredMethods()) {
            List<String> roles = new ArrayList<>(2);
            Handles h = method.getAnnotation(Handles.class);
            Applies a = method.getAnnotation(Applies.class);
            Rejected r = method.getAnnotation(Rejected.class);
            StateFactory sf = method.getAnnotation(StateFactory.class);

            if (h != null) roles.add("@Handles");
            if (a != null) roles.add("@Applies");
            if (r != null) roles.add("@Rejected");
            if (sf != null) roles.add("@StateFactory");

            if (roles.size() > 1) {
                throw new IllegalStateException(
                        cls.getSimpleName()
                                + "."
                                + method.getName()
                                + " carries conflicting method-role annotations ("
                                + String.join(", ", roles)
                                + "); a method may declare only one role");
            }
            if (roles.isEmpty()) continue;

            method.setAccessible(true);
            MethodHandle mh;
            try {
                mh = LOOKUP.unreflect(method);
            } catch (IllegalAccessException iae) {
                throw new IllegalStateException(
                        "cannot bind MethodHandle for "
                                + cls.getSimpleName()
                                + "."
                                + method.getName(),
                        iae);
            }

            if (h != null) handles.put(h.value(), mh);
            if (a != null) applies.put(a.value(), mh);
            if (r != null) rejected.put(new RejectedKey(r.domain(), r.command()), mh);
            if (sf != null) {
                if (stateFactory != null) {
                    throw new IllegalStateException(
                            cls.getSimpleName()
                                    + " declares multiple @StateFactory methods; only one is"
                                    + " allowed");
                }
                stateFactory = mh;
            }
        }

        return new HandlerMetadata(cls, kind, kindAnnotation, handles, applies, rejected, stateFactory);
    }

    private static Annotation inferKind(Class<?> cls) {
        List<String> found = new ArrayList<>(2);
        Annotation kindAnnotation = null;

        Aggregate aggregate = cls.getAnnotation(Aggregate.class);
        if (aggregate != null) {
            found.add("@Aggregate");
            kindAnnotation = aggregate;
        }
        Saga saga = cls.getAnnotation(Saga.class);
        if (saga != null) {
            found.add("@Saga");
            kindAnnotation = saga;
        }
        ProcessManager pm = cls.getAnnotation(ProcessManager.class);
        if (pm != null) {
            found.add("@ProcessManager");
            kindAnnotation = pm;
        }
        Projector projector = cls.getAnnotation(Projector.class);
        if (projector != null) {
            found.add("@Projector");
            kindAnnotation = projector;
        }

        if (found.isEmpty()) {
            throw new IllegalStateException(
                    cls.getSimpleName()
                            + " is not an angzarr handler — expected one of @Aggregate, @Saga,"
                            + " @ProcessManager, or @Projector");
        }
        if (found.size() > 1) {
            throw new IllegalStateException(
                    cls.getSimpleName()
                            + " carries multiple kind annotations ("
                            + String.join(", ", found)
                            + "); a handler class may declare only one kind");
        }
        return kindAnnotation;
    }

    private static Kind kindFor(Annotation annotation) {
        if (annotation instanceof Aggregate) return Kind.COMMAND_HANDLER;
        if (annotation instanceof Saga) return Kind.SAGA;
        if (annotation instanceof ProcessManager) return Kind.PROCESS_MANAGER;
        if (annotation instanceof Projector) return Kind.PROJECTOR;
        throw new IllegalStateException("unknown kind annotation: " + annotation);
    }
}
