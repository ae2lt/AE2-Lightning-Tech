package com.moakiee.ae2lt.celestweave.phase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PhaseLockSourceContractTest {
    @Test
    void projectionCarriesOnlyAReferenceAndMirroredFields() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/item/PhaseLockProjectionItem.java"));

        assertFalse(source.contains("CompoundTag"));
        assertTrue(source.contains("PhaseLockProjectionRules.isExpectedSlot(equipmentSlot, slotId)"));
        assertTrue(source.contains("PhaseLockService.hasPrivateArmor(player, equipmentSlot)"));
        assertTrue(source.contains("entity.discard()"));

        String service = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/phase/PhaseLockService.java"));
        assertTrue(service.contains("PhaseLockProjectionSynchronizer.synchronize("));
        assertTrue(service.contains("PhaseLockProjectionSynchronizer.publishArmorChanges("));

        String synchronizer = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/phase/PhaseLockProjectionSynchronizer.java"));
        assertTrue(synchronizer.contains("PHASE_LOCK_PROJECTION_LINK"));
        assertTrue(synchronizer.contains("PHASE_LOCK_ARMOR_UPDATE"));
        assertTrue(synchronizer.contains("ensureProjectionCurses"));
        assertTrue(synchronizer.contains("Enchantments.BINDING_CURSE"));
        assertTrue(synchronizer.contains("Enchantments.VANISHING_CURSE"));
        assertTrue(synchronizer.contains("removeProjectionOnlyCurse"));
        assertTrue(synchronizer.contains("copyProjectionEnchantmentsToArmor"));
    }

    @Test
    void transfersClearTheirSourceBeforeReturningOrEquippingAnItem() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/phase/PhaseLockService.java"));

        int take = source.indexOf("vault.takeAll(player.getUUID())");
        int wear = source.indexOf("player.setItemSlot(slot, armor)", take);
        assertTrue(take >= 0 && wear > take);

        int clear = source.indexOf("player.setItemSlot(slot, ItemStack.EMPTY)", wear);
        int returnToInventory = source.indexOf("player.getInventory().placeItemBackInInventory(displaced)", clear);
        assertTrue(clear >= 0 && returnToInventory > clear);

        assertFalse(source.contains("authoritativeArmor.copy("));
        assertFalse(source.contains("armor.copy("));

        int payment = source.indexOf("ArmorEnergyService.consumeActiveCostPayment");
        int failedRelease = source.indexOf("release(player, true)", payment);
        int regenerate = source.indexOf("displaceArmorOccupant(player, slot, armor)", payment);
        assertTrue(payment >= 0 && failedRelease > payment && regenerate > failedRelease);
    }

    @Test
    void vaultIsSoulBoundAndEffectiveComponentsAreMirrored() throws Exception {
        String vault = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/phase/PhaseArmorVaultSavedData.java"));
        assertTrue(vault.contains("Map<UUID, EnumMap<EquipmentSlot, ItemStack>>"));
        assertTrue(vault.contains("entryTag.putUUID(TAG_PLAYER"));
        assertTrue(vault.contains("entryTag.putString(TAG_SLOT"));

        String service = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/phase/PhaseLockService.java"));
        assertFalse(service.contains("stack.forEachModifier("));
        assertTrue(service.contains("EquipmentSlot.HEAD"));
        assertTrue(service.contains("EquipmentSlot.CHEST"));
        assertTrue(service.contains("EquipmentSlot.LEGS"));
        assertTrue(service.contains("EquipmentSlot.FEET"));

        String synchronizer = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/celestweave/phase/PhaseLockProjectionSynchronizer.java"));
        assertTrue(synchronizer.contains("source.getComponents()"));
        assertFalse(synchronizer.contains("type != DataComponents.ATTRIBUTE_MODIFIERS"));
        assertTrue(synchronizer.contains("type != ModDataComponents.CELESTWEAVE_STRUCTURAL_CORE"));
        assertTrue(synchronizer.contains("type != ModDataComponents.CELESTWEAVE_ENERGY_BUFFER"));
        assertTrue(synchronizer.contains("type != ModDataComponents.CELESTWEAVE_MODULES"));
        assertTrue(synchronizer.contains("type != ModDataComponents.CELESTWEAVE_MODULES_POWERED"));
    }
}
