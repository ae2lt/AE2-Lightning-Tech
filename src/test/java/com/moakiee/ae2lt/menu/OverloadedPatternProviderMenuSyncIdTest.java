package com.moakiee.ae2lt.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;

import org.junit.jupiter.api.Test;

import appeng.menu.guisync.GuiSync;

class OverloadedPatternProviderMenuSyncIdTest {
    private static final int AE2LT_SYNC_ID_MIN = 22000;
    private static final int AE2LT_SYNC_ID_MAX = 22099;

    @Test
    void customFieldsUseDedicatedAddonSafeRange() {
        int synchronizedFields = 0;
        for (var field : OverloadedPatternProviderMenu.class.getDeclaredFields()) {
            var annotation = field.getAnnotation(GuiSync.class);
            if (annotation == null) {
                continue;
            }
            synchronizedFields++;
            int id = annotation.value();
            assertTrue(
                    id >= AE2LT_SYNC_ID_MIN && id <= AE2LT_SYNC_ID_MAX,
                    () -> field.getName() + " uses collision-prone GUI sync ID " + id);
        }
        assertEquals(11, synchronizedFields);
    }

    @Test
    void classHierarchyHasNoDuplicateSyncIdsWithoutMixins() {
        var ids = new HashSet<Short>();
        Class<?> type = OverloadedPatternProviderMenu.class;
        while (type != Object.class) {
            for (var field : type.getDeclaredFields()) {
                var annotation = field.getAnnotation(GuiSync.class);
                if (annotation != null) {
                    assertTrue(
                            ids.add(annotation.value()),
                            () -> "Duplicate GUI sync ID " + annotation.value()
                                    + " on " + field.getDeclaringClass().getName() + "." + field.getName());
                }
            }
            type = type.getSuperclass();
        }
    }
}
