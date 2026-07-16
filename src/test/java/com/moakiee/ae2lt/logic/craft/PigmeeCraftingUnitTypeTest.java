package com.moakiee.ae2lt.logic.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.block.crafting.AbstractCraftingUnitBlock;
import appeng.blockentity.crafting.CraftingBlockEntity;

import com.moakiee.ae2lt.block.PigmeeMentalmathUnitBlock;
import com.moakiee.ae2lt.blockentity.PigmeeMentalmathUnitBlockEntity;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPool;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPoolHost;

import java.lang.reflect.Modifier;

import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.junit.jupiter.api.Test;

class PigmeeCraftingUnitTypeTest {
    @Test
    void usesVanillaBaseThreadInsteadOfAddingACoprocessor() {
        assertEquals(256L, PigmeeCraftingUnitType.INSTANCE.getStorageBytes());
        assertEquals(0, PigmeeCraftingUnitType.INSTANCE.getAcceleratorThreads());
    }

    @Test
    void declaresItsOwnStaticFacingProperty() throws ReflectiveOperationException {
        // Reading the field value would initialize Minecraft's registries, which this plain test
        // JVM deliberately does not bootstrap. Its declaration is enough to guard the Pigmee-only
        // state while the inheritance assertion below covers vanilla FORMED/POWERED state.
        var field = PigmeeMentalmathUnitBlock.class.getDeclaredField("FACING");
        assertEquals(DirectionProperty.class, field.getType());
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    @Test
    void participatesInVanillaCraftingCpuDiscovery() {
        assertTrue(AbstractCraftingUnitBlock.class.isAssignableFrom(PigmeeMentalmathUnitBlock.class));
        assertTrue(CraftingBlockEntity.class.isAssignableFrom(PigmeeMentalmathUnitBlockEntity.class));
        assertFalse(TimeWheelCraftingCpuPoolHost.class.isAssignableFrom(PigmeeMentalmathUnitBlockEntity.class),
                "the legacy migration pool must not be exposed as a second crafting CPU");
    }

    @Test
    void retainsAnIsolatedLegacyPoolForUpgradeRecovery() {
        assertTrue(java.util.Arrays.stream(PigmeeMentalmathUnitBlockEntity.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == TimeWheelCraftingCpuPool.class));
        var lifecycleMethods = java.util.Arrays.stream(PigmeeMentalmathUnitBlockEntity.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(lifecycleMethods.containsAll(java.util.Set.of(
                "loadTag", "saveAdditional", "onReady", "addAdditionalDrops", "clearContent")));
    }
}
