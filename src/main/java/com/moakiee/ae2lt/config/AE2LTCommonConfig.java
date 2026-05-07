package com.moakiee.ae2lt.config;

import net.minecraftforge.common.ModConfigSpec;

public final class AE2LTCommonConfig {
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
        private final ModConfigSpec.IntValue lightningCollectorCooldownTicks;
        private final ModConfigSpec.IntValue electroChimeMaxCatalysis;
        private final ModConfigSpec.BooleanValue overloadTntEnableTerrainDamage;
        private final ModConfigSpec.IntValue overloadTntGlobalBlockBudgetPerTick;
        private final ModConfigSpec.IntValue overloadTntGlobalLightningBudgetPerTick;
        private final ModConfigSpec.IntValue overloadedControllerChannelsPerController;
        private final ModConfigSpec.DoubleValue overloadedControllerPassiveAePerTick;
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

        private Values(ModConfigSpec.Builder builder) {
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
            builder.pop();

            builder.push("overloadProcessingFactory");
            overloadFactoryParallelPerMatrix = builder
                    .comment("Parallel operations provided by each Lightning Collapse Matrix.")
                    .defineInRange("parallelPerMatrix", 4, 0, Integer.MAX_VALUE / 64);
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

