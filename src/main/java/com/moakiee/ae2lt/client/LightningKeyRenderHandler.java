package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;

import appeng.client.api.AEKeyRenderer;
import appeng.client.gui.style.Blitter;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.me.key.LightningKey;

public final class LightningKeyRenderHandler implements AEKeyRenderer<LightningKey, LightningKeyRenderHandler.State> {
    public static final LightningKeyRenderHandler INSTANCE = new LightningKeyRenderHandler();

    private static final Identifier HIGH_VOLTAGE_SPRITE =
            Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "item/high_voltage_lightning");
    private static final Identifier EXTREME_HIGH_VOLTAGE_SPRITE =
            Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "item/extreme_high_voltage_lightning");

    private LightningKeyRenderHandler() {
    }

    public static final class State {
    }

    private static TextureAtlasSprite spriteFor(LightningKey stack) {
        Identifier id = stack.tier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                ? EXTREME_HIGH_VOLTAGE_SPRITE
                : HIGH_VOLTAGE_SPRITE;
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphicsExtractor guiGraphics, int x, int y, LightningKey stack) {
        Blitter.sprite(spriteFor(stack))
                .dest(x, y, 16, 16)
                .blit(guiGraphics);
    }

    @Override
    public Class<State> stateClass() {
        return State.class;
    }

    @Override
    public State createState() {
        return new State();
    }

    @Override
    public void extract(State state, LightningKey what, Level level, int seed) {
    }

    @Override
    public void submit(PoseStack poseStack, State state, SubmitNodeCollector nodes, int lightCoords) {
    }

    @Override
    public java.util.List<Component> getTooltip(LightningKey stack) {
        return java.util.List.of(stack.getDisplayName());
    }
}
