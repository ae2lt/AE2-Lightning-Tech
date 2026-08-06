package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.client.render.overlay.OverlayRenderType;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;

public class WirelessConnectorHostRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    // Host inner cube color (unselected): semi-transparent blue (ARGB)
    private static final int COLOR_HOST = 0x800080FF;
    // Host inner cube color (selected): semi-transparent yellow (ARGB)
    private static final int COLOR_HOST_SELECTED = 0x80FFFF00;

    public WirelessConnectorHostRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        String hostType = getRenderableHostType(blockEntity);
        if (hostType == null) {
            return;
        }

        var stack = WirelessConnectorRenderer.getHeldConnectorStack();
        if (stack.isEmpty()) {
            return;
        }

        boolean selected = WirelessConnectorRenderer.isSelectedHost(
                stack, level, blockEntity.getBlockPos(), hostType);
        renderInnerCube(poseStack, buffer, selected ? COLOR_HOST_SELECTED : COLOR_HOST);
    }

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return true;
    }

    private static String getRenderableHostType(BlockEntity blockEntity) {
        if (blockEntity instanceof OverloadedPatternProviderBlockEntity provider
                && provider.getProviderMode() != OverloadedPatternProviderBlockEntity.ProviderMode.NORMAL) {
            return OverloadedWirelessConnectorItem.HOST_PROVIDER;
        }
        if (blockEntity instanceof OverloadedInterfaceBlockEntity iface
                && iface.getInterfaceMode() == OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS) {
            return OverloadedWirelessConnectorItem.HOST_INTERFACE;
        }
        if (blockEntity instanceof OverloadedPowerSupplyBlockEntity) {
            return OverloadedWirelessConnectorItem.HOST_POWER_SUPPLY;
        }
        return null;
    }

    /**
     * Render the same small glowing cube used by the NeoForge branch, but in the
     * block entity's local render coordinates so it stays anchored to the block.
     */
    private static void renderInnerCube(PoseStack poseStack, MultiBufferSource buffer, int color) {
        VertexConsumer vc = buffer.getBuffer(Ae2ltRenderTypes.getFaceSeeThrough());
        int[] c = OverlayRenderType.decomposeColor(color);

        poseStack.pushPose();
        Matrix4f mat = poseStack.last().pose();

        float lo = 0.25f, hi = 0.75f;

        // DOWN (Y-)
        quad(vc, mat, c, lo, lo, lo, hi, lo, lo, hi, lo, hi, lo, lo, hi, 0, -1, 0);
        // UP (Y+)
        quad(vc, mat, c, lo, hi, hi, hi, hi, hi, hi, hi, lo, lo, hi, lo, 0, 1, 0);
        // NORTH (Z-)
        quad(vc, mat, c, lo, lo, lo, lo, hi, lo, hi, hi, lo, hi, lo, lo, 0, 0, -1);
        // SOUTH (Z+)
        quad(vc, mat, c, hi, lo, hi, hi, hi, hi, lo, hi, hi, lo, lo, hi, 0, 0, 1);
        // WEST (X-)
        quad(vc, mat, c, lo, lo, hi, lo, hi, hi, lo, hi, lo, lo, lo, lo, -1, 0, 0);
        // EAST (X+)
        quad(vc, mat, c, hi, lo, lo, hi, hi, lo, hi, hi, hi, hi, lo, hi, 1, 0, 0);

        poseStack.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, int[] c,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float nx, float ny, float nz) {
        vc.vertex(mat, x1, y1, z1).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz).endVertex();
        vc.vertex(mat, x2, y2, z2).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz).endVertex();
        vc.vertex(mat, x3, y3, z3).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz).endVertex();
        vc.vertex(mat, x4, y4, z4).color(c[1], c[2], c[3], c[0]).normal(nx, ny, nz).endVertex();
    }
}
