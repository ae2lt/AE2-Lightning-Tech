package com.moakiee.ae2lt.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.fml.loading.LoadingModList;

class NativeStackDropHelperTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void splitsOversizedStackWithoutLosingItems() {
        ItemStack oversized = new ItemStack(Items.COBBLESTONE, 1024);

        List<ItemStack> drops = new ArrayList<>();
        NativeStackDropHelper.addDrops(drops, oversized);

        assertEquals(16, drops.size());
        assertTrue(drops.stream().allMatch(stack -> stack.getCount() == 64));
        assertEquals(1024, drops.stream().mapToInt(ItemStack::getCount).sum());
        assertEquals(1024, oversized.getCount(), "splitting must not mutate the stored stack");
    }

    @Test
    void preservesComponentsWhenSplittingNonstackableItems() {
        Component customName = Component.literal("conservation-marker");
        ItemStack oversized = new ItemStack(Items.DIAMOND_PICKAXE, 2);
        oversized.set(DataComponents.CUSTOM_NAME, customName);

        List<ItemStack> drops = new ArrayList<>();
        NativeStackDropHelper.addDrops(drops, oversized);

        assertEquals(2, drops.size());
        assertTrue(drops.stream().allMatch(stack -> stack.getCount() == 1));
        assertTrue(drops.stream().allMatch(stack -> customName.equals(stack.get(DataComponents.CUSTOM_NAME))));
        assertEquals(2, drops.stream().mapToInt(ItemStack::getCount).sum());
        assertEquals(2, oversized.getCount(), "splitting must not mutate the stored stack");
    }

    @Test
    void ignoresEmptyStacks() {
        assertTrue(NativeStackDropHelper.splitForDrop(ItemStack.EMPTY).isEmpty());
    }

    @Test
    void vanillaHopperConservesEverySplitDrop() {
        assertHopperConserves(new ItemStack(Items.COBBLESTONE, 1024));
        assertHopperConserves(new ItemStack(Items.DIAMOND_PICKAXE, 2));
    }

    private static void assertHopperConserves(ItemStack oversized) {
        HopperBlockEntity hopper = new HopperBlockEntity(BlockPos.ZERO, Blocks.HOPPER.defaultBlockState());
        int returned = 0;
        for (ItemStack drop : NativeStackDropHelper.splitForDrop(oversized)) {
            returned += HopperBlockEntity.addItem(null, hopper, drop.copy(), null).getCount();
        }

        int stored = 0;
        for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
            stored += hopper.getItem(slot).getCount();
        }
        assertEquals(oversized.getCount(), stored + returned);
    }
}
