package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;

import appeng.api.client.AEKeyRenderHandler;
import appeng.client.gui.style.Blitter;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.me.key.LightningKey;

public final class LightningKeyRenderHandler implements AEKeyRenderHandler<LightningKey> {
    public static final LightningKeyRenderHandler INSTANCE = new LightningKeyRenderHandler();

    private static final ResourceLocation HIGH_VOLTAGE_SPRITE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "item/high_voltage_lightning");
    private static final ResourceLocation EXTREME_HIGH_VOLTAGE_SPRITE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "item/extreme_high_voltage_lightning");

    private LightningKeyRenderHandler() {
    }

    private static TextureAtlasSprite spriteFor(LightningKey stack) {
        ResourceLocation id = stack.tier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                ? EXTREME_HIGH_VOLTAGE_SPRITE
                : HIGH_VOLTAGE_SPRITE;
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, LightningKey stack) {
        Blitter.sprite(spriteFor(stack))
                .dest(x, y, 16, 16)
                .blit(guiGraphics);
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, LightningKey what, float scale,
            int combinedLight, Level level) {
        var sprite = spriteFor(what);

        poseStack.pushPose();
        poseStack.translate(0, 0, 0.01f);

        var buffer = buffers.getBuffer(RenderType.cutout());

        // Match FluidKeyRenderHandler: shrink slightly since sprites typically fill the full texel.
        scale -= 0.05f;
        float x0 = -scale / 2f;
        float y0 = scale / 2f;
        float x1 = scale / 2f;
        float y1 = -scale / 2f;

        var transform = poseStack.last().pose();
        buffer.addVertex(transform, x0, y1, 0)
                .setColor(0xFFFFFFFF)
                .setUv(sprite.getU0(), sprite.getV1())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(combinedLight)
                .setNormal(0, 0, 1);
        buffer.addVertex(transform, x1, y1, 0)
                .setColor(0xFFFFFFFF)
                .setUv(sprite.getU1(), sprite.getV1())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(combinedLight)
                .setNormal(0, 0, 1);
        buffer.addVertex(transform, x1, y0, 0)
                .setColor(0xFFFFFFFF)
                .setUv(sprite.getU1(), sprite.getV0())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(combinedLight)
                .setNormal(0, 0, 1);
        buffer.addVertex(transform, x0, y0, 0)
                .setColor(0xFFFFFFFF)
                .setUv(sprite.getU0(), sprite.getV0())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(combinedLight)
                .setNormal(0, 0, 1);

        poseStack.popPose();
    }

    @Override
    public Component getDisplayName(LightningKey stack) {
        return stack.getDisplayName();
    }
}
