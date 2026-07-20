package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Excludes only the phase-flying placer from the normal block obstruction check. */
@Mixin(BlockItem.class)
public abstract class BlockItemPhasePlacementMixin {
    @Shadow
    protected abstract boolean mustSurvive();

    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void ae2lt$ignorePhaseFlyingPlacers(
            BlockPlaceContext context,
            BlockState state,
            CallbackInfoReturnable<Boolean> cir) {
        Player player = context.getPlayer();
        if (!PhaseFlightMovementGuard.isPhaseFlightActive(player)) {
            return;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (this.mustSurvive() && !state.canSurvive(level, pos)) {
            cir.setReturnValue(false);
            return;
        }

        VoxelShape localShape = state.getCollisionShape(level, pos, CollisionContext.of(player));
        cir.setReturnValue(localShape.isEmpty() || level.isUnobstructed(
                player,
                localShape.move(pos.getX(), pos.getY(), pos.getZ())));
    }
}
