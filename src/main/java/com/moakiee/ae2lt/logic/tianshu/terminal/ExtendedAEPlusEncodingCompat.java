package com.moakiee.ae2lt.logic.tianshu.terminal;

import java.lang.reflect.Method;

/** Limits ExtendedAE Plus' post-encode matrix upload to non-Tianshu encoding terminals. */
public final class ExtendedAEPlusEncodingCompat {
    private static final String ARM_METHOD = "eap$clientSetShiftUpload";
    private static final String CONSUME_METHOD = "eap$consumeShiftUploadFlag";
    private static final Suppression NO_OP = () -> { };

    /**
     * Arms EAEP's own one-shot upload-suppression flag. The returned scope also consumes an
     * unobserved flag when another outer encode Mixin cancels before EAEP reaches its tail hook.
     */
    public static Suppression suppressAutomaticUpload(Object menu) {
        if (menu == null) return NO_OP;
        try {
            Method arm = menu.getClass().getMethod(ARM_METHOD, boolean.class);
            Method consume = menu.getClass().getMethod(CONSUME_METHOD);
            arm.invoke(menu, true);
            return () -> consumeQuietly(consume, menu);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return NO_OP;
        }
    }

    private static void consumeQuietly(Method consume, Object menu) {
        try {
            consume.invoke(menu);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    @FunctionalInterface
    public interface Suppression extends AutoCloseable {
        @Override
        void close();
    }

    private ExtendedAEPlusEncodingCompat() {
    }
}
