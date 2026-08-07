package com.moakiee.ae2lt.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.moakiee.ae2lt.blockentity.ExtendedPatternProviderCapacity;
import com.moakiee.thunderbolt.core.util.FastWildcardMatcher;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

public final class AE2LTCommonConfig {
    public static final int CURRENT_CONFIG_VERSION = 3;
    private static final List<String> DEFAULT_EASTER_EGG_WEIGHTS = List.of(
            "avaritia:infinity_ingot=1200",
            "mekanism_extras:qio_drive_singularity=1200",
            "modern_industrialization:quantum_upgrade=1200",
            "bigreactors:inanite_block=776",
            "draconicevolution:chaotic_core=760",
            "occultism:celestial_chalice=744",
            "appflux:core_256m=460",
            "advanced_ae:data_entangler=455",
            "advanced_ae:quantum_multi_threader=445",
            "megacells:cell_component_256m=440",
            "ae2omnicells:quantum_omni_cell_component_256m=435",
            "mekanism_extras:infinite_induction_cell=415",
            "mekanism_extras:infinite_induction_provider=415",
            "mekanism:pellet_antimatter=252",
            "mekanism_extras:infinite_control_circuit=240",
            "ars_nouveau:wilden_tribute=204",
            "minecraft:elytra=138",
            "minecraft:heavy_core=132",
            "minecraft:dragon_head=114",
            "ae2:256k_crafting_storage=98",
            "mekanism:ultimate_induction_cell=76",
            "mekanism:ultimate_induction_provider=76",
            "pneumaticcraft:micromissiles=72",
            "minecraft:dragon_egg=54",
            "minecraft:heart_of_the_sea=40",
            "minecraft:echo_shard=36",
            "minecraft:nether_star=32",
            "minecraft:torchflower=8",
            "minecraft:recovery_compass=7",
            "minecraft:slime_ball=4");
    private static final Set<ResourceLocation> DEFAULT_EASTER_EGG_CANDIDATE_IDS = Set.copyOf(
            DEFAULT_EASTER_EGG_WEIGHTS.stream()
                    .map(entry -> ResourceLocation.of(entry.substring(0, entry.lastIndexOf('=')), ':'))
                    .toList());
    private static final List<String> DEFAULT_BATCH_COPY_LIMITED_BLOCKS = List.of(
            "neoecoae:crafting_pattern_bus",
            "sophisticated*:*");

    public static final ForgeConfigSpec SPEC;

    private static final Values VALUES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        VALUES = new Values(builder);
        SPEC = builder.build();
    }

    private AE2LTCommonConfig() {
    }

    public static int lightningCollectorCooldownTicks() {
        return VALUES.lightningCollectorCooldownTicks.get();
    }

    public static int electroChimeMaxCatalysis() {
        return VALUES.electroChimeMaxCatalysis.get();
    }

    public static boolean overloadTntEnableTerrainDamage() {
        return VALUES.overloadTntEnableTerrainDamage.get();
    }

    public static boolean overloadTntEnableMysteriousCellEasterEgg() {
        return VALUES.overloadTntEnableMysteriousCellEasterEgg.get();
    }

    public static boolean easterEggEnabled() {
        return VALUES.easterEggEnabled.get();
    }

    public static String easterEggItem() {
        return VALUES.easterEggItem.get();
    }

    public static int easterEggWeight() {
        return VALUES.easterEggWeight.get();
    }

    public static Map<ResourceLocation, Integer> easterEggWeights() {
        Map<ResourceLocation, Integer> parsed = new LinkedHashMap<>();
        for (String entry : VALUES.easterEggWeights.get()) {
            int separator = entry.lastIndexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, separator).strip());
            if (id == null) {
                continue;
            }
            try {
                parsed.put(id, Integer.parseInt(entry.substring(separator + 1).strip()));
            } catch (NumberFormatException ignored) {
                // The config spec rejects malformed entries; keep this tolerant for live reloads.
            }
        }
        return Map.copyOf(parsed);
    }

    public static boolean isDefaultEasterEggCandidate(ResourceLocation id) {
        return DEFAULT_EASTER_EGG_CANDIDATE_IDS.contains(id);
    }

    public static int overloadTntGlobalBlockBudgetPerTick() {
        return VALUES.overloadTntGlobalBlockBudgetPerTick.get();
    }

    public static int overloadTntGlobalLightningBudgetPerTick() {
        return VALUES.overloadTntGlobalLightningBudgetPerTick.get();
    }

    public static boolean shulkerBulletCollectionEnabled() {
        return VALUES.shulkerBulletCollectionEnabled.get();
    }

    public static double floatingMatterRiseSpeed() {
        return VALUES.floatingMatterRiseSpeed.get();
    }

    public static double floatingMatterDespawnHeightMultiplier() {
        return VALUES.floatingMatterDespawnHeightMultiplier.get();
    }

    public static int overloadedControllerChannelsPerController() {
        return VALUES.overloadedControllerChannelsPerController.get();
    }

    public static double overloadedControllerPassiveAePerTick() {
        return VALUES.overloadedControllerPassiveAePerTick.get();
    }

    public static int wirelessConnectorMaxDistance() {
        return VALUES.wirelessConnectorMaxDistance.get();
    }

    public static int extendedPatternProviderPages() {
        return ExtendedPatternProviderCapacity.clampPages(VALUES.extendedPatternProviderPages.get());
    }

    public static List<? extends String> batchCopyLimitedBlocks() {
        return VALUES.batchCopyLimitedBlocks.get();
    }

    public static int overloadFactoryParallelPerMatrix() {
        return VALUES.overloadFactoryParallelPerMatrix.get();
    }

    public static long overloadFactoryEnergyCapacity() {
        return VALUES.overloadFactoryEnergyCapacity.get();
    }

    public static long overloadFactoryFePerTickNoSpeedCard() {
        return VALUES.overloadFactoryFePerTickNoSpeedCard.get();
    }

    public static long overloadFactoryFePerTickOneSpeedCard() {
        return VALUES.overloadFactoryFePerTickOneSpeedCard.get();
    }

    public static long overloadFactoryFePerTickTwoSpeedCards() {
        return VALUES.overloadFactoryFePerTickTwoSpeedCards.get();
    }

    public static long overloadFactoryFePerTickThreeSpeedCards() {
        return VALUES.overloadFactoryFePerTickThreeSpeedCards.get();
    }

    public static long overloadFactoryFePerTickFourSpeedCards() {
        return VALUES.overloadFactoryFePerTickFourSpeedCards.get();
    }

    public static boolean artificialLightningTriggerFromHotbar() {
        return VALUES.artificialLightningTriggerFromHotbar.get();
    }

    public static boolean artificialLightningTriggerFromBackpack() {
        return VALUES.artificialLightningTriggerFromBackpack.get();
    }

    public static int lightningCollectorHvBaseMin() {
        return VALUES.lightningCollectorHvBaseMin.get();
    }

    public static int lightningCollectorHvBaseMax() {
        return VALUES.lightningCollectorHvBaseMax.get();
    }

    public static int lightningCollectorEhvBaseMin() {
        return VALUES.lightningCollectorEhvBaseMin.get();
    }

    public static int lightningCollectorEhvBaseMax() {
        return VALUES.lightningCollectorEhvBaseMax.get();
    }

    public static int lightningCollectorHvCrystalStart() {
        return VALUES.lightningCollectorHvCrystalStart.get();
    }

    public static int lightningCollectorHvCrystalEnd() {
        return VALUES.lightningCollectorHvCrystalEnd.get();
    }

    public static int lightningCollectorEhvCrystalStart() {
        return VALUES.lightningCollectorEhvCrystalStart.get();
    }

    public static int lightningCollectorEhvCrystalEnd() {
        return VALUES.lightningCollectorEhvCrystalEnd.get();
    }

    public static int lightningCollectorPerfectHvOutput() {
        return VALUES.lightningCollectorPerfectHvOutput.get();
    }

    public static int lightningCollectorPerfectEhvOutput() {
        return VALUES.lightningCollectorPerfectEhvOutput.get();
    }

    public static int electroChimeCatalysisPerStrikeMin() {
        return VALUES.electroChimeCatalysisPerStrikeMin.get();
    }

    public static int electroChimeCatalysisPerStrikeMax() {
        return VALUES.electroChimeCatalysisPerStrikeMax.get();
    }

    public static double lightningCollectorSpreadRatio() {
        return VALUES.lightningCollectorSpreadRatio.get();
    }

    public static int teslaCoilHighVoltageDustCost() {
        return VALUES.teslaCoilHighVoltageDustCost.get();
    }

    public static int teslaCoilHighVoltageFe() {
        return VALUES.teslaCoilHighVoltageFe.get();
    }

    public static int teslaCoilExtremeHighVoltageInput() {
        return VALUES.teslaCoilExtremeHighVoltageInput.get();
    }

    public static int teslaCoilExtremeHighVoltageFe() {
        return VALUES.teslaCoilExtremeHighVoltageFe.get();
    }

    public static boolean pigmeeFumoGiftOnFirstJoin() {
        return VALUES.pigmeeFumoGiftOnFirstJoin.get();
    }

    public static int overloadArmorPurificationPeriodTicks() { return VALUES.overloadArmorPurificationPeriodTicks.get(); }
    public static boolean overloadArmorPurificationBeneficialEffects() { return VALUES.overloadArmorPurificationBeneficialEffects.get(); }
    public static boolean overloadArmorPurificationNeutralEffects() { return VALUES.overloadArmorPurificationNeutralEffects.get(); }
    public static boolean overloadArmorPurificationHarmfulEffects() { return VALUES.overloadArmorPurificationHarmfulEffects.get(); }
    public static int overloadArmorSaturationCheckIntervalTicks() { return VALUES.overloadArmorSaturationCheckIntervalTicks.get(); }
    public static double overloadArmorUnderwaterDigMultiplier() { return VALUES.overloadArmorUnderwaterDigMultiplier.get(); }
    public static double overloadArmorAirborneDigMultiplier() { return VALUES.overloadArmorAirborneDigMultiplier.get(); }
    public static boolean overloadArmorPhaseFlightEnabled() { return VALUES.overloadArmorPhaseFlightEnabled.get(); }
    public static PhaseLockTeleportMode overloadArmorPhaseLockTeleportMode() {
        return PhaseLockTeleportMode.fromConfigValue(VALUES.overloadArmorPhaseLockTeleportMode.get());
    }
    public static double overloadArmorPhaseFlightSpeedMultiplier() { return VALUES.overloadArmorPhaseFlightSpeedMultiplier.get(); }
    public static long overloadArmorPassiveHvPerTick() { return VALUES.overloadArmorPassiveHvPerTick.get(); }
    public static long overloadArmorFlightHvPerTick() { return VALUES.overloadArmorFlightHvPerTick.get(); }
    public static long overloadArmorPhaseFlightHvPerTick() { return VALUES.overloadArmorPhaseFlightHvPerTick.get(); }
    public static int overloadArmorShieldComboWindowTicks() { return VALUES.overloadArmorShieldComboWindowTicks.get(); }
    public static int overloadArmorUndyingComboWindowTicks() { return VALUES.overloadArmorUndyingComboWindowTicks.get(); }

    // ── Railgun: damage (per-tier base + beam settle) ────────────────────────
    public static int railgunBeamDamagePerSettle() { return VALUES.railgunBeamDamagePerSettle.get(); }
    public static double railgunBeamBypass() { return VALUES.railgunBeamBypass.get(); }
    public static int railgunBaseDamageEhv1() { return VALUES.railgunBaseDamageEhv1.get(); }
    public static int railgunBaseDamageEhv2() { return VALUES.railgunBaseDamageEhv2.get(); }
    public static int railgunBaseDamageEhv3() { return VALUES.railgunBaseDamageEhv3.get(); }
    public static double railgunChargedBypass() { return VALUES.railgunChargedBypass.get(); }

    // ── Railgun: FE energy + lightning ammo ───────────────────────────────────
    public static long railgunBeamFeCostPerSettle() { return VALUES.railgunBeamFeCostPerSettle.get(); }
    public static long railgunFeCostTier1() { return VALUES.railgunFeCostTier1.get(); }
    public static long railgunFeCostTier2() { return VALUES.railgunFeCostTier2.get(); }
    public static long railgunFeCostTier3() { return VALUES.railgunFeCostTier3.get(); }
    public static int railgunBeamHvCostInterval() { return VALUES.railgunBeamHvCostInterval.get(); }
    public static long railgunEhvCostTier1() { return VALUES.railgunEhvCostTier1.get(); }
    public static long railgunEhvCostTier2() { return VALUES.railgunEhvCostTier2.get(); }
    public static long railgunEhvCostTier3() { return VALUES.railgunEhvCostTier3.get(); }
    public static long railgunBufferCapacity() { return VALUES.railgunBufferCapacity.get(); }

    // ── Railgun: PvP / terrain switches and budget ────────────────────────────
    public static boolean railgunDamagePlayers() { return VALUES.railgunDamagePlayers.get(); }
    public static boolean railgunParalysisOnPlayers() { return VALUES.railgunParalysisOnPlayers.get(); }
    public static boolean railgunTerrainDestructionEnabled() { return VALUES.railgunTerrainDestructionEnabled.get(); }
    public static boolean railgunTerrainDropItems() { return VALUES.railgunTerrainDropItems.get(); }
    public static int railgunTerrainBlocksPerTick() { return VALUES.railgunTerrainBlocksPerTick.get(); }

    // ── Railgun: Overload Execution module ──────────────────────────────────
    public static boolean overloadExecutionEnabled() { return VALUES.overloadExecutionEnabled.get(); }
    public static int overloadExecutionDecayWindowTicks() { return VALUES.overloadExecutionDecayWindowTicks.get(); }
    public static double overloadExecutionDecayPower() { return VALUES.overloadExecutionDecayPower.get(); }
    public static int overloadExecutionMaxTracked() { return VALUES.overloadExecutionMaxTracked.get(); }

    public static boolean frequencyCardEnableAutoCleanup() {
        return VALUES.frequencyCardEnableAutoCleanup.get();
    }

    public static int frequencyCardCleanupIntervalSeconds() {
        return VALUES.frequencyCardCleanupIntervalSeconds.get();
    }

    public static int frequencyCardInvalidCleanupDelaySeconds() {
        return VALUES.frequencyCardInvalidCleanupDelaySeconds.get();
    }

    public static int frequencyCardInvalidCleanupRequiredChecks() {
        return VALUES.frequencyCardInvalidCleanupRequiredChecks.get();
    }

    public static int frequencyCardCleanupBatchSize() {
        return VALUES.frequencyCardCleanupBatchSize.get();
    }

    private static final class Values {
        private final ForgeConfigSpec.IntValue configVersion;
        private final ForgeConfigSpec.BooleanValue dataEnergisticsMixinProtection;
        private final ForgeConfigSpec.IntValue lightningCollectorCooldownTicks;
        private final ForgeConfigSpec.IntValue electroChimeMaxCatalysis;
        private final ForgeConfigSpec.BooleanValue overloadTntEnableTerrainDamage;
        private final ForgeConfigSpec.BooleanValue overloadTntEnableMysteriousCellEasterEgg;
        private final ForgeConfigSpec.BooleanValue easterEggEnabled;
        private final ForgeConfigSpec.ConfigValue<String> easterEggItem;
        private final ForgeConfigSpec.IntValue easterEggWeight;
        private final ForgeConfigSpec.ConfigValue<List<? extends String>> easterEggWeights;
        private final ForgeConfigSpec.IntValue overloadTntGlobalBlockBudgetPerTick;
        private final ForgeConfigSpec.IntValue overloadTntGlobalLightningBudgetPerTick;
        private final ForgeConfigSpec.BooleanValue shulkerBulletCollectionEnabled;
        private final ForgeConfigSpec.DoubleValue floatingMatterRiseSpeed;
        private final ForgeConfigSpec.DoubleValue floatingMatterDespawnHeightMultiplier;
        private final ForgeConfigSpec.IntValue overloadedControllerChannelsPerController;
        private final ForgeConfigSpec.DoubleValue overloadedControllerPassiveAePerTick;
        private final ForgeConfigSpec.IntValue wirelessConnectorMaxDistance;
        private final ForgeConfigSpec.IntValue extendedPatternProviderPages;
        private final ForgeConfigSpec.ConfigValue<List<? extends String>> batchCopyLimitedBlocks;
        private final ForgeConfigSpec.IntValue overloadFactoryParallelPerMatrix;
        private final ForgeConfigSpec.LongValue overloadFactoryEnergyCapacity;
        private final ForgeConfigSpec.LongValue overloadFactoryFePerTickNoSpeedCard;
        private final ForgeConfigSpec.LongValue overloadFactoryFePerTickOneSpeedCard;
        private final ForgeConfigSpec.LongValue overloadFactoryFePerTickTwoSpeedCards;
        private final ForgeConfigSpec.LongValue overloadFactoryFePerTickThreeSpeedCards;
        private final ForgeConfigSpec.LongValue overloadFactoryFePerTickFourSpeedCards;
        private final ForgeConfigSpec.BooleanValue artificialLightningTriggerFromHotbar;
        private final ForgeConfigSpec.BooleanValue artificialLightningTriggerFromBackpack;
        private final ForgeConfigSpec.IntValue lightningCollectorHvBaseMin;
        private final ForgeConfigSpec.IntValue lightningCollectorHvBaseMax;
        private final ForgeConfigSpec.IntValue lightningCollectorEhvBaseMin;
        private final ForgeConfigSpec.IntValue lightningCollectorEhvBaseMax;
        private final ForgeConfigSpec.IntValue lightningCollectorHvCrystalStart;
        private final ForgeConfigSpec.IntValue lightningCollectorHvCrystalEnd;
        private final ForgeConfigSpec.IntValue lightningCollectorEhvCrystalStart;
        private final ForgeConfigSpec.IntValue lightningCollectorEhvCrystalEnd;
        private final ForgeConfigSpec.IntValue lightningCollectorPerfectHvOutput;
        private final ForgeConfigSpec.IntValue lightningCollectorPerfectEhvOutput;
        private final ForgeConfigSpec.IntValue electroChimeCatalysisPerStrikeMin;
        private final ForgeConfigSpec.IntValue electroChimeCatalysisPerStrikeMax;
        private final ForgeConfigSpec.DoubleValue lightningCollectorSpreadRatio;
        private final ForgeConfigSpec.IntValue teslaCoilHighVoltageDustCost;
        private final ForgeConfigSpec.IntValue teslaCoilHighVoltageFe;
        private final ForgeConfigSpec.IntValue teslaCoilExtremeHighVoltageInput;
        private final ForgeConfigSpec.IntValue teslaCoilExtremeHighVoltageFe;
        private final ForgeConfigSpec.BooleanValue pigmeeFumoGiftOnFirstJoin;
        private final ForgeConfigSpec.BooleanValue frequencyCardEnableAutoCleanup;
        private final ForgeConfigSpec.IntValue frequencyCardCleanupIntervalSeconds;
        private final ForgeConfigSpec.IntValue frequencyCardInvalidCleanupDelaySeconds;
        private final ForgeConfigSpec.IntValue frequencyCardInvalidCleanupRequiredChecks;
        private final ForgeConfigSpec.IntValue frequencyCardCleanupBatchSize;

        private final ForgeConfigSpec.IntValue overloadArmorPurificationPeriodTicks;
        private final ForgeConfigSpec.BooleanValue overloadArmorPurificationBeneficialEffects;
        private final ForgeConfigSpec.BooleanValue overloadArmorPurificationNeutralEffects;
        private final ForgeConfigSpec.BooleanValue overloadArmorPurificationHarmfulEffects;
        private final ForgeConfigSpec.IntValue overloadArmorSaturationCheckIntervalTicks;
        private final ForgeConfigSpec.DoubleValue overloadArmorUnderwaterDigMultiplier;
        private final ForgeConfigSpec.DoubleValue overloadArmorAirborneDigMultiplier;
        private final ForgeConfigSpec.BooleanValue overloadArmorPhaseFlightEnabled;
        private final ForgeConfigSpec.ConfigValue<String> overloadArmorPhaseLockTeleportMode;
        private final ForgeConfigSpec.DoubleValue overloadArmorPhaseFlightSpeedMultiplier;
        private final ForgeConfigSpec.LongValue overloadArmorPassiveHvPerTick;
        private final ForgeConfigSpec.LongValue overloadArmorFlightHvPerTick;
        private final ForgeConfigSpec.LongValue overloadArmorPhaseFlightHvPerTick;
        private final ForgeConfigSpec.IntValue overloadArmorShieldComboWindowTicks;
        private final ForgeConfigSpec.IntValue overloadArmorUndyingComboWindowTicks;

        // ── Railgun fields ────────────────────────────────────────────────
        private final ForgeConfigSpec.IntValue railgunBeamDamagePerSettle;
        private final ForgeConfigSpec.DoubleValue railgunBeamBypass;
        private final ForgeConfigSpec.IntValue railgunBaseDamageEhv1;
        private final ForgeConfigSpec.IntValue railgunBaseDamageEhv2;
        private final ForgeConfigSpec.IntValue railgunBaseDamageEhv3;
        private final ForgeConfigSpec.DoubleValue railgunChargedBypass;
        private final ForgeConfigSpec.LongValue railgunBeamFeCostPerSettle;
        private final ForgeConfigSpec.LongValue railgunFeCostTier1;
        private final ForgeConfigSpec.LongValue railgunFeCostTier2;
        private final ForgeConfigSpec.LongValue railgunFeCostTier3;
        private final ForgeConfigSpec.IntValue railgunBeamHvCostInterval;
        private final ForgeConfigSpec.LongValue railgunEhvCostTier1;
        private final ForgeConfigSpec.LongValue railgunEhvCostTier2;
        private final ForgeConfigSpec.LongValue railgunEhvCostTier3;
        private final ForgeConfigSpec.LongValue railgunBufferCapacity;
        private final ForgeConfigSpec.BooleanValue railgunDamagePlayers;
        private final ForgeConfigSpec.BooleanValue railgunParalysisOnPlayers;
        private final ForgeConfigSpec.BooleanValue railgunTerrainDestructionEnabled;
        private final ForgeConfigSpec.BooleanValue railgunTerrainDropItems;
        private final ForgeConfigSpec.IntValue railgunTerrainBlocksPerTick;

        // Overload Execution (HP-record / decay model)
        private final ForgeConfigSpec.BooleanValue overloadExecutionEnabled;
        private final ForgeConfigSpec.IntValue overloadExecutionDecayWindowTicks;
        private final ForgeConfigSpec.DoubleValue overloadExecutionDecayPower;
        private final ForgeConfigSpec.IntValue overloadExecutionMaxTracked;

        private Values(ForgeConfigSpec.Builder builder) {
            configVersion = builder
                    .comment("Internal config schema version. Do not edit; used by the mod for upgrade migrations.")
                    .defineInRange("configVersion", CURRENT_CONFIG_VERSION, 1, Integer.MAX_VALUE);

            builder.push(EarlyCompatibilityConfig.SECTION);
            dataEnergisticsMixinProtection = builder
                    .comment(
                            "Enable AE2LT's startup compatibility protection when Data Energistics is installed.",
                            "Set this to false only for diagnostics or after upstream compatibility is restored.",
                            "Changing this option requires a full client or server restart.")
                    .define(EarlyCompatibilityConfig.DATA_ENERGISTICS_PROTECTION_KEY, true);
            builder.pop();

            builder.push("lightningCollector");
            lightningCollectorCooldownTicks = builder
                    .comment("Cooldown in ticks after each captured lightning strike.")
                    .defineInRange("cooldownTicks", 0, 0, Integer.MAX_VALUE);
            builder.push("outputProfile");
            lightningCollectorHvBaseMin = builder
                    .comment("Minimum HV output before crystal bonuses are applied.")
                    .defineInRange("hvBaseMin", 1, 0, Integer.MAX_VALUE);
            lightningCollectorHvBaseMax = builder
                    .comment("Maximum HV output before crystal bonuses are applied.")
                    .defineInRange("hvBaseMax", 2, 0, Integer.MAX_VALUE);
            lightningCollectorEhvBaseMin = builder
                    .comment("Minimum EHV output before crystal bonuses are applied.")
                    .defineInRange("ehvBaseMin", 1, 0, Integer.MAX_VALUE);
            lightningCollectorEhvBaseMax = builder
                    .comment("Maximum EHV output before crystal bonuses are applied.")
                    .defineInRange("ehvBaseMax", 4, 0, Integer.MAX_VALUE);
            lightningCollectorHvCrystalStart = builder
                    .comment("HV crystal count where bonus scaling starts.")
                    .defineInRange("hvCrystalStart", 2, 0, Integer.MAX_VALUE);
            lightningCollectorHvCrystalEnd = builder
                    .comment("HV crystal count where bonus scaling ends.")
                    .defineInRange("hvCrystalEnd", 16, 0, Integer.MAX_VALUE);
            lightningCollectorEhvCrystalStart = builder
                    .comment("EHV crystal count where bonus scaling starts.")
                    .defineInRange("ehvCrystalStart", 2, 0, Integer.MAX_VALUE);
            lightningCollectorEhvCrystalEnd = builder
                    .comment("EHV crystal count where bonus scaling ends.")
                    .defineInRange("ehvCrystalEnd", 16, 0, Integer.MAX_VALUE);
            lightningCollectorPerfectHvOutput = builder
                    .comment("Fixed HV output for a perfect crystal.")
                    .defineInRange("perfectHvOutput", 16, 0, Integer.MAX_VALUE);
            lightningCollectorPerfectEhvOutput = builder
                    .comment("Fixed EHV output for a perfect crystal.")
                    .defineInRange("perfectEhvOutput", 16, 0, Integer.MAX_VALUE);
            lightningCollectorSpreadRatio = builder
                    .comment("Fraction of output used as random spread. Range: > 0.")
                    .defineInRange("spreadRatio", 0.12D, 1.0E-6D, Double.MAX_VALUE);
            builder.pop();
            builder.pop();

            builder.push("electroChimeCrystal");
            electroChimeMaxCatalysis = builder
                    .comment("Catalysis value needed to transform an electro chime crystal into its perfect form.")
                    .defineInRange("maxCatalysis", 180, 1, Integer.MAX_VALUE);
            electroChimeCatalysisPerStrikeMin = builder
                    .comment("Minimum catalysis gained per natural (EHV) lightning strike on the collector.")
                    .defineInRange("catalysisPerStrikeMin", 8, 1, Integer.MAX_VALUE);
            electroChimeCatalysisPerStrikeMax = builder
                    .comment("Maximum catalysis gained per natural (EHV) lightning strike on the collector.")
                    .defineInRange("catalysisPerStrikeMax", 12, 1, Integer.MAX_VALUE);
            builder.pop();

            builder.push("overloadTnt");
            overloadTntEnableTerrainDamage = builder
                    .comment("Controls whether overload TNT can damage terrain with the custom blast task.")
                    .define("enableTerrainDamage", true);
            overloadTntEnableMysteriousCellEasterEgg = builder
                    .comment("Controls whether overload TNT can consume a Lightning Collapse Matrix to drop a Mysterious Cell.")
                    .define("enableMysteriousCellEasterEgg", true);
            overloadTntGlobalBlockBudgetPerTick = builder
                    .comment("Maximum blocks processed per tick across all overload TNT tasks.")
                    .defineInRange("globalBlockBudgetPerTick", 2400, 0, Integer.MAX_VALUE);
            overloadTntGlobalLightningBudgetPerTick = builder
                    .comment("Maximum lightning strikes processed per tick across all overload TNT tasks.")
                    .defineInRange("globalLightningBudgetPerTick", 8, 0, Integer.MAX_VALUE);
            builder.pop();

            builder.push("easterEgg");
            easterEggEnabled = builder
                    .comment("Enables easter eggs.")
                    .define("enabled", true);
            easterEggItem = builder
                    .comment("Easter egg item id.")
                    .define("eastereggitem", "ae2lt:lightning_collapse_matrix",
                            value -> value instanceof String text && ResourceLocation.tryParse(text) != null);
            easterEggWeight = builder
                    .comment("Easter egg weight.")
                    .defineInRange("eastereggweight", 50, 0, 10000);
            Supplier<List<? extends String>> easterEggWeightsDefault = () -> DEFAULT_EASTER_EGG_WEIGHTS;
            easterEggWeights = builder
                    .comment("Easter egg weights. Format: item_id=weight.")
                    .defineList(
                            "eastereggweights",
                            easterEggWeightsDefault,
                            AE2LTCommonConfig::isEasterEggWeightEntry);
            builder.pop();

            builder.push("floatingMatter");
            shulkerBulletCollectionEnabled = builder
                    .comment("Whether a Silk Touch enchanted AE2 Annihilation Plane collects vanilla Shulker bullets",
                            "that reach it, consuming the bullet and inserting one Floating Matter into the ME network.",
                            "The bullet does not need to be shot down; reaching the plane is enough.")
                    .define("shulkerBulletCollection", true);
            floatingMatterRiseSpeed = builder
                    .comment("Blocks per tick that a dropped Floating Matter item rises.")
                    .defineInRange("riseSpeed", 0.08D, 0.001D, 2.0D);
            floatingMatterDespawnHeightMultiplier = builder
                    .comment("Floating Matter despawns once it climbs above this multiple of the world max build height.")
                    .defineInRange("despawnHeightMultiplier", 2.0D, 1.0D, 16.0D);
            builder.pop();

            builder.push("network");
            builder.push("overloadedController");
            overloadedControllerChannelsPerController = builder
                    .comment("Extra channels provided by each overloaded controller.")
                    .defineInRange("channelsPerController", 128, 0, Integer.MAX_VALUE);
            overloadedControllerPassiveAePerTick = builder
                    .comment("Passive AE injected per tick by an overloaded controller.")
                    .defineInRange("passiveAePerTick", 100.0D, 0.0D, Double.MAX_VALUE);
            builder.pop();
            builder.push("wirelessConnector");
            wirelessConnectorMaxDistance = builder
                    .comment("Maximum block distance for Overloaded Wireless Connect Tool links.",
                            "Only limits links from overloaded providers, interfaces, and power supplies to target machines.",
                            "Set to 0 to disable this distance limit.")
                    .defineInRange("maxDistance", 128, 0, Integer.MAX_VALUE);
            builder.pop();
            builder.push("extendedPatternProvider");
            extendedPatternProviderPages = builder
                    .comment("Number of 36-slot pattern pages in the Extended Overloaded Pattern Provider.")
                    .defineInRange("pages",
                            ExtendedPatternProviderCapacity.DEFAULT_PAGES,
                            1,
                            ExtendedPatternProviderCapacity.MAX_PAGES);
            builder.pop();
            builder.push("batchDispatch");
            Supplier<List<? extends String>> batchCopyLimitedBlocksDefault = () -> DEFAULT_BATCH_COPY_LIMITED_BLOCKS;
            batchCopyLimitedBlocks = builder
                    .comment(
                            "Block ids whose single-target pushBatch calls are capped at 1024 copies.",
                            "Matches both batch-provider blocks and physical machines targeted by overloaded providers.",
                            "Supports '*' and '?' wildcards; exact ids and namespace:* use constant-time lookup.")
                    .defineList(
                            "copyLimitedBlocks",
                            batchCopyLimitedBlocksDefault,
                            FastWildcardMatcher::isValidPattern);
            builder.pop();
            builder.pop();

            builder.push("overloadProcessingFactory");
            overloadFactoryParallelPerMatrix = builder
                    .comment("Parallel operations provided by each Lightning Collapse Matrix.")
                    .defineInRange("parallelPerMatrix", 8, 0, Integer.MAX_VALUE / 32);
            overloadFactoryEnergyCapacity = builder
                    .comment("Internal FE buffer capacity of the Overload Processing Factory.")
                    .defineInRange("energyCapacity", 640_000_000L, 1L, Long.MAX_VALUE);
            overloadFactoryFePerTickNoSpeedCard = builder
                    .comment("Maximum FE consumed per tick with no Speed Cards installed.")
                    .defineInRange("fePerTickBase", 400_000L, 0L, Long.MAX_VALUE);
            overloadFactoryFePerTickOneSpeedCard = builder
                    .comment("Maximum FE consumed per tick with 1 Speed Card installed.")
                    .defineInRange("fePerTick1SpeedCard", 2_000_000L, 0L, Long.MAX_VALUE);
            overloadFactoryFePerTickTwoSpeedCards = builder
                    .comment("Maximum FE consumed per tick with 2 Speed Cards installed.")
                    .defineInRange("fePerTick2SpeedCards", 8_000_000L, 0L, Long.MAX_VALUE);
            overloadFactoryFePerTickThreeSpeedCards = builder
                    .comment("Maximum FE consumed per tick with 3 Speed Cards installed.")
                    .defineInRange("fePerTick3SpeedCards", 32_000_000L, 0L, Long.MAX_VALUE);
            overloadFactoryFePerTickFourSpeedCards = builder
                    .comment("Maximum FE consumed per tick with 4 Speed Cards installed.")
                    .defineInRange("fePerTick4SpeedCards", 128_000_000L, 0L, Long.MAX_VALUE);
            builder.pop();

            builder.push("artificialLightning");
            artificialLightningTriggerFromHotbar = builder
                    .comment("Controls whether Overload Crystals in the hotbar or offhand can trigger artificial lightning.")
                    .define("triggerFromHotbar", true);
            artificialLightningTriggerFromBackpack = builder
                    .comment("Controls whether Overload Crystals in the main inventory can trigger artificial lightning.")
                    .define("triggerFromBackpack", false);
            builder.pop();

            builder.push("teslaCoil");
            builder.push("modeCosts");
            teslaCoilHighVoltageDustCost = builder
                    .comment("Overload Crystal Dust cost for High Voltage mode.")
                    .defineInRange("highVoltageDustCost", 2, 0, Integer.MAX_VALUE);
            teslaCoilHighVoltageFe = builder
                    .comment("FE cost for High Voltage mode. Range: >= 1.")
                    .defineInRange("highVoltageFe", 25000, 1, Integer.MAX_VALUE);
            teslaCoilExtremeHighVoltageInput = builder
                    .comment("High Voltage Lightning input cost for Extreme High Voltage mode.")
                    .defineInRange("extremeHighVoltageInput", 8, 0, Integer.MAX_VALUE);
            teslaCoilExtremeHighVoltageFe = builder
                    .comment("FE cost for Extreme High Voltage mode. Range: >= 1.")
                    .defineInRange("extremeHighVoltageFe", 500000, 1, Integer.MAX_VALUE);
            builder.pop();
            builder.pop();

            builder.push("pigmeeFumo");
            pigmeeFumoGiftOnFirstJoin = builder
                    .comment("Controls whether players receive a Pigmee Fumo as a gift on their first login.")
                    .define("giftOnFirstJoin", true);
            builder.pop();

            builder.push("overloadArmor");
            builder.push("movement");
            overloadArmorPhaseFlightEnabled = builder
                    .comment("Server master switch for the phase module's no-clip mode. Creative flight and movement guards remain available.")
                    .define("phaseFlightEnabled", true);
            overloadArmorPhaseLockTeleportMode = builder
                    .comment(
                            "Server policy for the phase-lock module's external-teleport protection.",
                            "ignore-all: disable teleport protection entirely.",
                            "ignore-command: allow player-self commands and permission-level-2 management commands; block other external teleports.",
                            "ignore-none: allow player-self commands only; block every external teleport source.")
                    .define(
                            "phaseLockTeleportMode",
                            PhaseLockTeleportMode.IGNORE_COMMAND.configValue(),
                            PhaseLockTeleportMode::isValidConfigValue);
            overloadArmorPhaseFlightSpeedMultiplier = builder
                    .comment("Movement multiplier applied while phase flight is active.")
                    .defineInRange("phaseFlightSpeedMultiplier", 0.35D, 0.0D, 4.0D);
            builder.pop();

            builder.push("defense");
            overloadArmorPurificationPeriodTicks = builder
                    .comment("Ticks between automatic status effect purification attempts.")
                    .defineInRange("purificationPeriodTicks", 40, 1, 20 * 60 * 60);
            overloadArmorPurificationBeneficialEffects = builder
                    .comment("Whether purification can remove beneficial effects.")
                    .define("purificationBeneficialEffects", false);
            overloadArmorPurificationNeutralEffects = builder
                    .comment("Whether purification can remove neutral effects.")
                    .define("purificationNeutralEffects", false);
            overloadArmorPurificationHarmfulEffects = builder
                    .comment("Whether purification can remove harmful effects.")
                    .define("purificationHarmfulEffects", true);
            builder.pop();

            builder.push("utility");
            overloadArmorSaturationCheckIntervalTicks = builder
                    .comment("Ticks between saturation sustain checks.")
                    .defineInRange("saturationCheckIntervalTicks", 20, 1, 20 * 60 * 60);
            overloadArmorUnderwaterDigMultiplier = builder
                    .comment("Break-speed multiplier applied by underwater dig affinity.")
                    .defineInRange("underwaterDigMultiplier", 5.0D, 1.0D, 64.0D);
            overloadArmorAirborneDigMultiplier = builder
                    .comment("Break-speed multiplier applied by airborne dig affinity.")
                    .defineInRange("airborneDigMultiplier", 5.0D, 1.0D, 64.0D);
            builder.pop();

            builder.push("lightningCosts");
            overloadArmorPassiveHvPerTick = builder
                    .comment("HV lightning consumed each tick by the active reach extension armor module.")
                    .defineInRange("passiveHvPerTick", 1L, 0L, Long.MAX_VALUE);
            overloadArmorFlightHvPerTick = builder
                    .comment("HV lightning consumed each tick while creative flight is active.")
                    .defineInRange("flightHvPerTick", 2L, 0L, Long.MAX_VALUE);
            overloadArmorPhaseFlightHvPerTick = builder
                    .comment("HV lightning consumed each tick while phase flight is active.")
                    .defineInRange("phaseFlightHvPerTick", 8L, 0L, Long.MAX_VALUE);
            builder.pop();

            builder.push("penalty");
            overloadArmorShieldComboWindowTicks = builder
                    .comment("Ticks in the linear combo window for matrix shield lightning cost scaling.")
                    .defineInRange("shieldComboWindowTicks", 200, 1, 20 * 60 * 60);
            overloadArmorUndyingComboWindowTicks = builder
                    .comment("Ticks in the linear combo window for undying FE and EHV cost scaling.")
                    .defineInRange("undyingComboWindowTicks", 200, 1, 20 * 60 * 60);
            builder.pop();
            builder.pop();

            builder.push("railgun");
            builder.push("damage");
            railgunBeamDamagePerSettle = builder
                    .comment("High Voltage beam damage per 2-tick settle.")
                    .defineInRange("beamDamagePerSettle", 20, 0, Integer.MAX_VALUE);
            railgunBeamBypass = builder
                    .comment("High Voltage beam armor bypass (0.0 = fully blocked by armor, 1.0 = ignore armor).")
                    .defineInRange("beamBypass", 0.4D, 0.0D, 1.0D);
            railgunBaseDamageEhv1 = builder
                    .comment("Charge tier 1 base damage.")
                    .defineInRange("baseDamageEhv1", 100, 0, Integer.MAX_VALUE);
            railgunBaseDamageEhv2 = builder
                    .comment("Charge tier 2 base damage.")
                    .defineInRange("baseDamageEhv2", 300, 0, Integer.MAX_VALUE);
            railgunBaseDamageEhv3 = builder
                    .comment("Charge tier 3 (max) base damage.")
                    .defineInRange("baseDamageEhv3", 600, 0, Integer.MAX_VALUE);
            railgunChargedBypass = builder
                    .comment("Charged-shot armor bypass for all tiers (single dial — replaces per-tier 0.4/0.6/0.8).")
                    .defineInRange("chargedBypass", 0.8D, 0.0D, 1.0D);
            builder.pop();

            builder.push("energy");
            railgunBeamFeCostPerSettle = builder
                    .comment("FE energy consumed per beam settle.")
                    .defineInRange("beamFeCostPerSettle", 400L, 0L, Long.MAX_VALUE);
            railgunFeCostTier1 = builder
                    .comment("FE energy consumed per tier-1 charged shot.")
                    .defineInRange("feCostTier1", 8000L, 0L, Long.MAX_VALUE);
            railgunFeCostTier2 = builder
                    .comment("FE energy consumed per tier-2 charged shot.")
                    .defineInRange("feCostTier2", 40000L, 0L, Long.MAX_VALUE);
            railgunFeCostTier3 = builder
                    .comment("FE energy consumed per tier-3 (max) charged shot.")
                    .defineInRange("feCostTier3", 200000L, 0L, Long.MAX_VALUE);
            railgunBeamHvCostInterval = builder
                    .comment("HV beam consumes 1 HV every N settles (settle = 2 ticks). N=8 means ~1.25 HV/sec.")
                    .defineInRange("beamHvCostInterval", 8, 1, 64);
            railgunEhvCostTier1 = builder
                    .comment("EHV consumed per tier-1 charged shot.")
                    .defineInRange("ehvCostTier1", 32L, 0L, Long.MAX_VALUE);
            railgunEhvCostTier2 = builder
                    .comment("EHV consumed per tier-2 charged shot.")
                    .defineInRange("ehvCostTier2", 96L, 0L, Long.MAX_VALUE);
            railgunEhvCostTier3 = builder
                    .comment("EHV consumed per tier-3 (max) charged shot.")
                    .defineInRange("ehvCostTier3", 256L, 0L, Long.MAX_VALUE);
            railgunBufferCapacity = builder
                    .comment("Base FE stored in the railgun when no structural energy module is installed.",
                            "Installing an energy module makes the railgun capacity equal to that module's capacity.")
                    .defineInRange("bufferCapacity", 1_000_000L, 0L, Long.MAX_VALUE);
            builder.pop();

            builder.push("misc");
            railgunDamagePlayers = builder
                    .comment("Whether the railgun damages other players.")
                    .define("damagePlayers", true);
            railgunParalysisOnPlayers = builder
                    .comment("Whether paralysis applies to players.")
                    .define("paralysisOnPlayers", true);
            builder.pop();

            builder.push("terrain");
            railgunTerrainDestructionEnabled = builder
                    .comment("Master switch for railgun terrain destruction.",
                            "When false, no railgun shot can break blocks even if the item setting is ON.")
                    .define("enableTerrainDestruction", true);
            railgunTerrainDropItems = builder
                    .comment("Whether terrain destruction produces drops (drops auto-despawn after 60s).")
                    .define("dropItems", false);
            railgunTerrainBlocksPerTick = builder
                    .comment("Block break budget per tick across all railgun terrain jobs.")
                    .defineInRange("blocksPerTick", 200, 1, 8192);
            builder.pop();

            builder.push("overloadExecution");
            overloadExecutionEnabled = builder
                    .comment("Master switch for the Overload Execution module (EHv3-charged forced-kill).")
                    .define("enabled", true);
            overloadExecutionDecayWindowTicks = builder
                    .comment("Decay window in ticks. After this many ticks since the last hit, the recorded HP fully resets to current HP.",
                            "Default 1200 = 60 seconds.")
                    .defineInRange("decayWindowTicks", 1200, 1, Integer.MAX_VALUE);
            overloadExecutionDecayPower = builder
                    .comment("Decay curve exponent (slow-start, fast-finish). recovery_fraction = (elapsed / window)^power.",
                            "  1.0 = linear",
                            "  2.0 = quadratic (default — early ticks barely heal, last ticks restore fast)",
                            "  3.0 = cubic (even slower start)")
                    .defineInRange("decayPower", 2.0D, 0.1D, 10.0D);
            overloadExecutionMaxTracked = builder
                    .comment("Maximum number of targets whose recorded HP is kept simultaneously on a single railgun.")
                    .defineInRange("maxTracked", 8, 1, 64);
            builder.pop();
            builder.pop();

            builder.push("frequencyCard");
            builder.push("cleanup");
            frequencyCardEnableAutoCleanup = builder
                    .comment("Controls whether invalid Overloaded Frequency Card wireless link records are cleaned up automatically.")
                    .define("enableAutoCleanup", true);
            frequencyCardCleanupIntervalSeconds = builder
                    .comment("Seconds between cleanup passes for Overloaded Frequency Card wireless links.")
                    .defineInRange("cleanupIntervalSeconds", 300, 1, Integer.MAX_VALUE);
            frequencyCardInvalidCleanupDelaySeconds = builder
                    .comment("Seconds an invalid Overloaded Frequency Card wireless link must remain invalid before removal.")
                    .defineInRange("invalidCleanupDelaySeconds", 300, 0, Integer.MAX_VALUE);
            frequencyCardInvalidCleanupRequiredChecks = builder
                    .comment("Number of cleanup passes that must confirm an invalid Overloaded Frequency Card wireless link before removal.")
                    .defineInRange("invalidCleanupRequiredChecks", 3, 1, Integer.MAX_VALUE);
            frequencyCardCleanupBatchSize = builder
                    .comment("Maximum Overloaded Frequency Card wireless links checked per cleanup pass.")
                    .defineInRange("cleanupBatchSize", 128, 1, Integer.MAX_VALUE);
            builder.pop();
            builder.pop();
        }
    }

    private static boolean isEasterEggWeightEntry(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        int separator = text.lastIndexOf('=');
        if (separator <= 0 || separator == text.length() - 1
                || ResourceLocation.tryParse(text.substring(0, separator).strip()) == null) {
            return false;
        }
        try {
            int weight = Integer.parseInt(text.substring(separator + 1).strip());
            return weight >= 1 && weight <= 1_000_000;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
