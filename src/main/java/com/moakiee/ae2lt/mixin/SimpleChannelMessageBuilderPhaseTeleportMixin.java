package com.moakiee.ae2lt.mixin;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Binds the sending player only while Forge invokes a SimpleChannel main-thread consumer. */
@Mixin(value = SimpleChannel.MessageBuilder.class, remap = false)
public abstract class SimpleChannelMessageBuilderPhaseTeleportMixin {
    @WrapOperation(
            method = "lambda$consumerMainThread$1",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/BiConsumer;accept(Ljava/lang/Object;Ljava/lang/Object;)V"))
    private static void ae2lt$bindPayloadHandlerToSender(
            BiConsumer<?, ?> handler,
            Object message,
            Object contextArgument,
            Operation<Void> original) {
        if (contextArgument instanceof Supplier<?> supplier
                && supplier.get() instanceof NetworkEvent.Context context) {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                PhaseFlightMovementGuard.runAsPlayerPayloadHandler(
                        sender,
                        () -> original.call(handler, message, contextArgument));
                return;
            }
        }
        original.call(handler, message, contextArgument);
    }
}
