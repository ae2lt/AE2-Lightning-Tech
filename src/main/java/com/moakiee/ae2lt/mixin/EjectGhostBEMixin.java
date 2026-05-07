package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.moakiee.ae2lt.logic.EjectModeRegistry;

/**
 * Mixin into {@link Level#getBlockEntity} to return a cached Ghost BE
 * at eject-mode interception positions when the original block is air (null).
 * <p>
 * This satisfies machines that check {@code getBlockEntity(adjacent) != null}
 * before querying capabilities. Returns the GhostBE regardless of whether
 * the owning pattern provider is currently loaded, so that the capability
 * mixin can then return a rejecting handler when the provider is offline.
 */
@Mixin(Level.class)
public abstract class EjectGhostBEMixin {

    @Inject(method = "getBlockEntity", at = @At("RETURN"), cancellable = true)
    private void ae2lt$injectGhostBE(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (cir.getReturnValue() != null) return;
        if (!((Object) this instanceof ServerLevel)) return;

        var entry = EjectModeRegistry.lookupAny(
                ((Level) (Object) this).dimension(), pos.asLong());
        if (entry != null) {
            var ghost = entry.ghostBE();
            if (ghost.getLevel() == null) {
                ghost.setLevel((Level) (Object) this);
            }
            cir.setReturnValue(ghost);
        }
    }
}
