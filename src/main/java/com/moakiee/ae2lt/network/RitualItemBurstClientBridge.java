package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.entity.RitualHyperdimensionalPigmeeEntity;
import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

final class RitualItemBurstClientBridge {
    private RitualItemBurstClientBridge() {
    }

    static void show(RitualItemBurstPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        var entity = minecraft.level.getEntity(packet.entityId());
        if (!(entity instanceof RitualHyperdimensionalPigmeeEntity)) {
            return;
        }

        ItemStack activationItem = switch (packet.stage()) {
            case RitualItemBurstPacket.PIGMEE_CORE -> new ItemStack(ModItems.PIGMEE_CORE.get());
            case RitualItemBurstPacket.UNDYING_MODULE ->
                    new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_UNDYING.get());
            case RitualItemBurstPacket.PHASE_LOCK_MODULE ->
                    new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_PHASE_LOCK.get());
            default -> ItemStack.EMPTY;
        };
        if (activationItem.isEmpty()) {
            return;
        }

        minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.TOTEM_OF_UNDYING, 30);
        minecraft.level.playLocalSound(
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                SoundEvents.TOTEM_USE,
                entity.getSoundSource(),
                1.0F,
                1.0F,
                false);
        minecraft.gameRenderer.displayItemActivation(activationItem);
    }
}
