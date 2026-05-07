package com.moakiee.ae2lt.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * Shared reflection helpers for {@link org.spongepowered.asm.mixin.Pseudo} mixins
 * that target optional dependencies (AdvancedAE, NeoECOAEExtension, ...).
 * <p>
 * Lookup helpers return {@code null} on failure instead of throwing, so static
 * field initializers in mixin classes never crash class loading. Invocation
 * helpers swallow {@link ReflectiveOperationException} and log a warning at
 * most once per {@code action} key, mirroring the existing {@code ae2lt$*}
 * pattern that lived inside {@code AdvCraftingCpuLogicMixin}.
 */
public final class MixinReflectionSupport {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> LOGGED_FAILURES =
            Collections.synchronizedSet(new HashSet<>());

    private MixinReflectionSupport() {
    }

    @Nullable
    public static Class<?> findClassSafe(String fqn) {
        try {
            return Class.forName(fqn);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    public static Field findDeclaredFieldSafe(@Nullable Class<?> owner, String name) {
        if (owner == null) return null;
        try {
            var field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    public static Method findDeclaredMethodSafe(@Nullable Class<?> owner, String name,
                                                Class<?>... parameterTypes) {
        if (owner == null) return null;
        try {
            var method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    public static Object getFieldValueSafe(@Nullable Field field, Object target) {
        if (field == null) return null;
        try {
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            logReflectionFailure("read reflective field", e);
            return null;
        }
    }

    public static long getLongFieldSafe(@Nullable Field field, Object target, long fallback) {
        if (field == null) return fallback;
        try {
            return field.getLong(target);
        } catch (ReflectiveOperationException e) {
            logReflectionFailure("read reflective long field", e);
            return fallback;
        }
    }

    public static void setLongFieldSafe(@Nullable Field field, Object target, long value, String action) {
        if (field == null) return;
        try {
            field.setLong(target, value);
        } catch (ReflectiveOperationException e) {
            logReflectionFailure(action, e);
        }
    }

    @Nullable
    public static Object invokeMethodSafe(@Nullable Method method, Object target, String action,
                                          Object... args) {
        if (method == null) return null;
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            logReflectionFailure(action, e);
            return null;
        }
    }

    public static void logReflectionFailure(String action, Throwable exception) {
        if (LOGGED_FAILURES.add(action)) {
            LOGGER.warn("AE2LT reflection failed while trying to {}.", action, exception);
        }
    }
}
