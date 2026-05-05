package com.moakiee.ae2lt.client;

import java.util.List;

import com.moakiee.ae2lt.menu.FrequencyBindingMenu;
import com.moakiee.ae2lt.network.OpenFrequencyMenuPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class FrequencyBindingClient {
    private static boolean restoreCursor;
    private static double restoreCursorX;
    private static double restoreCursorY;

    private FrequencyBindingClient() {
    }

    public static TextureToggleButton createToolbarButton(FrequencyBindingMenu menu) {
        var button = new TextureToggleButton(
                TextureToggleButton.ButtonType.FREQUENCY_BIND,
                ignored -> {
                    rememberCursorPosition();
                    PacketDistributor.sendToServer(new OpenFrequencyMenuPacket(
                            menu.getFrequencyBindingToken(),
                            menu.getFrequencyBindingBlockPos()));
                });
        button.setTooltipAt(0, List.of(Component.translatable("ae2lt.gui.frequency.bind")));
        return button;
    }

    private static void rememberCursorPosition() {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return;
        }

        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().getWindow(), x, y);
        restoreCursorX = x[0];
        restoreCursorY = y[0];
        restoreCursor = true;
    }

    public static void restoreCursorPositionIfNeeded() {
        if (!restoreCursor) {
            return;
        }

        var mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return;
        }

        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), restoreCursorX, restoreCursorY);
        restoreCursor = false;
    }
}
