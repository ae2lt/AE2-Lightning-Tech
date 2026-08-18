package com.moakiee.ae2lt.celestweave.service;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;

import com.moakiee.ae2lt.celestweave.MovementAssistRules;
import com.moakiee.ae2lt.celestweave.module.MovementAssistSubmodule;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector.ActiveCapability;
import com.moakiee.ae2lt.device.capability.DeviceCapability;

public final class ArmorMovementAssistService {
    private static final UUID SPEED_MODIFIER_ID =
            UUID.fromString("a1b2c3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d");
    private static final UUID STEP_HEIGHT_MODIFIER_ID =
            UUID.fromString("b2c3d4e5-6f7a-4b8c-9d0e-1f2a3b4c5d6e");
    private static final double EPSILON = 1.0E-6D;

    private ArmorMovementAssistService() {
    }

    public static void tick(ServerPlayer player, List<ActiveCapability> capabilities) {
        boolean active = false;
        double movementMultiplier = 1.0D;
        double stepHeight = MovementAssistRules.VANILLA_STEP_HEIGHT;
        boolean suppressGroundMovement = player.getAbilities().flying
                || player.isFallFlying()
                || player.isSwimming();

        for (var capability : capabilities) {
            if (!(capability.capability() instanceof DeviceCapability.MovementAssist)) {
                continue;
            }
            double candidateMovementMultiplier = MovementAssistRules.movementMultiplier(
                    suppressGroundMovement,
                    player.isCrouching(),
                    player.isSprinting(),
                    MovementAssistSubmodule.walkSpeedMultiplier(capability.armor()),
                    MovementAssistSubmodule.sprintSpeedMultiplier(capability.armor()),
                    MovementAssistSubmodule.sneakSpeedMultiplier(capability.armor()));
            double candidateStepHeight = MovementAssistSubmodule.automaticStepHeight(capability.armor());
            if (!active) {
                movementMultiplier = candidateMovementMultiplier;
                stepHeight = candidateStepHeight;
                active = true;
            } else {
                movementMultiplier = Math.max(movementMultiplier, candidateMovementMultiplier);
                stepHeight = Math.max(stepHeight, candidateStepHeight);
            }
        }

        updateModifier(
                player,
                Attributes.MOVEMENT_SPEED,
                SPEED_MODIFIER_ID,
                active ? MovementAssistRules.speedModifierAmount(movementMultiplier) : 0.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        updateModifier(
                player,
                ForgeMod.STEP_HEIGHT_ADDITION.get(),
                STEP_HEIGHT_MODIFIER_ID,
                active ? MovementAssistRules.stepHeightModifierAmount(stepHeight) : 0.0D,
                AttributeModifier.Operation.ADDITION);
    }

    private static void updateModifier(
            ServerPlayer player,
            Attribute attribute,
            UUID id,
            double amount,
            AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        AttributeModifier existing = instance.getModifier(id);
        if (Math.abs(amount) < EPSILON) {
            if (existing != null) {
                instance.removeModifier(existing);
            }
            return;
        }

        if (existing != null
                && Math.abs(existing.getAmount() - amount) < EPSILON
                && existing.getOperation() == operation) {
            return;
        }

        if (existing != null) {
            instance.removeModifier(existing);
        }
        instance.addTransientModifier(new AttributeModifier(id, "celestweave_movement_assist", amount, operation));
    }
}
