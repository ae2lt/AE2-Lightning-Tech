package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.util.SettingsFrom;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.machine.common.LightningCollapseMatrixHost;

class MemoryCardMatrixCountTest {
    @Test
    void memoryCardRoundTripPreservesInstalledMatrixCount() {
        var output = new CompoundTag();
        MemoryCardConfigSupport.exportMemoryCardSettings(
                SettingsFrom.MEMORY_CARD,
                output,
                tag -> MemoryCardConfigSupport.writeMatrixCount(tag, matrixHostWithCount(32)));

        var restoredCount = new AtomicInteger(-1);
        MemoryCardConfigSupport.importMemoryCardSettings(
                SettingsFrom.MEMORY_CARD,
                output,
                tag -> MemoryCardConfigSupport.restoreMatrixCount(tag, null, restoringHost(restoredCount)));

        assertEquals(32, restoredCount.get());
    }

    @Test
    void nonMemoryCardExportsDoNotPersistMatrixCount() {
        var output = new CompoundTag();
        MemoryCardConfigSupport.exportMemoryCardSettings(
                SettingsFrom.DISMANTLE_ITEM,
                output,
                tag -> MemoryCardConfigSupport.writeMatrixCount(tag, matrixHostWithCount(32)));

        assertFalse(output.contains("AE2LTMachineConfig"));
    }

    @Test
    void exportedMatrixCountUsesTheDedicatedMachineConfigTag() {
        var output = new CompoundTag();
        MemoryCardConfigSupport.exportMemoryCardSettings(
                SettingsFrom.MEMORY_CARD,
                output,
                tag -> MemoryCardConfigSupport.writeMatrixCount(tag, matrixHostWithCount(7)));

        CompoundTag machineConfig = MemoryCardConfigSupport.readCustomTag(output);
        assertNotNull(machineConfig);
        assertEquals(7, machineConfig.getInt("LightningCollapseMatrixCount"));
    }

    private static LightningCollapseMatrixHost matrixHostWithCount(int count) {
        return new StubMatrixHost() {
            @Override
            public int getInstalledMatrixCount() {
                return count;
            }
        };
    }

    private static LightningCollapseMatrixHost restoringHost(AtomicInteger restoredCount) {
        return new StubMatrixHost() {
            @Override
            public int restoreMatricesFromMemoryCard(net.minecraft.world.entity.player.Player player,
                                                     int requestedCount) {
                restoredCount.set(requestedCount);
                return 0;
            }
        };
    }

    private abstract static class StubMatrixHost implements LightningCollapseMatrixHost {
        @Override
        public IItemHandlerModifiable getMatrixInventory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getMatrixSlot() {
            return 0;
        }
    }
}
