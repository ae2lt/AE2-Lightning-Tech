package com.moakiee.ae2lt.client.core;

import com.moakiee.ae2lt.block.MatrixControllerBlock;
import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanner;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockTemplate;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

public final class MatrixCoreEffectRenderer implements BlockEntityRenderer<MatrixControllerBlockEntity> {
    private static final CoreEffectAnimationState.MotionProfile MOTION =
            new CoreEffectAnimationState.MotionProfile(
                    8.0D, 48.0D, 36_000.0D,
                    22.0D, 84.0D, 36_000.0D,
                    1.2D, 4.2D);

    private final Map<MatrixControllerBlockEntity, CoreEffectAnimationState> animations =
            new WeakHashMap<>();

    public MatrixCoreEffectRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MatrixControllerBlockEntity controller, float partialTick,
                       PoseStack stack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var state = controller.getBlockState();
        if (!AE2LTClientConfig.renderMultiblockCoreEffects()
                || !state.hasProperty(MatrixControllerBlock.FORMED)
                || !state.getValue(MatrixControllerBlock.FORMED)
                || controller.getLevel() == null) {
            return;
        }

        Direction facing = controller.getOrientation();
        BlockPos center = MatrixMultiblockScanner.worldPos(
                controller.getBlockPos(), MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL, facing);
        var component = MatrixMultiblockScanner.componentAt(controller.getLevel(), center);
        CoreEffectPalette palette = palette(component);
        boolean working = state.hasProperty(MatrixControllerBlock.WORKING)
                && state.getValue(MatrixControllerBlock.WORKING);
        double renderTick = controller.getLevel().getGameTime() + (double) partialTick;
        var animation = animations.computeIfAbsent(
                controller, ignored -> new CoreEffectAnimationState()).sample(renderTick, working, MOTION);

        stack.pushPose();
        stack.translate(
                center.getX() + 0.5D - controller.getBlockPos().getX(),
                center.getY() + 0.5D - controller.getBlockPos().getY(),
                center.getZ() + 0.5D - controller.getBlockPos().getZ());
        CoreEffectGeometry.renderMatrix(stack, buffers, palette, animation);
        stack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(MatrixControllerBlockEntity controller) {
        BlockPos center = MatrixMultiblockScanner.worldPos(
                controller.getBlockPos(),
                MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL,
                controller.getOrientation());
        return new AABB(center).inflate(3.0D).minmax(new AABB(controller.getBlockPos()));
    }

    private static CoreEffectPalette palette(MatrixMultiblockComponent component) {
        return switch (component) {
            case QUANTUM_MAIN_CORE -> new CoreEffectPalette(0.22F, 0.62F, 0.78F, 0.58F, 0.86F, 0.92F);
            case OVERLOAD_MAIN_CORE -> new CoreEffectPalette(0.78F, 0.26F, 0.10F, 0.96F, 0.60F, 0.22F);
            case MULTIDIMENSIONAL_MAIN_CORE -> new CoreEffectPalette(0.54F, 0.20F, 0.66F, 0.18F, 0.70F, 0.62F);
            default -> new CoreEffectPalette(0.36F, 0.58F, 0.70F, 0.72F, 0.82F, 0.86F);
        };
    }
}
