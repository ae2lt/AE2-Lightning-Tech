package com.moakiee.ae2lt.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AE2LTCommonConfig {
    public static final int CURRENT_CONFIG_VERSION = 3;

    public static final ModConfigSpec SPEC;

    private static final Values VALUES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
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
        private final ModConfigSpec.IntValue configVersion;
        private final ModConfigSpec.IntValue lightningCollectorCooldownTicks;
        private final ModConfigSpec.IntValue electroChimeMaxCatalysis;
        private final ModConfigSpec.BooleanValue overloadTntEnableTerrainDamage;
        private final ModConfigSpec.BooleanValue overloadTntEnableMysteriousCellEasterEgg;
        private final ModConfigSpec.IntValue overloadTntGlobalBlockBudgetPerTick;
        private final ModConfigSpec.IntValue overloadTntGlobalLightningBudgetPerTick;
        private final ModConfigSpec.BooleanValue shulkerBulletCollectionEnabled;
        private final ModConfigSpec.DoubleValue floatingMatterRiseSpeed;
        private final ModConfigSpec.DoubleValue floatingMatterDespawnHeightMultiplier;
        private final ModConfigSpec.IntValue overloadedControllerChannelsPerController;
        private final ModConfigSpec.DoubleValue overloadedControllerPassiveAePerTick;
        private final ModConfigSpec.IntValue wirelessConnectorMaxDistance;
        private final ModConfigSpec.IntValue overloadFactoryParallelPerMatrix;
        private final ModConfigSpec.LongValue overloadFactoryEnergyCapacity;
        private final ModConfigSpec.LongValue overloadFactoryFePerTickNoSpeedCard;
        private final ModConfigSpec.LongValue overloadFactoryFePerTickOneSpeedCard;
        private final ModConfigSpec.LongValue overloadFactoryFePerTickTwoSpeedCards;
        private final ModConfigSpec.LongValue overloadFactoryFePerTickThreeSpeedCards;
        private final ModConfigSpec.LongValue overloadFactoryFePerTickFourSpeedCards;
        private final ModConfigSpec.BooleanValue artificialLightningTriggerFromHotbar;
        private final ModConfigSpec.BooleanValue artificialLightningTriggerFromBackpack;
        private final ModConfigSpec.IntValue lightningCollectorHvBaseMin;
        private final ModConfigSpec.IntValue lightningCollectorHvBaseMax;
        private final ModConfigSpec.IntValue lightningCollectorEhvBaseMin;
        private final ModConfigSpec.IntValue lightningCollectorEhvBaseMax;
        private final ModConfigSpec.IntValue lightningCollectorHvCrystalStart;
        private final ModConfigSpec.IntValue lightningCollectorHvCrystalEnd;
        private final ModConfigSpec.IntValue lightningCollectorEhvCrystalStart;
        private final ModConfigSpec.IntValue lightningCollectorEhvCrystalEnd;
        private final ModConfigSpec.IntValue lightningCollectorPerfectHvOutput;
        private final ModConfigSpec.IntValue lightningCollectorPerfectEhvOutput;
        private final ModConfigSpec.IntValue electroChimeCatalysisPerStrikeMin;
        private final ModConfigSpec.IntValue electroChimeCatalysisPerStrikeMax;
        private final ModConfigSpec.DoubleValue lightningCollectorSpreadRatio;
        private final ModConfigSpec.IntValue teslaCoilHighVoltageDustCost;
        private final ModConfigSpec.IntValue teslaCoilHighVoltageFe;
        private final ModConfigSpec.IntValue teslaCoilExtremeHighVoltageInput;
        private final ModConfigSpec.IntValue teslaCoilExtremeHighVoltageFe;
        private final ModConfigSpec.BooleanValue pigmeeFumoGiftOnFirstJoin;
        private final ModConfigSpec.BooleanValue frequencyCardEnableAutoCleanup;
        private final ModConfigSpec.IntValue frequencyCardCleanupIntervalSeconds;
        private final ModConfigSpec.IntValue frequencyCardInvalidCleanupDelaySeconds;
        private final ModConfigSpec.IntValue frequencyCardInvalidCleanupRequiredChecks;
        private final ModConfigSpec.IntValue frequencyCardCleanupBatchSize;

        private final ModConfigSpec.IntValue overloadArmorPurificationPeriodTicks;
        private final ModConfigSpec.BooleanValue overloadArmorPurificationBeneficialEffects;
        private final ModConfigSpec.BooleanValue overloadArmorPurificationNeutralEffects;
        private final ModConfigSpec.BooleanValue overloadArmorPurificationHarmfulEffects;
        private final ModConfigSpec.IntValue overloadArmorSaturationCheckIntervalTicks;
        private final ModConfigSpec.DoubleValue overloadArmorUnderwaterDigMultiplier;
        private final ModConfigSpec.DoubleValue overloadArmorAirborneDigMultiplier;
        private final ModConfigSpec.BooleanValue overloadArmorPhaseFlightEnabled;
        private final ModConfigSpec.DoubleValue overloadArmorPhaseFlightSpeedMultiplier;
        private final ModConfigSpec.LongValue overloadArmorPassiveHvPerTick;
        private final ModConfigSpec.LongValue overloadArmorFlightHvPerTick;
        private final ModConfigSpec.LongValue overloadArmorPhaseFlightHvPerTick;
        private final ModConfigSpec.IntValue overloadArmorShieldComboWindowTicks;
        private final ModConfigSpec.IntValue overloadArmorUndyingComboWindowTicks;

        // ── Railgun fields ────────────────────────────────────────────────
        private final ModConfigSpec.IntValue railgunBeamDamagePerSettle;
        private final ModConfigSpec.DoubleValue railgunBeamBypass;
        private final ModConfigSpec.IntValue railgunBaseDamageEhv1;
        private final ModConfigSpec.IntValue railgunBaseDamageEhv2;
        private final ModConfigSpec.IntValue railgunBaseDamageEhv3;
        private final ModConfigSpec.DoubleValue railgunChargedBypass;
        private final ModConfigSpec.LongValue railgunBeamFeCostPerSettle;
        private final ModConfigSpec.LongValue railgunFeCostTier1;
        private final ModConfigSpec.LongValue railgunFeCostTier2;
        private final ModConfigSpec.LongValue railgunFeCostTier3;
        private final ModConfigSpec.IntValue railgunBeamHvCostInterval;
        private final ModConfigSpec.LongValue railgunEhvCostTier1;
        private final ModConfigSpec.LongValue railgunEhvCostTier2;
        private final ModConfigSpec.LongValue railgunEhvCostTier3;
        private final ModConfigSpec.LongValue railgunBufferCapacity;
        private final ModConfigSpec.BooleanValue railgunDamagePlayers;
        private final ModConfigSpec.BooleanValue railgunParalysisOnPlayers;
        private final ModConfigSpec.BooleanValue railgunTerrainDestructionEnabled;
        private final ModConfigSpec.BooleanValue railgunTerrainDropItems;
        private final ModConfigSpec.IntValue railgunTerrainBlocksPerTick;

        // Overload Execution (HP-record / decay model)
        private final ModConfigSpec.BooleanValue overloadExecutionEnabled;
        private final ModConfigSpec.IntValue overloadExecutionDecayWindowTicks;
        private final ModConfigSpec.DoubleValue overloadExecutionDecayPower;
        private final ModConfigSpec.IntValue overloadExecutionMaxTracked;

        private Values(ModConfigSpec.Builder builder) {
            configVersion = builder
                    .comment("Internal config schema version. Do not edit; used by the mod for upgrade migrations.")
                    .defineInRange("configVersion", CURRENT_CONFIG_VERSION, 1, Integer.MAX_VALUE);

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
                    .comment("Master switch for phase flight.")
                    .define("phaseFlightEnabled", true);
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
                    .comment("Ticks in the linear combo window for matrix and phase shield lightning cost scaling.")
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
}
