package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;

import appeng.api.stacks.AEItemKey;

class OverloadedInterfaceImportScanBufferTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void successiveTargetsNeverShareScannedAmounts() {
        var scans = new OverloadedInterfaceBlockEntity.ImportScanBuffer();
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        var first = scans.acquire();
        first.add(stone, 64);
        scans.release(first);

        var second = scans.acquire();
        assertSame(first, second, "small scans should reuse their map allocation");
        assertTrue(second.isEmpty());
        assertEquals(0, second.get(stone));
        second.add(dirt, 5);
        scans.release(second);

        var third = scans.acquire();
        assertTrue(third.isEmpty());
        third.add(stone, 3);
        assertEquals(3, third.get(stone));
        assertEquals(0, third.get(dirt));
        assertEquals(1, third.size());
        scans.release(third);
    }

    @Test
    void largeVariantInventoriesAreNotRetainedForSmallTargets() {
        var scans = new OverloadedInterfaceBlockEntity.ImportScanBuffer();
        var large = scans.acquire();
        for (int index = 0; index < 257; index++) {
            var stack = new ItemStack(Items.STONE);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("scan-" + index));
            large.add(AEItemKey.of(stack), 1);
        }
        assertEquals(257, large.size());
        scans.release(large);
        var next = scans.acquire();
        assertNotSame(large, next, "large maps must not remain in the reuse pool");
        assertTrue(next.isEmpty());
        scans.release(next);
    }

    @Test
    void nestedStorageCallbacksCannotClearTheOuterSnapshot() {
        var scans = new OverloadedInterfaceBlockEntity.ImportScanBuffer();
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        var outer = scans.acquire();
        outer.add(stone, 64);
        var nested = scans.acquire();
        assertNotSame(outer, nested);
        nested.add(dirt, 5);
        scans.release(nested);
        assertEquals(64, outer.get(stone));
        assertEquals(0, outer.get(dirt));
        scans.release(outer);
        var next = scans.acquire();
        assertTrue(next.isEmpty());
        scans.release(next);
    }
}
