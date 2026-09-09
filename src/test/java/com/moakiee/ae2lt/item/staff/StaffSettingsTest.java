package com.moakiee.ae2lt.item.staff;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaffSettingsTest {
    @Test
    void timeFormulaPreservesFractionalOriginalTimeForEveryCombination() {
        for (int b = 0; b < 5; b++) for (int k = 0; k < 6; k++) {
            var settings = new StaffSettings(0, 0, 0, b, k, true, false, false);
            for (float original : new float[]{1F / 40, 9F / 500, .2F, 2F}) {
                double expectedTime = settings.baseTicks() + settings.timeScale() / original;
                assertEquals(1 / expectedTime, settings.transformProgress(original), 1e-5);
            }
        }
        var sevenTicks = new StaffSettings(0, 0, 0, 1, 2, true, false, false);
        assertEquals(1F / 7, sevenTicks.transformProgress(1F / 40), 1e-7);
    }

    @Test
    void preservesUnbreakableInvalidAndInstantResults() {
        var enabled = new StaffSettings(0, 0, 0, 1, 0, true, false, false);
        assertEquals(0, enabled.transformProgress(0));
        assertEquals(-1, enabled.transformProgress(-1));
        assertTrue(Float.isNaN(enabled.transformProgress(Float.NaN)));
        assertEquals(Float.NEGATIVE_INFINITY, enabled.transformProgress(Float.NEGATIVE_INFINITY));
        assertEquals(1F / 3, enabled.transformProgress(Float.POSITIVE_INFINITY));
        var instant = new StaffSettings(0, 0, 0, 0, 0, true, false, false);
        assertEquals(Float.POSITIVE_INFINITY, instant.transformProgress(Float.POSITIVE_INFINITY));
    }

    @Test
    void disabledIsIdentityAndDoesNotChangeOtherSettings() {
        assertEquals(.025F, StaffSettings.DEFAULT.transformProgress(.025F));
        assertFalse(StaffSettings.DEFAULT.smelting());
        assertFalse(StaffSettings.DEFAULT.collection());
        assertEquals(0, StaffSettings.DEFAULT.land());
        assertEquals(0, StaffSettings.DEFAULT.mekanism());
        assertEquals(0, StaffSettings.DEFAULT.harvest());
    }

    @Test
    void settingsRoundTripAndRejectInvalidInputs() {
        var settings = new StaffSettings(2, 6, 2, 4, 0, true, true, true, 4, 6, 2);
        var json = StaffSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        assertEquals(settings, StaffSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(StaffSettings.DEFAULT, StaffSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
        assertTrue(StaffSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"scale\":-1}")).isError());
        for (String field : new String[]{"combat", "damage", "execution"}) {
            assertTrue(StaffSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"" + field + "\":-1}")).isError());
            assertTrue(StaffSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"" + field + "\":7}")).isError());
        }
        var old = StaffSettings.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"land\":1,\"collection\":true}")).getOrThrow();
        assertEquals(0, old.combat());
        assertEquals(2, old.damage());
        assertEquals(1, old.execution());
        var cycle = settings;
        for (int i = 0; i < 5; i++) cycle = cycle.cycle(8);
        for (int i = 0; i < 7; i++) cycle = cycle.cycle(9);
        for (int i = 0; i < 3; i++) cycle = cycle.cycle(10);
        assertEquals(settings, cycle);
        assertTrue(StaffSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"mekanism\":7}")).isError());
    }
}
