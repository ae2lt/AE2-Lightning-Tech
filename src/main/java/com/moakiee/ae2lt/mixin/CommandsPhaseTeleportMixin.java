package com.moakiee.ae2lt.mixin;

import java.util.function.Supplier;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/**
 * Binds the initiating command source around the complete synchronous command queue.
 * 1.20.1 has no {@code executeCommandInContext}; every player/console command funnels
 * through {@code Commands.performPrefixedCommand}, so it is wrapped instead.
 */
@Mixin(Commands.class)
public abstract class CommandsPhaseTeleportMixin {
    @WrapMethod(
            method = "performPrefixedCommand(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;)I")
    private int ae2lt$bindTeleportCommandSource(
            CommandSourceStack source,
            String command,
            Operation<Integer> original) {
        return PhaseFlightMovementGuard.runAsCommandExecution(
                source,
                (Supplier<Integer>) () -> original.call(source, command));
    }
}
