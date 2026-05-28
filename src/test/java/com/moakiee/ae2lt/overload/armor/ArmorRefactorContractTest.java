package com.moakiee.ae2lt.overload.armor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ArmorRefactorContractTest {

    @Test
    void baseArmorItemDelegatesServerTickToArmorTickService() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/BaseOverloadArmorItem.java"));

        assertTrue(source.contains("ArmorTickService.tickEquipped"));
        assertTrue(source.contains("OverloadArmorState.ensureArmorId"));
    }

    @Test
    void overloadArmorStateUsesPersistentAndRuntimeFacades() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/overload/armor/OverloadArmorState.java"));

        assertTrue(source.contains("ArmorPersistentData"));
        assertTrue(source.contains("ArmorRuntimeRegistry"));
    }
}
