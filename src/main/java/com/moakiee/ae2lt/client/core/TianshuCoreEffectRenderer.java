package com.moakiee.ae2lt.client.core;

import com.moakiee.ae2lt.block.TianshuSupercomputerControllerBlock;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockComponent;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanner;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

public final class TianshuCoreEffectRenderer
        implements BlockEntityRenderer<TianshuSupercomputerControllerBlockEntity> {
    private static final BlockPos CORE_LOCAL = new BlockPos(3, 3, 3);
    private static final CoreEffectAnimationState.MotionProfile MOTION =
            new CoreEffectAnimationState.MotionProfile(
                    1.0D / 5.5D, 1.0D / 0.72D, 18.0D,
                    3.0D, 30.0D, 360.0D,
                    0.0D, 0.0D);

    private final Map<TianshuSupercomputerControllerBlockEntity, CoreEffectAnimationState> animations =
            new WeakHashMap<>();

    public TianshuCoreEffectRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TianshuSupercomputerControllerBlockEntity controller, float partialTick,
                       PoseStack stack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var state = controller.getBlockState();
        if (!AE2LTClientConfig.useCoreShaderRendering()
                || !AE2LTClientConfig.renderMultiblockCoreEffects()
                || !state.hasProperty(TianshuSupercomputerControllerBlock.FORMED)
                || !state.getValue(TianshuSupercomputerControllerBlock.FORMED)
                || controller.getLevel() == null) {
            return;
        }

        Direction facing = state.getValue(TianshuSupercomputerControllerBlock.FACING);
        BlockPos center = TianshuMultiblockScanner.worldPos(controller.getBlockPos(), CORE_LOCAL, facing);
        var component = TianshuMultiblockScanner.componentAt(controller.getLevel(), center);
        CoreEffectPalette palette = palette(component);
        boolean working = state.hasProperty(TianshuSupercomputerControllerBlock.WORKING)
                && state.getValue(TianshuSupercomputerControllerBlock.WORKING);
        double renderTick = controller.getLevel().getGameTime() + (double) partialTick;
        var animation = animations.computeIfAbsent(
                controller, ignored -> new CoreEffectAnimationState()).sample(renderTick, working, MOTION);

        stack.pushPose();
        stack.translate(
                center.getX() + 0.5D - controller.getBlockPos().getX(),
                center.getY() + 0.5D - controller.getBlockPos().getY(),
                center.getZ() + 0.5D - controller.getBlockPos().getZ());
        CoreEffectGeometry.renderTianshu(
                stack, buffers, palette, animation.primaryPhase(), animation.secondaryPhase());
        stack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(TianshuSupercomputerControllerBlockEntity controller) {
        Direction facing = controller.getBlockState().getValue(TianshuSupercomputerControllerBlock.FACING);
        BlockPos center = TianshuMultiblockScanner.worldPos(controller.getBlockPos(), CORE_LOCAL, facing);
        return new AABB(center).inflate(2.5D).minmax(new AABB(controller.getBlockPos()));
    }

    private static CoreEffectPalette palette(TianshuMultiblockComponent component) {
        // Dark body (primary) + bright glowing corners (accent), one hue per tier
        return switch (component) {
            case MAIN_QUANTUM -> new CoreEffectPalette(0.12F, 0.22F, 0.52F, 0.46F, 0.72F, 1.00F);
            case MAIN_OVERLOAD -> new CoreEffectPalette(0.52F, 0.14F, 0.06F, 1.00F, 0.62F, 0.20F);
            case MAIN_MULTIDIMENSIONAL -> new CoreEffectPalette(0.30F, 0.12F, 0.50F, 0.80F, 0.48F, 1.00F);
            default -> new CoreEffectPalette(0.10F, 0.34F, 0.40F, 0.42F, 0.96F, 1.00F);
        };
    }
}
