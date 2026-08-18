package com.moakiee.ae2lt.integration.ae2wtlib;

import java.lang.reflect.InvocationTargetException;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.loading.FMLLoader;

/**
 * Creates the registered wireless terminal without linking optional AE2WTLib classes when the
 * implementation mod is absent.
 */
public final class TianshuWirelessTerminalFactory {
    private static final String INTEGRATION_CLASS =
            "com.moakiee.ae2lt.integration.ae2wtlib.Ae2wtlibIntegration";

    private TianshuWirelessTerminalFactory() {
    }

    public static boolean isAvailable() {
        return FMLLoader.getLoadingModList().getModFileById("ae2wtlib") != null;
    }

    public static Item create() {
        if (!isAvailable()) {
            throw new IllegalStateException(
                    "The wireless Tianshu terminal must not be registered without AE2WTLib");
        }

        try {
            Class<?> integration = Class.forName(
                    INTEGRATION_CLASS, true, TianshuWirelessTerminalFactory.class.getClassLoader());
            return Item.class.cast(integration.getMethod("terminal").invoke(null));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException(
                    "AE2WTLib is loaded, but its Tianshu terminal integration is unavailable",
                    exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Failed to create the AE2WTLib Tianshu terminal", cause);
        }
    }
}
