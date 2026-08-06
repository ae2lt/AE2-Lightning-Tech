package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.block.LightningSimulationChamberBlock;
import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;

public class LightningSimulationChamberRenderer
        implements BlockEntityRenderer<LightningSimulationChamberBlockEntity> {
    private static final float ITEM_SCALE = 0.35F;
    private static final float ITEM_BASE_HEIGHT = 2.05F / 16.0F;
    private static final float ITEM_LAYER_OFFSET = 0.01F;
    private static final float ITEM_DEPTH = 0.50F;
    private static final float[] INPUT_X_POSITIONS = { 0.34F, 0.50F, 0.66F };

    public LightningSimulationChamberRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LightningSimulationChamberBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var itemRenderer = Minecraft.getInstance().getItemRenderer();
        Direction facing = blockEntity.getBlockState().hasProperty(LightningSimulationChamberBlock.FACING)
                ? blockEntity.getBlockState().getValue(LightningSimulationChamberBlock.FACING)
                : Direction.NORTH;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(facing.toYRot()));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        for (int i = 0; i < INPUT_X_POSITIONS.length; i++) {
            ItemStack stack = blockEntity.getInventory().getStackInSlot(LightningSimulationChamberInventory.SLOT_INPUT_0 + i);
            if (stack.isEmpty()) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(INPUT_X_POSITIONS[i], ITEM_BASE_HEIGHT + ITEM_LAYER_OFFSET * i, ITEM_DEPTH);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            itemRenderer.renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    buffer,
                    blockEntity.getLevel(),
                    0);
            poseStack.popPose();
        }

        poseStack.popPose();
    }
}
