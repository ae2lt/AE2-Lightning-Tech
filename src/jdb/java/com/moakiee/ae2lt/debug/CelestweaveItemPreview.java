package com.moakiee.ae2lt.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Live item-renderer preview; only compiled in the development source set. */
public final class CelestweaveItemPreview extends Screen {
    private static final String[] IDS = {"celestweave_oculus", "celestweave_core",
            "celestweave_conduit", "celestweave_stride", "firmament_spirit_core_oculus",
            "firmament_spirit_core_core", "firmament_spirit_core_conduit",
            "firmament_spirit_core_stride", "inactive_firmament_spirit_core"};
    private CelestweaveItemPreview() { super(Component.literal("苍穹织雷 · 物品贴图")); }
    public static String show() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null || !mc.getSingleplayerServer().getWorldData()
                .getLevelName().equals("Celestweave-Field-Preview")) return "Not in preview world";
        mc.setScreen(new CelestweaveItemPreview());
        return "Showing nine actual registered item models";
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xff202430);
        g.drawCenteredString(font, title, width / 2, 16, 0xe1eaff);
        int cell = Math.min(100, (width - 20) / 5);
        int left = (width - cell * 5) / 2;
        for (int i = 0; i < IDS.length; i++) {
            int column = i < 4 ? i : i - 4;
            int x = left + column * cell;
            int y = i < 4 ? 42 : height / 2 + 8;
            g.fill(x + 2, y, x + cell - 2, y + 75, i < 4 ? 0xff999da6 : 0xff414653);
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2lt", IDS[i])));
            g.pose().pushPose();
            g.pose().translate(x + (cell - 64) / 2.0f, y + 5, 0);
            g.pose().scale(4, 4, 1);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
            g.drawCenteredString(font, stack.getHoverName(), x + cell / 2, y + 81, 0xe1eaff);
        }
        g.drawCenteredString(font, Component.literal("32×32 · 实际物品渲染 / ESC 返回"), width / 2, height - 15, 0xaab7cf);
    }
    @Override public boolean isPauseScreen() { return false; }
}
