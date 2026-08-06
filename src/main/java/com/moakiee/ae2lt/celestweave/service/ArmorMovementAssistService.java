package com.moakiee.ae2lt.celestweave.service;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.celestweave.MovementAssistRules;
import com.moakiee.ae2lt.celestweave.module.MovementAssistSubmodule;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector.ActiveCapability;
import com.moakiee.ae2lt.device.capability.DeviceCapability;

public final class ArmorMovementAssistService {
    private static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID,
            "celestweave_movement_assist_speed");
    private static final ResourceLocation STEP_HEIGHT_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID,
            "celestweave_movement_assist_step_height");
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
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        updateModifier(
                player,
                Attributes.STEP_HEIGHT,
                STEP_HEIGHT_MODIFIER_ID,
                active ? MovementAssistRules.stepHeightModifierAmount(stepHeight) : 0.0D,
                AttributeModifier.Operation.ADD_VALUE);
    }

    private static void updateModifier(
            ServerPlayer player,
            Holder<Attribute> attribute,
            ResourceLocation id,
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
                && Math.abs(existing.amount() - amount) < EPSILON
                && existing.operation() == operation) {
            return;
        }

        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }
}
