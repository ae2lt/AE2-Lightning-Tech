package com.moakiee.ae2lt.celestweave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.celestweave.module.MekanismProtectionSubmodule;

final class MekanismProtectionContractTest {

    @Test
    void protectionModulesTargetMekanismsExactDamageTypes() {
        assertEquals(
                "mekanism:radiation",
                MekanismProtectionSubmodule.RADIATION.damageType().location().toString());
        assertEquals(
                "mekanism:laser",
                MekanismProtectionSubmodule.LASER.damageType().location().toString());
    }

    @Test
    void optionalIntegrationSupportsRealAndPhaseLockedChestplates() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/integration/mekanism/MekanismArmorIntegration.java"));

        assertTrue(source.contains("Capabilities.RADIATION_SHIELDING"));
        assertTrue(source.contains("Capabilities.LASER_DISSIPATION"));
        assertTrue(source.contains("ModItems.CELESTWEAVE_CORE.get()"));
        assertTrue(source.contains("ModItems.PHASE_LOCK_PROJECTION.get()"));
        assertTrue(source.contains("projectionLink.armorId()"));
        assertTrue(source.contains("ArmorEnergyService.receiveExternalEnergy"));
        assertTrue(source.contains("IRadiationManager.INSTANCE"));
    }

    @Test
    void mekanismRecipesAreConditionedOnMekanism() throws Exception {
        for (String module : new String[]{"radiation", "laser"}) {
            String recipe = Files.readString(Path.of(
                    "src/main/resources/data/ae2lt/recipe/lightning_assembly/module_"
                            + module
                            + "_protection.json"));
            assertTrue(recipe.contains("\"type\": \"neoforge:mod_loaded\""));
            assertTrue(recipe.contains("\"modid\": \"mekanism\""));
        }
    }

    @Test
    void radiationRegenerationUsesBoundedOneSecondCadence() {
        assertFalse(MekanismProtectionRules.shouldRegenerate(19L, 1.0D, 0.001D, 10.0F, 20.0F));
        assertFalse(MekanismProtectionRules.shouldRegenerate(20L, 0.0001D, 0.001D, 10.0F, 20.0F));
        assertFalse(MekanismProtectionRules.shouldRegenerate(20L, 1.0D, 0.001D, 20.0F, 20.0F));
        assertTrue(MekanismProtectionRules.shouldRegenerate(20L, 1.0D, 0.001D, 10.0F, 20.0F));
        assertEquals(2.0F, MekanismProtectionRules.radiationHealing(0.0D));
        assertEquals(6.0F, MekanismProtectionRules.radiationHealing(0.5D));
        assertEquals(10.0F, MekanismProtectionRules.radiationHealing(1.0D));
    }

    @Test
    void radiationAdvancementRequiresHealthToActuallyIncrease() throws Exception {
        String integration = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/integration/mekanism/MekanismArmorIntegration.java"));
        String advancement = Files.readString(Path.of(
                "src/main/resources/data/ae2lt/advancement/main/radiation_assimilation.json"));

        assertTrue(integration.contains("float healthBefore = player.getHealth()"));
        assertTrue(integration.contains("player.getHealth() > healthBefore"));
        assertTrue(integration.contains("CelestweaveAdvancementService.awardRadiationAssimilation(player)"));
        assertTrue(advancement.contains("\"trigger\": \"minecraft:impossible\""));
        assertTrue(advancement.contains("\"frame\": \"challenge\""));
        assertTrue(advancement.contains("\"hidden\": true"));
        assertTrue(advancement.contains("\"radiation_healing\""));
    }

    @Test
    void laserConversionUsesActualAbsorbedJoulesAndConfiguredRatio() {
        assertEquals(1_000L, MekanismProtectionRules.absorbedJoules(1_000L, 1.0D));
        assertEquals(250L, MekanismProtectionRules.absorbedJoules(1_000L, 0.25D));
        assertEquals(400L, MekanismProtectionRules.joulesToForgeEnergy(1_000L, 2.5D));
        assertEquals(0L, MekanismProtectionRules.joulesToForgeEnergy(1_000L, 0.0D));
    }

    @Test
    void laserMixinIsOptionalAndHooksOnlyMekanismsDissipationCall() throws Exception {
        String config = Files.readString(Path.of("src/main/resources/ae2lt.mekanism.mixins.json"));
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/mekanism/TileEntityBasicLaserMixin.java"));

        assertTrue(config.contains("\"required\": false"));
        assertTrue(config.contains("\"TileEntityBasicLaserMixin\""));
        assertTrue(source.contains("ILaserDissipation;getDissipationPercent()D"));
        assertTrue(source.contains("@Local(ordinal = 1) long remainingJoules"));
    }
}
