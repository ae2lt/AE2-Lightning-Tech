package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.handling.MainThreadPayloadHandler;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Binds the sending player only while NeoForge invokes a main-thread payload handler. */
@Mixin(MainThreadPayloadHandler.class)
public abstract class MainThreadPayloadHandlerPhaseTeleportMixin {
    @WrapOperation(
            method = "lambda$handle$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/network/handling/IPayloadHandler;"
                            + "handle(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;"
                            + "Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V"))
    private void ae2lt$bindPayloadHandlerToSender(
            IPayloadHandler<CustomPacketPayload> handler,
            CustomPacketPayload payload,
            IPayloadContext context,
            Operation<Void> original) {
        if (context instanceof ServerPayloadContext serverContext
                && serverContext.listener() instanceof ServerGamePacketListenerImpl playListener) {
            PhaseFlightMovementGuard.runAsPlayerPayloadHandler(
                    playListener.player,
                    () -> original.call(handler, payload, context));
            return;
        }
        original.call(handler, payload, context);
    }
}
