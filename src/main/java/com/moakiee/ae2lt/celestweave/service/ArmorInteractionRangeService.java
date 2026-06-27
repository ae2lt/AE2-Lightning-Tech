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
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.celestweave.module.ReachSubmodule;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector.ActiveCapability;

public final class ArmorInteractionRangeService {
    private static final ResourceLocation BLOCK_RANGE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "celestweave_reach_extension_block");
    private static final ResourceLocation ENTITY_RANGE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "celestweave_reach_extension_entity");

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

        updateModifier(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_RANGE_MODIFIER_ID, blockBonus);
        updateModifier(player, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_RANGE_MODIFIER_ID, entityBonus);
    }

    private static void updateModifier(
            ServerPlayer player,
            Holder<Attribute> attribute,
            ResourceLocation id,
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
                && Math.abs(existing.amount() - amount) < 1.0E-6D
                && existing.operation() == AttributeModifier.Operation.ADD_VALUE) {
            return;
        }

        instance.addOrUpdateTransientModifier(new AttributeModifier(
                id,
                amount,
                AttributeModifier.Operation.ADD_VALUE));
    }
}
