package com.moakiee.ae2lt.client;

import appeng.client.render.crafting.AssemblerAnimationStatus;
import appeng.client.render.effects.ParticleTypes;
import appeng.core.AppEngClient;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.PigmeeMolecularAssemblerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class PigmeeMolecularAssemblerRenderer
        implements BlockEntityRenderer<PigmeeMolecularAssemblerBlockEntity> {
    public static final ModelResourceLocation LIGHTS_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID,
                    "block/pigmee_molecular_assembler_lights"));

    private final RandomSource particleRandom = RandomSource.create();

    public PigmeeMolecularAssemblerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            PigmeeMolecularAssemblerBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        var animationStatus = blockEntity.getAnimationStatus();
        if (animationStatus != null) {
            if (!Minecraft.getInstance().isPaused()) {
                if (animationStatus.isExpired()) {
                    blockEntity.setAnimationStatus(null);
                }
                animationStatus.setAccumulatedTicks(
                        animationStatus.getAccumulatedTicks() + partialTick);
                animationStatus.setTicksUntilParticles(
                        animationStatus.getTicksUntilParticles() - partialTick);
            }
            renderAnimation(
                    blockEntity,
                    poseStack,
                    buffer,
                    packedLight,
                    animationStatus);
        }

        if (blockEntity.isPowered()) {
            renderPowerLight(poseStack, buffer, packedLight, packedOverlay);
        }
    }

    private void renderPowerLight(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel lightsModel = minecraft.getModelManager().getModel(LIGHTS_MODEL);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.tripwire());
        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                buffer,
                null,
                lightsModel,
                1.0F,
                1.0F,
                1.0F,
                packedLight,
                packedOverlay,
                ModelData.EMPTY,
                null);
    }

    private void renderAnimation(
            PigmeeMolecularAssemblerBlockEntity blockEntity,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AssemblerAnimationStatus status) {
        double centerX = blockEntity.getBlockPos().getX() + 0.5D;
        double centerY = blockEntity.getBlockPos().getY() + 0.5D;
        double centerZ = blockEntity.getBlockPos().getZ() + 0.5D;

        Minecraft minecraft = Minecraft.getInstance();
        if (status.getTicksUntilParticles() <= 0) {
            status.setTicksUntilParticles(4);
            if (AppEngClient.instance().shouldAddParticles(particleRandom)) {
                for (int i = 0; i < (int) Math.ceil(status.getSpeed() / 5.0D); i++) {
                    minecraft.particleEngine.createParticle(
                            ParticleTypes.CRAFTING,
                            centerX,
                            centerY,
                            centerZ,
                            0.0D,
                            0.0D,
                            0.0D);
                }
            }
        }

        ItemStack stack = status.getIs();
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.translate(0.0D, stack.getItem() instanceof BlockItem ? -0.2F : -0.3F, 0.0D);
        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.GROUND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                blockEntity.getLevel(),
                0);
        poseStack.popPose();
    }
}
