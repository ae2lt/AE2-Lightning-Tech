package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.ITeleporter;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/**
 * Stops dimension transitions before the player is removed from the source level.
 * Forge 1.20.1 replaced the 1.21 {@code changeDimension(DimensionTransition)} entry
 * with {@code changeDimension(ServerLevel, ITeleporter)}; returning null is already
 * the native cancel path (ForgeHooks.onTravelToDimension).
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPhaseMovementMixin {
    @Inject(
            method = "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"),
            cancellable = true,
            // ITeleporter is a Forge-patched parameter type with no SRG mapping; keep the name literal.
            remap = false)
    private void ae2lt$blockExternalPhaseDimensionChange(
            ServerLevel destination,
            ITeleporter teleporter,
            CallbackInfoReturnable<Entity> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (PhaseFlightMovementGuard.blocksExternalTeleports(player)
                && !PhaseFlightMovementGuard.isSelfTeleportAuthorized(player)) {
            PhaseFlightMovementGuard.notifyBlockedDimensionTeleport(
                    player,
                    destination,
                    player.position());
            cir.setReturnValue(null);
        }
    }
}
