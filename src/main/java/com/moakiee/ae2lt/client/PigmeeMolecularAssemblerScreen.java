package com.moakiee.ae2lt.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.menu.PigmeeMolecularAssemblerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class PigmeeMolecularAssemblerScreen
        extends AEBaseScreen<PigmeeMolecularAssemblerMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID,
            "textures/gui/pigmee_molecular_assembler.png");
    private static final int CRAFTING_GRID_X = 29;
    private static final int CRAFTING_GRID_Y = 43;
    private static final int CRAFTING_GRID_U = 179;
    private static final int CRAFTING_GRID_V = 2;
    private static final int CRAFTING_GRID_SIZE = 52;
    private static final int PATTERN_SLOT_X = 126;
    private static final int PATTERN_SLOT_Y = 28;
    private static final int PATTERN_SLOT_U = 179;
    private static final int PATTERN_SLOT_V = 57;
    private static final int PATTERN_SLOT_SIZE = 16;
    private static final int TEXTURE_SIZE = 256;

    private final ProgressBar progressBar;

    public PigmeeMolecularAssemblerScreen(
            PigmeeMolecularAssemblerMenu menu,
            Inventory playerInventory,
            Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        progressBar = new ProgressBar(
                menu,
                style.getImage("progressBar"),
                ProgressBar.Direction.VERTICAL);
        widgets.add("progressBar", progressBar);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        progressBar.setFullMsg(Component.literal(menu.getCurrentProgress() + "%"));
    }

    @Override
    public void drawBG(
            GuiGraphics graphics,
            int offsetX,
            int offsetY,
            int mouseX,
            int mouseY,
            float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        graphics.blit(
                TEXTURE,
                offsetX + CRAFTING_GRID_X,
                offsetY + CRAFTING_GRID_Y,
                CRAFTING_GRID_U,
                CRAFTING_GRID_V,
                CRAFTING_GRID_SIZE,
                CRAFTING_GRID_SIZE,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
        graphics.blit(
                TEXTURE,
                offsetX + PATTERN_SLOT_X,
                offsetY + PATTERN_SLOT_Y,
                PATTERN_SLOT_U,
                PATTERN_SLOT_V,
                PATTERN_SLOT_SIZE,
                PATTERN_SLOT_SIZE,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
    }
}
