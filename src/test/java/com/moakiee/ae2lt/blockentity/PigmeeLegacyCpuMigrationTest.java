package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPoolHost;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class PigmeeLegacyCpuMigrationTest {
    @Test
    void migrationWaitsForAnActiveServerGrid() {
        assertTrue(PigmeeMentalmathUnitBlockEntity.isLegacyMigrationReady(
                true, true, true, true));
        assertFalse(PigmeeMentalmathUnitBlockEntity.isLegacyMigrationReady(
                false, true, true, true));
        assertFalse(PigmeeMentalmathUnitBlockEntity.isLegacyMigrationReady(
                true, false, true, true));
        assertFalse(PigmeeMentalmathUnitBlockEntity.isLegacyMigrationReady(
                true, true, false, true));
        assertFalse(PigmeeMentalmathUnitBlockEntity.isLegacyMigrationReady(
                true, true, true, false));
    }

    @Test
    void loadingWithoutLegacyTagClearsPreviouslyDecodedPoolState() {
        var host = new FakeHost();
        var pool = new TimeWheelCraftingCpuPool(host, 256L, 1);
        host.pool = pool;

        var ownerTag = new CompoundTag();
        var poolTag = new CompoundTag();
        poolTag.putInt("version", 1);
        var cpus = new ListTag();
        var cpu = new CompoundTag();
        cpu.putUUID("id", UUID.randomUUID());
        cpu.putLong("reservedBytes", 1L);
        cpu.put("state", new CompoundTag());
        cpus.add(cpu);
        poolTag.put("cpus", cpus);
        ownerTag.put("CpuPool", poolTag);

        assertTrue(PigmeeMentalmathUnitBlockEntity.loadLegacyCpuState(
                pool, ownerTag, null));
        assertFalse(pool.getActiveCpus().isEmpty());

        assertFalse(PigmeeMentalmathUnitBlockEntity.loadLegacyCpuState(
                pool, new CompoundTag(), null));
        assertTrue(pool.getActiveCpus().isEmpty());
    }

    private static final class FakeHost implements TimeWheelCraftingCpuPoolHost {
        private TimeWheelCraftingCpuPool pool;

        @Override
        public TimeWheelCraftingCpuPool getTimeWheelCraftingCpuPool() {
            return pool;
        }

        @Override
        public boolean isCpuActive() {
            return false;
        }

        @Override
        public IGrid getGrid() {
            return null;
        }

        @Override
        public IActionSource getActionSource() {
            return IActionSource.empty();
        }

        @Override
        public Level getLevel() {
            return null;
        }

        @Override
        public void markCpuDirty() {
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Pigmee migration test");
        }
    }
}
