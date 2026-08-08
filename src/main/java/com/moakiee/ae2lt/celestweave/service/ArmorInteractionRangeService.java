package com.moakiee.ae2lt.celestweave.service;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.module.ReachSubmodule;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector.ActiveCapability;

public final class ArmorInteractionRangeService {
    // 1.20.1 attribute modifiers are keyed by UUID, not ResourceLocation.
    private static final UUID BLOCK_RANGE_MODIFIER_ID =
            UUID.fromString("7f4b3a2c-1d5e-4a6b-8c9d-0e1f2a3b4c5d");
    private static final UUID ENTITY_RANGE_MODIFIER_ID =
            UUID.fromString("8a5c4b3d-2e6f-4b7c-9d0e-1f2a3b4c5d6e");

    private ArmorInteractionRangeService() {
    }

    public static void tick(ServerPlayer player, List<ActiveCapability> capabilities) {
        double blockBonus = 0.0D;
        double entityBonus = 0.0D;
        for (var active : capabilities) {
            if (!(active.capability() instanceof DeviceCapability.InteractionRange)) {
                continue;
            }
            blockBonus = Math.max(blockBonus, ReachSubmodule.blockBonus(active.armor()));
            entityBonus = Math.max(entityBonus, ReachSubmodule.entityBonus(active.armor()));
        }

        updateModifier(player, ForgeMod.BLOCK_REACH.get(), BLOCK_RANGE_MODIFIER_ID, blockBonus);
        updateModifier(player, ForgeMod.ENTITY_REACH.get(), ENTITY_RANGE_MODIFIER_ID, entityBonus);
    }

    private static void updateModifier(
            ServerPlayer player,
            Attribute attribute,
            UUID id,
            double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        AttributeModifier existing = instance.getModifier(id);
        if (amount <= 0.0D) {
            if (existing != null) {
                instance.removeModifier(existing);
            }
            return;
        }

        if (existing != null
                && Math.abs(existing.getAmount() - amount) < 1.0E-6D
                && existing.getOperation() == AttributeModifier.Operation.ADDITION) {
            return;
        }

        if (existing != null) {
            instance.removeModifier(existing);
        }
        instance.addTransientModifier(new AttributeModifier(
                id,
                "celestweave_reach_extension",
                amount,
                AttributeModifier.Operation.ADDITION));
    }
}
