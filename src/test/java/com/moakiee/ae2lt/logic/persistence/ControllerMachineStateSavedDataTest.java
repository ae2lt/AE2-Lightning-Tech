package com.moakiee.ae2lt.logic.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moakiee.ae2lt.logic.persistence.ControllerMachineStateSavedData.MachineType;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ControllerMachineStateSavedDataTest {
    @Test
    void stateRoundTripsByMachineTypeAndUuid() {
        var id = UUID.randomUUID();
        var data = new ControllerMachineStateSavedData();
        var tianshu = new CompoundTag();
        tianshu.putLong("InFlight", 42L);
        var matrix = new CompoundTag();
        matrix.putDouble("Heat", 0.75D);

        data.setState(MachineType.TIANSHU, id, tianshu);
        data.setState(MachineType.MATRIX, id, matrix);
        var root = data.save(new CompoundTag(), null);
        var restored = ControllerMachineStateSavedData.load(root, null);

        assertEquals(42L, restored.getState(MachineType.TIANSHU, id).getLong("InFlight"));
        assertEquals(0.75D, restored.getState(MachineType.MATRIX, id).getDouble("Heat"));
        var externalCopy = restored.getState(MachineType.TIANSHU, id);
        externalCopy.putLong("InFlight", 0L);
        assertEquals(42L, restored.getState(MachineType.TIANSHU, id).getLong("InFlight"));
    }

    @Test
    void onlyOneLoadedControllerCanClaimAUuid() {
        var data = new ControllerMachineStateSavedData();
        var id = UUID.randomUUID();

        assertTrue(data.claim(MachineType.TIANSHU, id, "minecraft:overworld", 10L));
        assertTrue(data.claim(MachineType.TIANSHU, id, "minecraft:overworld", 10L));
        assertFalse(data.claim(MachineType.TIANSHU, id, "minecraft:overworld", 11L));
        assertTrue(data.claim(MachineType.MATRIX, id, "minecraft:overworld", 11L));

        data.release(MachineType.TIANSHU, id, "minecraft:overworld", 10L);
        assertTrue(data.claim(MachineType.TIANSHU, id, "minecraft:the_nether", 11L));
    }

    @Test
    void controllerIdentityRoundTripsThroughCustomDataPayload() {
        var id = UUID.randomUUID();
        var tag = new CompoundTag();

        ControllerMachineIdentity.write(tag, id);

        assertEquals(id, ControllerMachineIdentity.read(tag));
        assertEquals(id, ControllerMachineIdentity.read(tag.copy()));
    }
}
