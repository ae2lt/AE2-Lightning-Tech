package com.moakiee.ae2lt.celestweave.phase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Maintains the equipped projection as a writable mirror of the one authoritative armor stack in
 * the private vault.
 *
 * <p>All effective data components are mirrored except phase-lock bookkeeping and Celestweave's
 * private control state. This exposes vanilla and third-party affix/attribute components on the
 * equipped stack without allowing a projection to become a second functional armor controller.</p>
 */
final class PhaseLockProjectionSynchronizer {
    private PhaseLockProjectionSynchronizer() {
    }

    static void synchronize(ServerPlayer player, ItemStack armor, ItemStack projection) {
        clearMisplacedPrivateComponents(armor, projection);
        UUID armorId = CelestweaveArmorState.ensureArmorId(armor);
        long armorUpdate = armor.getOrDefault(ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.get(), 0L);
        PhaseLockProjectionLink link = projection.get(ModDataComponents.PHASE_LOCK_PROJECTION_LINK.get());
        ProjectionCurses projectionCurses = projectionCurses(player);
        boolean equal = armorSnapshot(armor).equals(projectionSnapshot(projection, armor, projectionCurses));

        PhaseLockProjectionSyncRules.Direction direction = PhaseLockProjectionSyncRules.direction(
                armorId,
                armorUpdate,
                link,
                equal);
        if (direction == PhaseLockProjectionSyncRules.Direction.NONE) {
            // A newer projection with identical fields still advances the authoritative clock.
            if (link != null && link.armorId().equals(armorId) && link.update() > armorUpdate) {
                armor.set(ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.get(), link.update());
            }
            ensureProjectionCurses(projection, projectionCurses);
            return;
        }

        long nextUpdate = PhaseLockProjectionSyncRules.nextUpdate(armorUpdate, link);
        if (direction == PhaseLockProjectionSyncRules.Direction.ARMOR_TO_PROJECTION) {
            armor.set(ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.get(), nextUpdate);
            replaceMirroredComponents(armor, projection, false);
            ensureProjectionCurses(projection, projectionCurses);
        } else {
            replaceMirroredComponents(projection, armor, true);
            copyProjectionEnchantmentsToArmor(projection, armor, projectionCurses);
            armor.set(ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.get(), nextUpdate);
        }
        projection.set(
                ModDataComponents.PHASE_LOCK_PROJECTION_LINK.get(),
                new PhaseLockProjectionLink(armorId, nextUpdate));
        ensureProjectionCurses(projection, projectionCurses);
    }

    static MirroredSnapshot captureArmorFields(ItemStack armor) {
        return armorSnapshot(armor);
    }

    /** Publishes real-armor mutations made by its manual armor tick without a one-tick rollback. */
    static void publishArmorChanges(
            ServerPlayer player,
            ItemStack armor,
            ItemStack projection,
            MirroredSnapshot before) {
        if (before.equals(armorSnapshot(armor))) {
            return;
        }
        UUID armorId = CelestweaveArmorState.ensureArmorId(armor);
        long armorUpdate = armor.getOrDefault(ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.get(), 0L);
        PhaseLockProjectionLink link = projection.get(ModDataComponents.PHASE_LOCK_PROJECTION_LINK.get());
        long nextUpdate = PhaseLockProjectionSyncRules.nextUpdate(armorUpdate, link);
        armor.set(ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.get(), nextUpdate);
        replaceMirroredComponents(armor, projection, false);
        ensureProjectionCurses(projection, projectionCurses(player));
        projection.set(
                ModDataComponents.PHASE_LOCK_PROJECTION_LINK.get(),
                new PhaseLockProjectionLink(armorId, nextUpdate));
    }

    private static MirroredSnapshot armorSnapshot(ItemStack armor) {
        return snapshot(armor, null, null);
    }

    private static MirroredSnapshot projectionSnapshot(
            ItemStack projection,
            ItemStack armor,
            ProjectionCurses projectionCurses) {
        return snapshot(projection, armor, projectionCurses);
    }

    private static MirroredSnapshot snapshot(
            ItemStack stack,
            ItemStack authoritativeArmor,
            ProjectionCurses projectionCurses) {
        Map<DataComponentType<?>, Object> components = new HashMap<>();
        for (TypedDataComponent<?> component : stack.getComponents()) {
            if (!isMirrored(component.type())) {
                continue;
            }
            Object value = component.value();
            if (component.type() == DataComponents.ENCHANTMENTS && authoritativeArmor != null) {
                value = projectionEnchantmentsForArmor(stack, authoritativeArmor, projectionCurses);
            }
            components.put(component.type(), value);
        }
        return new MirroredSnapshot(Map.copyOf(components));
    }

    private static void replaceMirroredComponents(
            ItemStack source,
            ItemStack target,
            boolean skipEnchantments) {
        for (DataComponentType<?> type : new ArrayList<>(target.getComponents().keySet())) {
            if (isMirrored(type) && (!skipEnchantments || type != DataComponents.ENCHANTMENTS)) {
                removeUnchecked(target, type);
            }
        }
        for (TypedDataComponent<?> component : source.getComponents()) {
            if (isMirrored(component.type())
                    && (!skipEnchantments || component.type() != DataComponents.ENCHANTMENTS)) {
                applyUnchecked(target, component);
            }
        }
    }

    private static void copyProjectionEnchantmentsToArmor(
            ItemStack projection,
            ItemStack armor,
            ProjectionCurses projectionCurses) {
        armor.set(
                DataComponents.ENCHANTMENTS,
                projectionEnchantmentsForArmor(projection, armor, projectionCurses));
    }

    private static ItemEnchantments projectionEnchantmentsForArmor(
            ItemStack projection,
            ItemStack armor,
            ProjectionCurses projectionCurses) {
        ItemEnchantments projectionEnchantments = projection.getOrDefault(
                DataComponents.ENCHANTMENTS,
                ItemEnchantments.EMPTY);
        var mutable = new ItemEnchantments.Mutable(projectionEnchantments);
        ItemEnchantments armorEnchantments = armor.getOrDefault(
                DataComponents.ENCHANTMENTS,
                ItemEnchantments.EMPTY);
        removeProjectionOnlyCurse(mutable, armorEnchantments, projectionCurses.binding());
        removeProjectionOnlyCurse(mutable, armorEnchantments, projectionCurses.vanishing());
        return mutable.toImmutable();
    }

    private static void removeProjectionOnlyCurse(
            ItemEnchantments.Mutable projectionEnchantments,
            ItemEnchantments armorEnchantments,
            Holder<Enchantment> curse) {
        if (armorEnchantments.getLevel(curse) == 0) {
            projectionEnchantments.removeIf(curse::equals);
        }
    }

    private static void ensureProjectionCurses(
            ItemStack projection,
            ProjectionCurses projectionCurses) {
        var enchantments = new ItemEnchantments.Mutable(projection.getOrDefault(
                DataComponents.ENCHANTMENTS,
                ItemEnchantments.EMPTY));
        enchantments.upgrade(projectionCurses.binding(), 1);
        enchantments.upgrade(projectionCurses.vanishing(), 1);
        projection.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
    }

    private static ProjectionCurses projectionCurses(ServerPlayer player) {
        var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        return new ProjectionCurses(
                enchantments.getOrThrow(Enchantments.BINDING_CURSE),
                enchantments.getOrThrow(Enchantments.VANISHING_CURSE));
    }

    private static boolean isMirrored(DataComponentType<?> type) {
        return type != ModDataComponents.CELESTWEAVE_STRUCTURAL_CORE.get()
                && type != ModDataComponents.CELESTWEAVE_ENERGY_BUFFER.get()
                && type != ModDataComponents.CELESTWEAVE_MODULES.get()
                && type != ModDataComponents.CELESTWEAVE_MODULES_POWERED.get()
                && type != ModDataComponents.PHASE_LOCK_PROJECTION_LINK.get()
                && type != ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.get();
    }

    private static void clearMisplacedPrivateComponents(ItemStack armor, ItemStack projection) {
        armor.remove(ModDataComponents.PHASE_LOCK_PROJECTION_LINK.get());
        projection.remove(ModDataComponents.PHASE_LOCK_ARMOR_UPDATE.get());
        projection.remove(ModDataComponents.CELESTWEAVE_STRUCTURAL_CORE.get());
        projection.remove(ModDataComponents.CELESTWEAVE_ENERGY_BUFFER.get());
        projection.remove(ModDataComponents.CELESTWEAVE_MODULES.get());
        projection.remove(ModDataComponents.CELESTWEAVE_MODULES_POWERED.get());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void removeUnchecked(ItemStack stack, DataComponentType<?> type) {
        stack.remove((DataComponentType) type);
    }

    private static <T> void applyUnchecked(ItemStack stack, TypedDataComponent<T> component) {
        stack.set(component.type(), component.value());
    }

    record MirroredSnapshot(Map<DataComponentType<?>, Object> components) {
    }

    private record ProjectionCurses(
            Holder<Enchantment> binding,
            Holder<Enchantment> vanishing) {
    }
}
