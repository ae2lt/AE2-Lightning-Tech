package com.moakiee.ae2lt.celestweave.phase;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.module.PhaseLockSubmodule;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector;
import com.moakiee.ae2lt.celestweave.service.ArmorEnergyService;
import com.moakiee.ae2lt.celestweave.service.ArmorLightningService;
import com.moakiee.ae2lt.celestweave.service.ArmorTickService;
import com.moakiee.ae2lt.item.PhaseLockProjectionItem;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModItems;

/** Owns all transfers between vanilla armor slots and the UUID-bound private armor vault. */
public final class PhaseLockService {
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET);

    private PhaseLockService() {
    }

    /** Runs before the other Celestweave post-tick services. */
    public static void tick(ServerPlayer player) {
        PhaseArmorVaultSavedData vault = vault(player);
        UUID playerId = player.getUUID();
        if (!vault.containsAny(playerId)) {
            tryEnter(player, vault);
            return;
        }

        ItemStack anchor = vault.getMutable(playerId, EquipmentSlot.CHEST);
        if (!isRealArmorForSlot(anchor, EquipmentSlot.CHEST)) {
            recoverInvalidVaultEntries(player, vault);
            return;
        }
        if (!shouldRemainLocked(player, anchor)) {
            release(player, false);
            return;
        }

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack armor = vault.getMutable(playerId, slot);
            if (armor != null && !isRealArmorForSlot(armor, slot)) {
                recoverInvalidVaultEntries(player, vault);
                return;
            }
        }

        EnumSet<EquipmentSlot> newlyLocked = captureNewArmor(player, vault);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack armor = vault.getMutable(playerId, slot);
            if (armor == null) {
                clearStaleProjection(player, slot);
                continue;
            }
            if (!isProjectionForSlot(player.getItemBySlot(slot), slot)) {
                var payment = ArmorEnergyService.consumeActiveCostPayment(
                        player,
                        armor,
                        ArmorOverloadRules.PHASE_LOCK_REGEN_COST_FE);
                if (!completeRegenerationPayment(
                        payment.paid(),
                        () -> ArmorLightningService.consume(
                                player,
                                armor,
                                LightningKey.EXTREME_HIGH_VOLTAGE,
                                ArmorOverloadRules.PHASE_LOCK_REGEN_COST_EHV),
                        payment::refund)) {
                    release(player, true);
                    return;
                }
                displaceArmorOccupant(player, slot, armor);
                player.setItemSlot(slot, createProjection(player, slot, armor));
            }
            PhaseLockProjectionSynchronizer.synchronize(
                    player,
                    armor,
                    player.getItemBySlot(slot));
        }

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (newlyLocked.contains(slot)) {
                // The real stack already received its normal equipped inventory tick before the
                // post-tick service moved it into the vault.
                continue;
            }
            ItemStack armor = vault.getMutable(playerId, slot);
            if (armor != null) {
                ItemStack projection = player.getItemBySlot(slot);
                var before = PhaseLockProjectionSynchronizer.captureArmorFields(armor);
                ArmorTickService.tickEquipped(
                        player,
                        armor,
                        true,
                        player.registryAccess(),
                        Dist.DEDICATED_SERVER);
                PhaseLockProjectionSynchronizer.publishArmorChanges(
                        player,
                        armor,
                        projection,
                        before);
            }
        }
    }

    static boolean completeRegenerationPayment(
            boolean fePaid,
            BooleanSupplier consumeExtremeHighVoltage,
            Runnable refundFe) {
        if (!fePaid) {
            return false;
        }
        if (consumeExtremeHighVoltage.getAsBoolean()) {
            return true;
        }
        refundFe.run();
        return false;
    }

    public static boolean hasPrivateArmor(ServerPlayer player, EquipmentSlot slot) {
        return vault(player).contains(player.getUUID(), slot);
    }

    public static boolean hasPrivateArmor(ServerPlayer player) {
        return vault(player).containsAny(player.getUUID());
    }

    /** Returns the one live armor stack for the slot. Callers must never copy it for restoration. */
    public static ItemStack getPrivateArmor(ServerPlayer player, EquipmentSlot slot) {
        ItemStack armor = vault(player).getMutable(player.getUUID(), slot);
        return armor == null ? ItemStack.EMPTY : armor;
    }

    /** Explicit release path, also used when the module is disabled through the Device Hub. */
    public static boolean release(ServerPlayer player) {
        return release(player, false);
    }

    private static boolean release(ServerPlayer player, boolean collapsed) {
        PhaseArmorVaultSavedData vault = vault(player);
        synchronizeBeforeRelease(player, vault);
        EnumMap<EquipmentSlot, ItemStack> armorBySlot = vault.takeAll(player.getUUID());
        // The private source is cleared before any stack can be returned to player-controlled slots.
        clearPlayerProjections(player);
        boolean restoredAny = false;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack armor = armorBySlot.remove(slot);
            if (armor == null || armor.isEmpty()) {
                continue;
            }
            if (!isRealArmorForSlot(armor, slot)) {
                player.getInventory().placeItemBackInInventory(armor);
                continue;
            }
            displaceArmorOccupant(player, slot, armor);
            player.setItemSlot(slot, armor);
            restoredAny = true;
            ArmorTickService.tickEquipped(
                    player,
                    armor,
                    true,
                    player.registryAccess(),
                    Dist.DEDICATED_SERVER);
        }
        // Preserve any future/unknown entries instead of silently deleting them.
        for (ItemStack armor : armorBySlot.values()) {
            if (armor != null && !armor.isEmpty()) {
                player.getInventory().placeItemBackInInventory(armor);
            }
        }
        ArmorCapabilityCollector.clearCache(player);
        if (collapsed) {
            playCollapseSound(player);
        }
        return restoredAny;
    }

    private static void tryEnter(ServerPlayer player, PhaseArmorVaultSavedData vault) {
        ItemStack anchor = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!isRealArmorForSlot(anchor, EquipmentSlot.CHEST) || !shouldRemainLocked(player, anchor)) {
            return;
        }

        clearPlayerProjections(player);
        var armorToLock = new EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot.class);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack armor = player.getItemBySlot(slot);
            if (isRealArmorForSlot(armor, slot)) {
                armorToLock.put(slot, armor);
            }
        }
        for (EquipmentSlot slot : armorToLock.keySet()) {
            player.setItemSlot(slot, ItemStack.EMPTY); // clear every equipment source first
        }
        for (var entry : armorToLock.entrySet()) {
            if (!vault.store(player.getUUID(), entry.getKey(), entry.getValue())) {
                restoreFailedEntry(player, vault, armorToLock);
                return;
            }
        }
        for (EquipmentSlot slot : armorToLock.keySet()) {
            ItemStack armor = vault.getMutable(player.getUUID(), slot);
            player.setItemSlot(slot, createProjection(player, slot, armor));
        }
        ArmorCapabilityCollector.clearCache(player);
        // Each real stack already ticked while it occupied its vanilla armor slot this tick.
    }

    private static EnumSet<EquipmentSlot> captureNewArmor(
            ServerPlayer player,
            PhaseArmorVaultSavedData vault) {
        EnumSet<EquipmentSlot> newlyLocked = EnumSet.noneOf(EquipmentSlot.class);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (vault.contains(player.getUUID(), slot)) {
                continue;
            }
            ItemStack armor = player.getItemBySlot(slot);
            if (!isRealArmorForSlot(armor, slot)) {
                continue;
            }
            player.setItemSlot(slot, ItemStack.EMPTY); // equipment source is cleared first
            if (!vault.store(player.getUUID(), slot, armor)) {
                player.setItemSlot(slot, armor);
                continue;
            }
            player.setItemSlot(slot, createProjection(player, slot, armor));
            newlyLocked.add(slot);
        }
        if (!newlyLocked.isEmpty()) {
            ArmorCapabilityCollector.clearCache(player);
        }
        return newlyLocked;
    }

    private static void restoreFailedEntry(
            ServerPlayer player,
            PhaseArmorVaultSavedData vault,
            EnumMap<EquipmentSlot, ItemStack> original) {
        EnumMap<EquipmentSlot, ItemStack> stored = vault.takeAll(player.getUUID());
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack armor = stored.getOrDefault(slot, original.get(slot));
            if (armor != null && !armor.isEmpty()) {
                displaceArmorOccupant(player, slot, armor);
                player.setItemSlot(slot, armor);
            }
        }
        ArmorCapabilityCollector.clearCache(player);
    }

    private static boolean shouldRemainLocked(ServerPlayer player, ItemStack anchor) {
        return CelestweaveArmorState.hasCore(anchor, player.registryAccess())
                && CelestweaveArmorState.isSubmoduleInstalled(
                        anchor,
                        player.registryAccess(),
                        PhaseLockSubmodule.INSTANCE.id())
                && CelestweaveArmorState.isSubmoduleEnabled(anchor, PhaseLockSubmodule.INSTANCE)
                && PhaseLockSubmodule.isArmorLockEnabled(anchor);
    }

    private static void recoverInvalidVaultEntries(
            ServerPlayer player,
            PhaseArmorVaultSavedData vault) {
        EnumMap<EquipmentSlot, ItemStack> invalidEntries = vault.takeAll(player.getUUID());
        clearPlayerProjections(player);
        for (ItemStack armor : invalidEntries.values()) {
            if (armor != null && !armor.isEmpty()) {
                player.getInventory().placeItemBackInInventory(armor);
            }
        }
        ArmorCapabilityCollector.clearCache(player);
        playCollapseSound(player);
    }

    private static void displaceArmorOccupant(
            ServerPlayer player,
            EquipmentSlot slot,
            ItemStack authoritativeArmor) {
        ItemStack displaced = player.getItemBySlot(slot);
        if (displaced.isEmpty()) {
            return;
        }
        player.setItemSlot(slot, ItemStack.EMPTY); // always clear the source first
        if (displaced == authoritativeArmor) {
            // A hostile inventory implementation may expose the same object in two slots. Clearing
            // the vanilla source is enough; emptying it here would also empty the authoritative vault.
            return;
        }
        if (isProjection(displaced) || isDuplicateOf(displaced, authoritativeArmor)) {
            displaced.setCount(0);
            return;
        }
        player.getInventory().placeItemBackInInventory(displaced);
    }

    private static boolean isDuplicateOf(ItemStack candidate, ItemStack authoritativeArmor) {
        if (!(candidate.getItem() instanceof BaseCelestweaveArmorItem)) {
            return false;
        }
        UUID authoritativeId = CelestweaveArmorState.getArmorId(authoritativeArmor);
        UUID candidateId = CelestweaveArmorState.getArmorId(candidate);
        return authoritativeId != null && authoritativeId.equals(candidateId);
    }

    private static ItemStack createProjection(
            ServerPlayer player,
            EquipmentSlot slot,
            ItemStack armor) {
        ItemStack projection = new ItemStack(switch (slot) {
            case HEAD -> ModItems.PHASE_LOCK_PROJECTION_HEAD.get();
            case CHEST -> ModItems.PHASE_LOCK_PROJECTION.get();
            case LEGS -> ModItems.PHASE_LOCK_PROJECTION_LEGS.get();
            case FEET -> ModItems.PHASE_LOCK_PROJECTION_FEET.get();
            default -> throw new IllegalArgumentException("Not an armor slot: " + slot);
        });
        PhaseLockProjectionSynchronizer.synchronize(player, armor, projection);
        return projection;
    }

    private static void synchronizeBeforeRelease(
            ServerPlayer player,
            PhaseArmorVaultSavedData vault) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack armor = vault.getMutable(player.getUUID(), slot);
            ItemStack projection = player.getItemBySlot(slot);
            if (armor != null && isProjectionForSlot(projection, slot)) {
                PhaseLockProjectionSynchronizer.synchronize(player, armor, projection);
            }
        }
    }

    private static void clearPlayerProjections(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isProjection(stack)) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static void clearStaleProjection(ServerPlayer player, EquipmentSlot slot) {
        ItemStack stack = player.getItemBySlot(slot);
        if (isProjection(stack)) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static boolean isProjection(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof PhaseLockProjectionItem;
    }

    private static boolean isProjectionForSlot(ItemStack stack, EquipmentSlot slot) {
        return isProjection(stack)
                && ((PhaseLockProjectionItem) stack.getItem()).equipmentSlot() == slot;
    }

    private static boolean isRealArmorForSlot(ItemStack stack, EquipmentSlot slot) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BaseCelestweaveArmorItem armor)) {
            return false;
        }
        return equipmentSlot(armor.armorPart()) == slot;
    }

    private static EquipmentSlot equipmentSlot(ArmorPart part) {
        return switch (part) {
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
        };
    }

    private static PhaseArmorVaultSavedData vault(ServerPlayer player) {
        return PhaseArmorVaultSavedData.get(player.getServer());
    }

    private static void playCollapseSound(ServerPlayer player) {
        player.level().playSound(
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
