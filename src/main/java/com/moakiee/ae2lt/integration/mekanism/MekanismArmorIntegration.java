package com.moakiee.ae2lt.integration.mekanism;

import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

import mekanism.api.lasers.ILaserDissipation;
import mekanism.api.math.FloatingLong;
import mekanism.api.radiation.IRadiationManager;
import mekanism.api.radiation.capability.IRadiationShielding;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.util.UnitDisplayUtils;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.celestweave.MekanismProtectionRules;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.module.MekanismProtectionSubmodule;
import com.moakiee.ae2lt.celestweave.phase.CelestweaveEquipmentAccess;
import com.moakiee.ae2lt.celestweave.service.CelestweaveAdvancementService;
import com.moakiee.ae2lt.celestweave.service.ArmorEnergyService;
import com.moakiee.ae2lt.celestweave.state.ArmorRuntimeRegistry;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;

/**
 * Optional native Mekanism protection capabilities for Celestweave.
 *
 * <p>Damage-event cancellation alone is too late for Mekanism's mechanics: radiation has already
 * been added to the entity and a laser has already passed its dissipation phase. These providers
 * participate in Mekanism's own calculations first; the generic armor damage capability remains
 * a final safety net for already accumulated radiation and direct damage calls.
 */
public final class MekanismArmorIntegration {
    private static final IRadiationShielding FULL_RADIATION_SHIELDING = () -> 1.0D;
    private static final CelestweaveLaserDissipation FULL_LASER_DISSIPATION =
            new CelestweaveLaserDissipation();

    public static final class CelestweaveLaserDissipation implements ILaserDissipation {
        private CelestweaveLaserDissipation() {
        }

        @Override
        public double getDissipationPercent() {
            return 1.0D;
        }

        @Override
        public double getRefractionPercent() {
            return 0.0D;
        }
    }

    private MekanismArmorIntegration() {
    }

    /**
     * Forge 1.20.1 has no RegisterCapabilitiesEvent.registerItem; item capabilities
     * are attached per-stack through AttachCapabilitiesEvent on the game bus.
     */
    public static void attachCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        if (!stack.is(ModItems.CELESTWEAVE_CORE.get())
                && !stack.is(ModItems.PHASE_LOCK_PROJECTION.get())) {
            return;
        }
        event.addCapability(
                ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "mekanism_armor_protection"),
                new ICapabilityProvider() {
                    @Override
                    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
                        if (cap == Capabilities.RADIATION_SHIELDING
                                && isProtectionActive(
                                        stack, MekanismProtectionSubmodule.RADIATION.id())) {
                            return LazyOptional.of(() -> FULL_RADIATION_SHIELDING).cast();
                        }
                        if (cap == Capabilities.LASER_DISSIPATION
                                && isProtectionActive(stack, MekanismProtectionSubmodule.LASER.id())) {
                            return LazyOptional.of(() -> FULL_LASER_DISSIPATION).cast();
                        }
                        return LazyOptional.empty();
                    }
                });
    }

    static boolean isProtectionActive(ItemStack stack, String submoduleId) {
        var projectionLink = ModDataComponents.PHASE_LOCK_PROJECTION_LINK.get(stack);
        if (projectionLink != null) {
            return ArmorRuntimeRegistry.isSubmoduleRuntimeActive(projectionLink.armorId(), submoduleId);
        }
        return CelestweaveArmorState.isSubmoduleRuntimeActive(stack, submoduleId);
    }

    public static void tickRadiationRegeneration(ServerPlayer player) {
        var radiationManager = IRadiationManager.INSTANCE;
        double radiationLevel = radiationManager.getRadiationLevel(player);
        // 1.20.1: RadiationScale is a nested enum of RadiationManager and IRadiationManager
        // has no minRadiationMagnitude(); the threshold lives on RadiationManager.MIN_MAGNITUDE.
        if (!MekanismProtectionRules.shouldRegenerate(
                player.level().getGameTime(),
                radiationLevel,
                RadiationManager.MIN_MAGNITUDE,
                player.getHealth(),
                player.getMaxHealth())) {
            return;
        }
        float healthBefore = player.getHealth();
        player.heal(MekanismProtectionRules.radiationHealing(
                RadiationManager.RadiationScale.getScaledDoseSeverity(radiationLevel)));
        if (player.getHealth() > healthBefore) {
            CelestweaveAdvancementService.awardRadiationAssimilation(player);
        }
    }

    /**
     * Called from the optional laser mixin at Mekanism's dissipation calculation, before the
     * absorbed Joules are removed from the beam.
     */
    public static void absorbLaserEnergy(
            ILaserDissipation dissipation,
            LivingEntity target,
            FloatingLong availableEnergy,
            double dissipationPercent) {
        if (dissipation != FULL_LASER_DISSIPATION || !(target instanceof ServerPlayer player)) {
            return;
        }
        ItemStack chest = CelestweaveEquipmentAccess.findArmor(player, EquipmentSlot.CHEST);
        if (chest.isEmpty()
                || !CelestweaveArmorState.isSubmoduleRuntimeActive(
                        chest,
                        MekanismProtectionSubmodule.LASER.id())) {
            return;
        }
        if (availableEnergy == null
                || availableEnergy.isZero()
                || !Double.isFinite(dissipationPercent)
                || dissipationPercent <= 0.0D) {
            return;
        }
        FloatingLong absorbedEnergy = dissipationPercent >= 1.0D
                ? availableEnergy
                : availableEnergy.multiply(dissipationPercent);
        long forgeEnergy = UnitDisplayUtils.EnergyUnit.FORGE_ENERGY
                .convertFrom(absorbedEnergy)
                .longValue();
        ArmorEnergyService.receiveExternalEnergy(player, chest, forgeEnergy);
    }
}
