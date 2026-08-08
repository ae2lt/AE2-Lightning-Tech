package com.moakiee.ae2lt.client.core;

import com.moakiee.ae2lt.block.TianshuSupercomputerControllerBlock;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
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
    private static final CoreEffectPalette CORE_PALETTE =
            new CoreEffectPalette(0.30F, 0.12F, 0.50F, 0.80F, 0.48F, 1.00F);
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
                stack, buffers, CORE_PALETTE, animation.primaryPhase(), animation.secondaryPhase());
        stack.popPose();
    }
}
