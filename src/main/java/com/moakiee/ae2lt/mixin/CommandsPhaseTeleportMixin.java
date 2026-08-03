package com.moakiee.ae2lt.mixin;

import java.util.function.Consumer;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.ExecutionContext;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Binds the initiating command source around the complete synchronous command queue. */
@Mixin(Commands.class)
public abstract class CommandsPhaseTeleportMixin {
    @WrapMethod(
            method = "executeCommandInContext(Lnet/minecraft/commands/CommandSourceStack;"
                    + "Ljava/util/function/Consumer;)V")
    private static void ae2lt$bindTeleportCommandSource(
            CommandSourceStack source,
            Consumer<ExecutionContext<CommandSourceStack>> command,
            Operation<Void> original) {
        PhaseFlightMovementGuard.runAsCommandExecution(
                source,
                () -> original.call(source, command));
    }
}
