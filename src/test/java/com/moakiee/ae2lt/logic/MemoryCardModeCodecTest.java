package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilMode;

class MemoryCardModeCodecTest {
    @Test
    void roundTripsCrystalCatalyzerMode() {
        var tag = new CompoundTag();

        MemoryCardConfigSupport.writeEnum(tag, "Mode", Mode.DUST);

        assertEquals(
                Mode.DUST,
                MemoryCardConfigSupport.readEnum(tag, "Mode", Mode.class, Mode.CRYSTAL));
    }

    @Test
    void roundTripsTeslaCoilMode() {
        var tag = new CompoundTag();

        MemoryCardConfigSupport.writeEnum(tag, "SelectedMode", TeslaCoilMode.EXTREME_HIGH_VOLTAGE);

        assertEquals(
                TeslaCoilMode.EXTREME_HIGH_VOLTAGE,
                MemoryCardConfigSupport.readEnum(
                        tag,
                        "SelectedMode",
                        TeslaCoilMode.class,
                        TeslaCoilMode.HIGH_VOLTAGE));
    }

    @Test
    void missingModeKeepsCurrentModeForOlderCards() {
        var tag = new CompoundTag();

        assertEquals(
                Mode.DUST,
                MemoryCardConfigSupport.readEnum(tag, "Mode", Mode.class, Mode.DUST));
        assertEquals(
                TeslaCoilMode.EXTREME_HIGH_VOLTAGE,
                MemoryCardConfigSupport.readEnum(
                        tag,
                        "SelectedMode",
                        TeslaCoilMode.class,
                        TeslaCoilMode.EXTREME_HIGH_VOLTAGE));
    }

    @Test
    void bothModeMachinesWireTheCodecIntoMemoryCardHooks() throws Exception {
        String catalyzer = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/CrystalCatalyzerBlockEntity.java"));
        String teslaCoil = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/TeslaCoilBlockEntity.java"));

        assertTrue(catalyzer.contains("writeEnum(tag, TAG_MODE, this.mode)"));
        assertTrue(catalyzer.contains("readEnum(\n"
                + "                            tag, TAG_MODE, Mode.class, this.mode)"));
        assertTrue(catalyzer.contains("logic.onStateChanged()"));

        assertTrue(teslaCoil.contains("writeEnum(tag, TAG_SELECTED_MODE, selectedMode)"));
        assertTrue(teslaCoil.contains("TAG_SELECTED_MODE, TeslaCoilMode.class, selectedMode"));
        assertTrue(teslaCoil.contains("logic.onStateChanged()"));
    }
}
