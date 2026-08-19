package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.celestweave.module.MultidimensionalProtectionSubmodule;
import com.moakiee.ae2lt.celestweave.module.ResistanceSubmodule;
import com.moakiee.ae2lt.celestweave.module.UndyingSubmodule;

final class MultidimensionalProtectionContractTest {
    @Test
    void occupiesBothShieldAndUndyingInstallGroups() {
        var groups = MultidimensionalProtectionSubmodule.INSTANCE.installGroupIds();

        assertTrue(groups.contains(ResistanceSubmodule.INSTALL_GROUP));
        assertTrue(groups.contains(UndyingSubmodule.INSTANCE.installGroupId()));
    }

    @Test
    void armorInstallationRejectsAnyIntersectingGroupInBothDirections() throws Exception {
        String state = source("src/main/java/com/moakiee/ae2lt/celestweave/CelestweaveArmorState.java");

        assertTrue(state.contains("Set<String> groupIds = resolveSubmoduleGroupIds(candidate)"));
        assertTrue(state.contains("groupIds.stream().anyMatch(installedGroups::contains)"));
        assertFalse(state.contains("installedInGroup > installedSameId"));
    }

    @Test
    void shieldAndLastStandReuseExistingHandlersWithoutResourcePayment() throws Exception {
        String item = source(
                "src/main/java/com/moakiee/ae2lt/item/MultidimensionalProtectionSubmoduleItem.java");
        String shield = source(
                "src/main/java/com/moakiee/ae2lt/celestweave/CelestweaveArmorDamageHandler.java");
        String undying = source(
                "src/main/java/com/moakiee/ae2lt/celestweave/CelestweaveArmorUndyingHandler.java");

        assertTrue(item.contains("new DeviceCapability.StagedMitigation("));
        assertTrue(item.contains("new DeviceCapability.LastStandTuning(0L, 0)"));
        assertFalse(item.contains("PassiveDrain"));

        int shieldFree = shield.indexOf(
                "if (MultidimensionalProtectionSubmodule.ID.equals(staged.stage()))");
        int shieldPayment = shield.indexOf("ArmorEnergyService.consumeActiveCostPayment(", shieldFree);
        assertTrue(shieldFree >= 0 && shieldFree < shieldPayment);
        assertTrue(shield.substring(shieldFree, shieldPayment).contains("return true"));

        int undyingFree = undying.indexOf(
                "if (MultidimensionalProtectionSubmodule.ID.equals(active.submoduleId()))");
        int ordinaryBranch = undying.indexOf("int comboIndex =", undyingFree);
        int undyingPayment = undying.indexOf("ArmorEnergyService.consumeActiveCostPayment(", undyingFree);
        assertTrue(undyingFree >= 0 && undyingFree < ordinaryBranch);
        assertTrue(ordinaryBranch < undyingPayment);
        String freeBranch = undying.substring(undyingFree, ordinaryBranch);
        assertTrue(freeBranch.contains("recordProtectionWindow(player, now)"));
        assertTrue(freeBranch.contains("restoreSurvivalState(player)"));
        assertFalse(freeBranch.contains("ArmorLightningService"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
