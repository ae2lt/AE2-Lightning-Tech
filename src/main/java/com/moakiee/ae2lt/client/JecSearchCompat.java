package com.moakiee.ae2lt.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Optional bridge to Just Enough Characters without making it a required dependency. */
final class JecSearchCompat {
    private static final String MATCH_CLASS = "me.towdium.jecharacters.utils.Match";
    private static volatile MethodHandle containsMethod = findContainsMethod();

    private JecSearchCompat() {
    }

    static boolean contains(String text, String query) {
        MethodHandle method = containsMethod;
        if (method == null || text == null || query == null || query.isBlank()) {
            return false;
        }
        try {
            return (boolean) method.invokeExact(text, (CharSequence) query);
        } catch (Throwable ignored) {
            // A mismatched or broken JEC version must never break this screen's normal search.
            containsMethod = null;
            return false;
        }
    }

    private static MethodHandle findContainsMethod() {
        try {
            Class<?> matchClass = Class.forName(MATCH_CLASS);
            return MethodHandles.publicLookup().findStatic(
                    matchClass,
                    "contains",
                    MethodType.methodType(boolean.class, String.class, CharSequence.class));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
