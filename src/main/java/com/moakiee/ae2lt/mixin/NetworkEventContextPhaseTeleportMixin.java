package com.moakiee.ae2lt.mixin;

import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Carries player-owned custom-payload teleport authorization onto Forge's main-thread task. */
@Mixin(value = NetworkEvent.Context.class, remap = false)
public abstract class NetworkEventContextPhaseTeleportMixin {
    @Shadow
    @Nullable
    public abstract ServerPlayer getSender();

    @WrapMethod(method = "enqueueWork(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<Void> ae2lt$authorizeEnqueuedPlayerTeleport(
            Runnable task,
            Operation<CompletableFuture<Void>> original) {
        ServerPlayer sender = getSender();
        if (sender == null) {
            return original.call(task);
        }
        return original.call((Runnable) () ->
                PhaseFlightMovementGuard.runAsPlayerPayloadTeleport(sender, task));
    }
}
