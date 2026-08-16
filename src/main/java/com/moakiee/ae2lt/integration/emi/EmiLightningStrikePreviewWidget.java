package com.moakiee.ae2lt.integration.emi;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** EMI counterpart of the JEI lightning-strike isometric preview widget. */
final class EmiLightningStrikePreviewWidget extends Widget {
    private static final float X_ROTATION_DEG = 30.0F;
    private static final float SCALE_MARGIN = 0.92F;
    private static final float ROTATION_DEGREES_PER_MS = 0.024F;

    private final Bounds bounds;
    private final List<Entry> blocks;
    private final float centerX;
    private final float centerY;
    private final float centerZ;
    private final float scale;

    private EmiLightningStrikePreviewWidget(int x, int y, int width, int height, List<Entry> blocks) {
        this.bounds = new Bounds(x, y, width, height);
        this.blocks = blocks;

        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (Entry entry : blocks) {
            minX = Math.min(minX, entry.offset.getX() - 0.5F);
            maxX = Math.max(maxX, entry.offset.getX() + 0.5F);
            minY = Math.min(minY, entry.offset.getY() - 0.5F);
            maxY = Math.max(maxY, entry.offset.getY() + 0.5F);
            minZ = Math.min(minZ, entry.offset.getZ() - 0.5F);
            maxZ = Math.max(maxZ, entry.offset.getZ() + 0.5F);
        }
        centerX = (minX + maxX) * 0.5F;
        centerY = (minY + maxY) * 0.5F;
        centerZ = (minZ + maxZ) * 0.5F;

        float xRotCos = (float) Math.cos(Math.toRadians(X_ROTATION_DEG));
        float xRotSin = (float) Math.sin(Math.toRadians(X_ROTATION_DEG));
        float horizontalRadius = 0F;
        float verticalRadius = 0F;
        for (float xv : new float[] {minX - centerX, maxX - centerX}) {
            for (float yv : new float[] {minY - centerY, maxY - centerY}) {
                for (float zv : new float[] {minZ - centerZ, maxZ - centerZ}) {
                    float horizontal = (float) Math.hypot(xv, zv);
                    horizontalRadius = Math.max(horizontalRadius, horizontal);
                    verticalRadius = Math.max(
                            verticalRadius,
                            Math.abs(yv) * xRotCos + horizontal * xRotSin);
                }
            }
        }
        scale = horizontalRadius <= 0F || verticalRadius <= 0F
                ? 0F
                : Math.min(
                                width * 0.5F / horizontalRadius,
                                height * 0.5F / verticalRadius)
                        * SCALE_MARGIN;
    }

    @Override
    public Bounds getBounds() {
        return bounds;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (blocks.isEmpty() || scale <= 0F) {
            return;
        }
        var client = Minecraft.getInstance();
        var bufferSource = client.renderBuffers().bufferSource();
        var blockRenderer = client.getBlockRenderer();
        PoseStack pose = graphics.pose();
        float rotation = (Util.getMillis() * ROTATION_DEGREES_PER_MS) % 360F;

        for (Entry entry : blocks) {
            pose.pushPose();
            pose.translate(
                    bounds.x() + bounds.width() / 2F,
                    bounds.y() + bounds.height() / 2F,
                    400);
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(X_ROTATION_DEG));
            pose.mulPose(Axis.YP.rotationDegrees(225 + rotation));
            pose.translate(
                    -0.5F + entry.offset.getX() - centerX,
                    -0.5F + entry.offset.getY() - centerY,
                    -0.5F + entry.offset.getZ() - centerZ);
            RenderSystem.runAsFancy(() -> {
                if (entry.state.getRenderShape() != RenderShape.ENTITYBLOCK_ANIMATED) {
                    blockRenderer.renderSingleBlock(
                            entry.state,
                            pose,
                            bufferSource,
                            LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY);
                }
            });
            pose.popPose();
        }
        if (bufferSource instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
        Lighting.setupFor3DItems();
    }

    private record Entry(BlockState state, BlockPos offset) {
    }

    static final class Builder {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final List<Entry> blocks = new ArrayList<>();

        Builder(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        Builder addBlock(Block block, BlockPos offset) {
            if (block != null) {
                blocks.add(new Entry(block.defaultBlockState(), offset));
            }
            return this;
        }

        EmiLightningStrikePreviewWidget build() {
            return new EmiLightningStrikePreviewWidget(x, y, width, height, List.copyOf(blocks));
        }
    }
}
