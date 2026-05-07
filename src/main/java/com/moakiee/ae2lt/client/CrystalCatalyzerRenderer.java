package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;

public class CrystalCatalyzerRenderer implements BlockEntityRenderer<CrystalCatalyzerBlockEntity> {
    private static final double CAVITY_CENTER_Y = 8.0D / 16.0D;
    private static final float ITEM_SCALE = 0.50F;
    private static final float WORKING_ROTATION_SPEED = 4.0F;

    public CrystalCatalyzerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CrystalCatalyzerBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ItemStack stack = getDisplayStack(blockEntity);
        if (stack.isEmpty()) {
            return;
        }

        var level = blockEntity.getLevel();
        var itemRenderer = Minecraft.getInstance().getItemRenderer();

        poseStack.pushPose();
        poseStack.translate(0.5D, CAVITY_CENTER_Y, 0.5D);

        if (level != null && blockEntity.isWorking()) {
            float rotation = (level.getGameTime() + partialTick) * WORKING_ROTATION_SPEED;
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        }

        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);

        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                level,
                0);
        poseStack.popPose();
    }

    private static ItemStack getDisplayStack(CrystalCatalyzerBlockEntity blockEntity) {
        return blockEntity.getInventory().getStackInSlot(CrystalCatalyzerInventory.SLOT_CATALYST);
    }
}
