package com.moakiee.ae2lt.item.staff;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/** Stored module enchantments and gameplay enchantments deliberately have different entry points. */
public final class StaffEnchantments {
    private static final Set<ResourceKey<Enchantment>> COMBAT = Set.of(Enchantments.SHARPNESS,
            Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS, Enchantments.LOOTING,
            Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT, Enchantments.SWEEPING_EDGE);
    private static final List<ItemAbility> TOOL_ACTIONS = List.of(ItemAbilities.PICKAXE_DIG,
            ItemAbilities.AXE_DIG, ItemAbilities.SHOVEL_DIG, ItemAbilities.HOE_DIG,
            ItemAbilities.SHEARS_DIG, ItemAbility.get("knife_dig"), ItemAbility.get("knife_harvest"));

    private StaffEnchantments() {}

    private static boolean backstabbing(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().map(k -> k.location().toString().equals("farmersdelight:backstabbing")).orElse(false);
    }

    public static boolean accepts(StaffModule type, Holder<Enchantment> enchantment) {
        boolean combat = enchantment.unwrapKey().map(COMBAT::contains).orElse(false) || backstabbing(enchantment);
        if (type == StaffModule.DAMAGE) return combat;
        if (type != StaffModule.HARVEST || combat || enchantment.is(Enchantments.SILK_TOUCH)
                || enchantment.is(Enchantments.EFFICIENCY)) return false;
        // Read the real supported-item definition. Modded tool enchantments need no ID whitelist.
        return enchantment.value().definition().supportedItems().stream().anyMatch(holder -> {
            Item item = holder.value();
            if (item instanceof MimicryStaffItem || item instanceof StaffModuleItem) return false;
            if (item instanceof DiggerItem || item instanceof ShearsItem) return true;
            ItemStack probe = new ItemStack(item);
            return TOOL_ACTIONS.stream().anyMatch(probe::canPerformAction);
        });
    }

    private static boolean usable(ItemStack staff, Holder<Enchantment> enchantment) {
        if (backstabbing(enchantment)) return StaffState.has(staff, StaffModule.KNIFE);
        if (enchantment.unwrapKey().map(COMBAT::contains).orElse(false)) return true;
        // A shears-only or hoe-only enchantment does not activate its effect through the
        // always-available pickaxe. The corresponding functional module must be present.
        return enchantment.value().definition().supportedItems().stream().anyMatch(holder -> {
            var item = holder.value();
            if (item instanceof MimicryStaffItem || item instanceof StaffModuleItem) return false;
            if (item instanceof PickaxeItem || item instanceof AxeItem) return true;
            if (item instanceof HoeItem || item instanceof ShovelItem) return StaffState.has(staff, StaffModule.MATTOCK);
            if (item instanceof ShearsItem) return StaffState.has(staff, StaffModule.SHEARS);
            var probe = new ItemStack(item);
            return TOOL_ACTIONS.stream().anyMatch(action -> probe.canPerformAction(action) && ((MimicryStaffItem) staff.getItem()).supportsAction(staff, action));
        });
    }

    public static ItemEnchantments proposed(ItemStack staff, RegistryLookup<Enchantment> lookup) {
        staff = StaffPhaseService.resolve(staff);
        if (staff.isEmpty()) return ItemEnchantments.EMPTY;
        var result = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var type : List.of(StaffModule.DAMAGE, StaffModule.HARVEST)) {
            var module = StaffState.module(staff, type);
            if (module.isEmpty()) continue;
            // Read effective levels, allowing standard third-party level modifiers on the module.
            for (var entry : module.getAllEnchantments(lookup).entrySet()) {
                var enchantment = entry.getKey();
                if (accepts(type, enchantment) && usable(staff, enchantment)
                        && !enchantment.is(Enchantments.FORTUNE)) {
                    result.upgrade(enchantment, entry.getIntValue());
                }
            }
        }
        var harvest = StaffState.module(staff, StaffModule.HARVEST);
        if (!harvest.isEmpty()) {
            if (StaffState.settings(staff).harvest() == 1) {
                var fortune = lookup.getOrThrow(Enchantments.FORTUNE);
                result.set(fortune, Math.max(3, harvest.getEnchantmentLevel(fortune)));
            } else if (StaffState.settings(staff).harvest() == 2) {
                result.set(lookup.getOrThrow(Enchantments.SILK_TOUCH), 1);
            }
        }
        return result.toImmutable();
    }

    public static boolean compatible(ItemEnchantments enchantments) {
        var holders = new ArrayList<>(enchantments.keySet());
        for (int i = 0; i < holders.size(); i++) {
            for (int j = i + 1; j < holders.size(); j++) {
                if (!Enchantment.areCompatible(holders.get(i), holders.get(j))) return false;
            }
        }
        return true;
    }

    public static ItemEnchantments effective(ItemStack staff, RegistryLookup<Enchantment> lookup) {
        var proposed = proposed(staff, lookup);
        // A data pack reload or externally edited component may invalidate an existing loadout.
        // Never run mutually exclusive effects even if the normal menu validation was bypassed.
        return compatible(proposed) ? proposed : ItemEnchantments.EMPTY;
    }
}
