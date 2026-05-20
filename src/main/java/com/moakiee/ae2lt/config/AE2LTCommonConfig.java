package com.moakiee.ae2lt.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class AE2LTCommonConfig {
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

    public static int overloadTntGlobalBlockBudgetPerTick() {
        return VALUES.overloadTntGlobalBlockBudgetPerTick.get();
    }

    public static int overloadTntGlobalLightningBudgetPerTick() {
        return VALUES.overloadTntGlobalLightningBudgetPerTick.get();
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

    private static final class Values {
        private final ForgeConfigSpec.IntValue lightningCollectorCooldownTicks;
        private final ForgeConfigSpec.IntValue electroChimeMaxCatalysis;
        private final ForgeConfigSpec.BooleanValue overloadTntEnableTerrainDamage;
        private final ForgeConfigSpec.IntValue overloadTntGlobalBlockBudgetPerTick;
        private final ForgeConfigSpec.IntValue overloadTntGlobalLightningBudgetPerTick;
        private final ForgeConfigSpec.IntValue overloadedControllerChannelsPerController;
        private final ForgeConfigSpec.DoubleValue overloadedControllerPassiveAePerTick;
        private final ForgeConfigSpec.IntValue wirelessConnectorMaxDistance;
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

        private Values(ForgeConfigSpec.Builder builder) {
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
            overloadTntGlobalBlockBudgetPerTick = builder
                    .comment("Maximum blocks processed per tick across all overload TNT tasks.")
                    .defineInRange("globalBlockBudgetPerTick", 2400, 0, Integer.MAX_VALUE);
            overloadTntGlobalLightningBudgetPerTick = builder
                    .comment("Maximum lightning strikes processed per tick across all overload TNT tasks.")
                    .defineInRange("globalLightningBudgetPerTick", 8, 0, Integer.MAX_VALUE);
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
        }
    }
}

