package com.moakiee.ae2lt.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorMaterials;
import com.moakiee.ae2lt.celestweave.phase.PhaseLockProjectionRules;
import com.moakiee.ae2lt.celestweave.phase.PhaseLockService;

/**
 * Armor-slot projection for a UUID-bound private armor stack. Its registry identity fixes the
 * expected equipment slot; a versioned data-component mirror exposes non-private armor state to
 * vanilla and third-party equipment systems.
 */
public final class PhaseLockProjectionItem extends ArmorItem {
    private final EquipmentSlot equipmentSlot;

    public PhaseLockProjectionItem(Properties properties, EquipmentSlot equipmentSlot) {
        super(
                CelestweaveArmorMaterials.CELESTWEAVE,
                armorType(equipmentSlot),
                properties.stacksTo(1).fireResistant());
        this.equipmentSlot = equipmentSlot;
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return equipmentSlot;
    }

    public EquipmentSlot equipmentSlot() {
        return equipmentSlot;
    }

    @Override
    public String getDescriptionId() {
        return "item.ae2lt.phase_lock_projection";
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
        super.inventoryTick(stack, level, entity, slotId, selected);
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (!PhaseLockProjectionRules.isExpectedSlot(equipmentSlot, slotId)) {
            stack.setCount(0);
            return;
        }
        if (!PhaseLockService.hasPrivateArmor(player, equipmentSlot)) {
            stack.setCount(0);
            level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_DEPLETE,
                    SoundSource.PLAYERS,
                    0.8F,
                    0.7F);
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.discard();
        return true;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // The projection always carries binding and vanishing curses. Suppress their glint so the
        // empty armor material cannot leave a visible armor-shaped overlay on the player.
        return false;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ae2lt.phase_lock_projection.desc"));
    }

    private static ArmorItem.Type armorType(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> ArmorItem.Type.HELMET;
            case CHEST -> ArmorItem.Type.CHESTPLATE;
            case LEGS -> ArmorItem.Type.LEGGINGS;
            case FEET -> ArmorItem.Type.BOOTS;
            default -> throw new IllegalArgumentException("Phase-lock projections require an armor slot");
        };
    }
}
