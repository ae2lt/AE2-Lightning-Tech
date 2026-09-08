package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

class OverloadedInterfaceExactImportPlanTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exportExclusionIsExactAndKeepsOtherComponentsAndKeyTypes() {
        var stone = AEItemKey.of(Items.STONE);
        var named = new ItemStack(Items.STONE);
        named.setHoverName(Component.literal("allowed variant"));
        var variant = AEItemKey.of(named);
        var water = AEFluidKey.of(Fluids.WATER);
        var plan = new OverloadedInterfaceBlockEntity.ExactImportPlan(
                Set.of(stone, variant, water), Set.of(stone));

        assertTrue(plan.hasKeys());
        assertEquals(List.of(variant), plan.keysFor(AEKeyType.items()));
        assertEquals(List.of(water), plan.keysFor(AEKeyType.fluids()));
    }

    @Test
    void completelyExcludedTypesHaveNoImportWork() {
        var stone = AEItemKey.of(Items.STONE);
        var water = AEFluidKey.of(Fluids.WATER);
        var mixed = new OverloadedInterfaceBlockEntity.ExactImportPlan(Set.of(stone, water), Set.of(stone));
        assertFalse(mixed.allowsType(AEKeyType.items()));
        assertTrue(mixed.allowsType(AEKeyType.fluids()));
        assertTrue(mixed.keysFor(AEKeyType.items()).isEmpty());

        var excluded = new OverloadedInterfaceBlockEntity.ExactImportPlan(Set.of(stone), Set.of(stone));
        assertFalse(excluded.hasKeys());
        assertFalse(excluded.allowsType(AEKeyType.items()));
    }

    @Test
    void rebuiltConfigurationDoesNotMutateAnEarlierPassSnapshot() {
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        var filters = new HashSet<AEKey>(Set.of(stone, dirt));
        var exports = new HashSet<AEKey>(Set.of(stone));
        var before = new OverloadedInterfaceBlockEntity.ExactImportPlan(filters, exports);
        filters.remove(dirt);
        exports.clear();
        var after = new OverloadedInterfaceBlockEntity.ExactImportPlan(filters, exports);
        assertEquals(List.of(dirt), before.keysFor(AEKeyType.items()));
        assertEquals(List.of(stone), after.keysFor(AEKeyType.items()));
    }

    @Test
    void groupingKeepsOriginalKeyOrderForLimitedEnergyBudgets() {
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        var water = AEFluidKey.of(Fluids.WATER);
        var lava = AEFluidKey.of(Fluids.LAVA);
        var filters = new LinkedHashSet<AEKey>(List.of(water, stone, lava, dirt));
        var plan = new OverloadedInterfaceBlockEntity.ExactImportPlan(filters, Set.of());
        assertEquals(List.of(stone, dirt), plan.keysFor(AEKeyType.items()));
        assertEquals(List.of(water, lava), plan.keysFor(AEKeyType.fluids()));
    }
}
