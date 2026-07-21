package com.moakiee.ae2lt.mixin;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Propagates the exact sending player into main-thread work enqueued by a serverbound payload. */
@Mixin(ServerPayloadContext.class)
public abstract class ServerPayloadContextPhaseTeleportMixin {
    @ModifyVariable(
            method = "enqueueWork(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            argsOnly = true)
    private Runnable ae2lt$bindPayloadRunnableToSender(Runnable task) {
        var context = (ServerPayloadContext) (Object) this;
        if (!(context.listener() instanceof ServerGamePacketListenerImpl playListener)) {
            return task;
        }
        ServerPlayer sender = playListener.player;
        return () -> PhaseFlightMovementGuard.runAsPlayerPayloadTeleport(sender, task);
    }

    @ModifyVariable(
            method = "enqueueWork(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            argsOnly = true)
    private <T> Supplier<T> ae2lt$bindPayloadSupplierToSender(Supplier<T> task) {
        var context = (ServerPayloadContext) (Object) this;
        if (!(context.listener() instanceof ServerGamePacketListenerImpl playListener)) {
            return task;
        }
        ServerPlayer sender = playListener.player;
        return () -> PhaseFlightMovementGuard.runAsPlayerPayloadTeleport(sender, task);
    }
}
