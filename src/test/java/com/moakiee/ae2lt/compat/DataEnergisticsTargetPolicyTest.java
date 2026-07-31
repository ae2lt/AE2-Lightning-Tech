package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DataEnergisticsTargetPolicyTest {
    @Test
    void leavesTheExternalEjectCapabilityMixinAlone() {
        assertFalse(DataEnergisticsTargetPolicy.shouldCancel(
                java.util.List.of("net.neoforged.neoforge.capabilities.BlockCapability"),
                "com.fish_dan_.data_energistics.mixin.ae2lt.Ae2ltEjectCapabilityMixin"));
    }

    @Test
    void catchesOwnedTargetsOutsideTheDedicatedCompatibilityPackage() {
        assertTrue(DataEnergisticsTargetPolicy.shouldCancel(
                java.util.List.of("com.moakiee.thunderbolt.SomeInternalClass"),
                "com.fish_dan_.data_energistics.mixin.core.FutureMixin"));
    }

    @Test
    void leavesDataEnergisticsExternalMixinsAndOtherModsAlone() {
        assertFalse(DataEnergisticsTargetPolicy.shouldCancel(
                java.util.List.of("appeng.me.service.PathingService"),
                "com.fish_dan_.data_energistics.mixin.core.PathingServiceMixin"));
        assertFalse(DataEnergisticsTargetPolicy.shouldCancel(
                java.util.List.of("com.moakiee.ae2lt.logic.SomeInternalClass"),
                "example.othermod.mixin.Ae2LtMixin"));
    }

    @Test
    void recognizesOwnedTargetsInMixinNameFormats() {
        assertTrue(DataEnergisticsTargetPolicy.isOwnedTarget(
                "com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic"));
        assertTrue(DataEnergisticsTargetPolicy.isOwnedTarget(
                "com/moakiee/thunderbolt/api/eject/EjectCapabilityRegistry"));
        assertTrue(DataEnergisticsTargetPolicy.isOwnedTarget(
                "Lcom/moakiee/ae2lt/client/WirelessConnectorRenderer;"));
    }

    @Test
    void leavesExternalTargetsToTheirOwners() {
        assertFalse(DataEnergisticsTargetPolicy.isOwnedTarget(
                "appeng.me.service.PathingService"));
        assertFalse(DataEnergisticsTargetPolicy.isOwnedTarget(
                "net.neoforged.neoforge.capabilities.BlockCapability"));
        assertFalse(DataEnergisticsTargetPolicy.isOwnedTarget(null));
    }
}
