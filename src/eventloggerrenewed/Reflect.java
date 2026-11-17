package eventloggerrenewed;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calls a public no-argument method of a game object by name.
 *
 * The game's own screen classes get new scrambled names with every hotfix, so this mod never names
 * them. Starsector refuses java.lang.reflect to mod code when a class names it, so those classes are
 * fetched by name from the boot loader and driven through method handles (the same way Omega Core
 * Everywhere does it). No class of this mod names them.
 */
final class Reflect {

    private static final MethodHandle GET_METHOD;
    private static final MethodHandle INVOKE;
    private static final MethodHandle METHOD_ACCESSIBLE;

    static {
        try {
            ClassLoader boot = Class.class.getClassLoader();
            Class<?> method = Class.forName("java.lang.reflect.Method", false, boot);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            GET_METHOD = lookup.findVirtual(Class.class, "getMethod",
                    MethodType.methodType(method, String.class, Class[].class));
            INVOKE = lookup.findVirtual(method, "invoke",
                    MethodType.methodType(Object.class, Object.class, Object[].class));
            METHOD_ACCESSIBLE = lookup.findVirtual(method, "setAccessible",
                    MethodType.methodType(void.class, boolean.class));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Event Logger - Renewed cannot read the game's screens", e);
        }
    }

    private static final Object MISSING = new Object();
    /** Per class and name: the method, or MISSING. Looked up once, since this runs every frame. */
    private static final Map<Class<?>, Map<String, Object>> METHODS = new ConcurrentHashMap<>();

    private Reflect() {}

    /** True when the object has a public method without arguments by that name. */
    static boolean has(Object target, String name) {
        return target != null && method(target.getClass(), name) != MISSING;
    }

    /** Calls a public method without arguments, or returns null if the object has none by that name. */
    static Object call(Object target, String name) {
        if (target == null) return null;
        Object method = method(target.getClass(), name);
        if (method == MISSING) return null;
        try {
            return INVOKE.invoke(method, target, new Object[0]);
        } catch (Throwable t) {
            throw new IllegalStateException("Calling " + name + " failed", t);
        }
    }

    private static Object method(Class<?> type, String name) {
        return METHODS.computeIfAbsent(type, c -> new ConcurrentHashMap<>())
                .computeIfAbsent(name, n -> find(type, n));
    }

    private static Object find(Class<?> type, String name) {
        try {
            Object method = GET_METHOD.invoke(type, name, new Class<?>[0]);
            METHOD_ACCESSIBLE.invoke(method, true);
            return method;
        } catch (Throwable t) {
            // NoSuchMethodException, or a class the game will not let us open: treat as absent.
            return MISSING;
        }
    }
}
