package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

public class ElectroChimeCrystalItem extends Item {
    private static final String TAG_CATALYSIS = "ae2lt.catalysis_value";
    private static final double STAGE_1_FRACTION = 0.20D;
    private static final double STAGE_2_FRACTION = 0.55D;
    private static final double STAGE_3_FRACTION = 0.85D;

    public ElectroChimeCrystalItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int getMaxCatalysis() {
        return AE2LTCommonConfig.electroChimeMaxCatalysis();
    }

    public static int getCatalysisValue(ItemStack stack) {
        return Mth.clamp(
                stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(TAG_CATALYSIS),
                0,
                getMaxCatalysis());
    }

    public static void setCatalysisValue(ItemStack stack, int catalysisValue) {
        int clamped = Mth.clamp(catalysisValue, 0, getMaxCatalysis());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG_CATALYSIS, clamped));
    }

    public static int addCatalysis(ItemStack stack, int amount) {
        int next = Mth.clamp(getCatalysisValue(stack) + Math.max(0, amount), 0, getMaxCatalysis());
        setCatalysisValue(stack, next);
        return next;
    }

    public static double getCatalysisPercent(ItemStack stack) {
        return (double) getCatalysisValue(stack) / (double) getMaxCatalysis();
    }

    public static int getCatalysisStage(ItemStack stack) {
        return getCatalysisStage(getCatalysisValue(stack));
    }

    public static int getCatalysisStage(int catalysisValue) {
        if (catalysisValue >= stageThreshold(2)) {
            return 3;
        }
        if (catalysisValue >= stageThreshold(1)) {
            return 2;
        }
        if (catalysisValue >= stageThreshold(0)) {
            return 1;
        }
        return 0;
    }

    public static Component getStageName(ItemStack stack) {
        return getStageName(getCatalysisStage(stack));
    }

    public static Component getStageName(int stage) {
        return Component.translatable("item.ae2lt.electro_chime_crystal.stage." + stage);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(
                "item.ae2lt.electro_chime_crystal.percent",
                String.format("%.1f", getCatalysisPercent(stack) * 100.0D)).withStyle(ChatFormatting.AQUA));
        tooltipComponents.add(Component.translatable(
                "item.ae2lt.electro_chime_crystal.stage",
                getStageName(stack)).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public static int rollCatalysisFeed(RandomSource random) {
        int min = Math.max(1, AE2LTCommonConfig.electroChimeCatalysisPerStrikeMin());
        int max = Math.max(min, AE2LTCommonConfig.electroChimeCatalysisPerStrikeMax());
        return min + random.nextInt(max - min + 1);
    }

    private static int stageThreshold(int stage) {
        int maxCatalysis = getMaxCatalysis();
        int stage1 = Mth.clamp(Mth.ceil(maxCatalysis * STAGE_1_FRACTION), 1, maxCatalysis);
        int stage2 = Mth.clamp(Mth.ceil(maxCatalysis * STAGE_2_FRACTION), stage1, maxCatalysis);
        int stage3 = Mth.clamp(Mth.ceil(maxCatalysis * STAGE_3_FRACTION), stage2, maxCatalysis);
        return switch (stage) {
            case 0 -> stage1;
            case 1 -> stage2;
            default -> stage3;
        };
    }
}
