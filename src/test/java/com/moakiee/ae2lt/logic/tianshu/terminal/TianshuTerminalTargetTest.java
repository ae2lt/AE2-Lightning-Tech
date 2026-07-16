package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class TianshuTerminalTargetTest {
    @Test
    void identityIncludesMachineDimensionAndControllerPosition() {
        var id = UUID.randomUUID();
        var target = new TianshuTerminalTarget(id, Level.OVERWORLD, new BlockPos(1, 2, 3));

        assertEquals(id, target.machineId());
        assertEquals(Level.OVERWORLD, target.dimension());
        assertEquals(new BlockPos(1, 2, 3), target.controllerPos());
        assertThrows(IllegalArgumentException.class,
                () -> new TianshuTerminalTarget(id, Level.OVERWORLD, null));
    }
}
