package com.moakiee.ae2lt.client;

import java.util.List;

import com.moakiee.ae2lt.menu.FrequencyBindingMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import com.moakiee.ae2lt.network.OpenFrequencyMenuPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class FrequencyBindingClient {
    /** Drop stale cursor restores if the server rejects or delays opening the requested screen. */
    private static final long RESTORE_TIMEOUT_MS = 10000L;

    private static boolean restoreCursor;
    private static BlockPos restoreCursorBlockPos;
    private static long restoreCursorAtMs;
    private static double restoreCursorX;
    private static double restoreCursorY;

    private FrequencyBindingClient() {
    }

    public static TextureToggleButton createToolbarButton(FrequencyBindingMenu menu) {
        var button = new TextureToggleButton(
                TextureToggleButton.ButtonType.FREQUENCY_BIND,
                ignored -> {
                    rememberCursorPosition(menu.getFrequencyBindingBlockPos());
                    NetworkInit.sendToServer(new OpenFrequencyMenuPacket(
                            menu.getFrequencyBindingToken(),
                            menu.getFrequencyBindingBlockPos()));
                });
        button.setTooltipAt(0, List.of(Component.translatable("ae2lt.gui.frequency.bind")));
        return button;
    }

    private static void rememberCursorPosition(BlockPos blockPos) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(window, x, y);
        restoreCursorX = x[0];
        restoreCursorY = y[0];
        restoreCursorBlockPos = blockPos.immutable();
        restoreCursorAtMs = System.currentTimeMillis();
        restoreCursor = true;
    }

    public static void restoreCursorPositionIfNeeded(BlockPos blockPos) {
        if (!restoreCursor) {
            return;
        }

        if (!blockPos.equals(restoreCursorBlockPos)
                || System.currentTimeMillis() - restoreCursorAtMs > RESTORE_TIMEOUT_MS) {
            clearRestoreState();
            return;
        }

        long window = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetCursorPos(window, restoreCursorX, restoreCursorY);
        clearRestoreState();
    }

    private static void clearRestoreState() {
        restoreCursor = false;
        restoreCursorBlockPos = null;
    }
}

