package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.lightning.strike.LightningStrikeRecipe;
import com.moakiee.ae2lt.lightning.strike.StructureRequirement;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.util.RecipeManagerByTypeAccess;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;

public final class NaturalLightningTransformationHandler {
    public static final String NATURAL_WEATHER_LIGHTNING_TAG = "ae2lt.natural_weather_lightning";
    private static final String TRANSFORMATION_CHECKED_TAG = "ae2lt.natural_transform_checked";
    /**
     * Marker that this mod's onLightningTick handler ran for a given LightningBolt.
     * Distinguishes "this mod handled the strike" from "some other mod set the
     * transformation_checked tag at higher priority". When the latter happens,
     * captureLightning and the public LightningCollectedEvent are silently bypassed.
     */
    private static final String MAIN_HANDLED_TAG = "ae2lt.main_lightning_handled";

    private static final Logger LOG = LogUtils.getLogger();
    /** Throttle the takeover warning so it logs at most once per JVM. */
    private static volatile boolean warnedTakeover;

    private static final DustParticleOptions PINK_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.78F), 1.6F);
    private static final DustParticleOptions PURPLE_DUST =
            new DustParticleOptions(new Vector3f(0.78F, 0.34F, 1.0F), 1.4F);
    private static final DustParticleOptions CERTUS_DUST =
            new DustParticleOptions(new Vector3f(0.85F, 0.92F, 1.0F), 1.4F);

    private NaturalLightningTransformationHandler() {
    }

    public static void handleLightningTick(LightningBolt lightningBolt) {
        if (!(lightningBolt.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var data = lightningBolt.getPersistentData();
        if (data.getBoolean(TRANSFORMATION_CHECKED_TAG)) {
            // The transformation_checked tag is set but our own marker isn't — another
            // mod (e.g. Thunderbolt_lib) intercepted the lightning at higher priority
            // and ran its own pipeline. The collector's captureLightning() will not be
            // called for this strike, which means LightningCollectedEvent will not
            // fire either. Warn once so server operators can correlate missing
            // capture-side effects with a third-party takeover.
            if (!data.getBoolean(MAIN_HANDLED_TAG) && !warnedTakeover) {
                warnedTakeover = true;
                LOG.warn(
                        "AE2 Lightning Tech: a LightningBolt arrived with "
                                + "{} already set but {} unset. Another mod is "
                                + "intercepting natural lightning before this mod's "
                                + "handler runs; LightningCollectorBlockEntity.captureLightning "
                                + "and the public LightningCollectedEvent will not fire "
                                + "for those strikes. This warning logs only once per JVM.",
                        TRANSFORMATION_CHECKED_TAG,
                        MAIN_HANDLED_TAG);
            }
            return;
        }

        data.putBoolean(TRANSFORMATION_CHECKED_TAG, true);
        data.putBoolean(MAIN_HANDLED_TAG, true);
        boolean naturalWeatherLightning = data.getBoolean(NATURAL_WEATHER_LIGHTNING_TAG);
        tryCaptureLightning(serverLevel, lightningBolt.blockPosition(), naturalWeatherLightning);
        tryTransformFromNearbyLightningRod(serverLevel, lightningBolt.blockPosition(), naturalWeatherLightning);
    }

    private static void tryCaptureLightning(ServerLevel level, BlockPos lightningPos, boolean naturalWeatherLightning) {
        for (int yOffset = 0; yOffset <= 2; yOffset++) {
            BlockPos rodPos = lightningPos.below(yOffset);
            if (!level.getBlockState(rodPos).is(Blocks.LIGHTNING_ROD)) {
                continue;
            }

            if (level.getBlockEntity(rodPos.below()) instanceof LightningCollectorBlockEntity collector
                    && collector.canCaptureLightning()) {
                collector.captureLightning(naturalWeatherLightning);
                return;
            }
        }
    }

    private static void tryTransformFromNearbyLightningRod(
            ServerLevel level, BlockPos lightningPos, boolean naturalWeather) {
        List<LightningStrikeRecipe> allRecipes = new java.util.ArrayList<>(
                RecipeManagerByTypeAccess.byType(level.getRecipeManager(), ModRecipeTypes.LIGHTNING_STRIKE_TYPE.get())
                        .values());
        if (allRecipes.isEmpty()) {
            return;
        }

        for (int yOffset = 0; yOffset <= 2; yOffset++) {
            BlockPos rodPos = lightningPos.below(yOffset);
            BlockState rodState = level.getBlockState(rodPos);
            if (!rodState.is(Blocks.LIGHTNING_ROD)) {
                continue;
            }

            // The lightning rod is an implicit prerequisite for every ritual: the recipe center
            // is always the block directly below it. Recipes therefore only describe the
            // surrounding structure, never the rod itself.
            BlockPos centerPos = rodPos.below();
            for (LightningStrikeRecipe recipe : allRecipes) {
                if (recipe.requiresNaturalLightning() && !naturalWeather) {
                    continue;
                }
                if (tryApplyRecipe(level, recipe, centerPos, rodPos)) {
                    return;
                }
            }
        }
    }

    private static boolean tryApplyRecipe(
            ServerLevel level, LightningStrikeRecipe recipe, BlockPos centerPos, BlockPos rodPos) {
        BlockState centerState = level.getBlockState(centerPos);
        if (!centerState.is(recipe.centerInput())) {
            return false;
        }

        for (StructureRequirement req : recipe.requirements()) {
            BlockPos worldPos = centerPos.offset(req.offset());
            if (!level.getBlockState(worldPos).is(req.block())) {
                return false;
            }
        }

        spawnTransformationParticles(level, recipe, centerPos, rodPos);

        for (StructureRequirement req : recipe.requirements()) {
            if (!req.consume()) {
                continue;
            }
            level.setBlockAndUpdate(centerPos.offset(req.offset()), Blocks.AIR.defaultBlockState());
        }

        level.setBlockAndUpdate(centerPos, recipe.centerOutput().defaultBlockState());
        spawnCompletionParticles(level, centerPos);
        return true;
    }

    private static void spawnTransformationParticles(
            ServerLevel level, LightningStrikeRecipe recipe, BlockPos centerPos, BlockPos rodPos) {
        // Pick particle palette based on whether the structure uses overload-crystal
        // corners (rich) or something else (simple/generic).
        boolean rich = hasCornerBlock(recipe, ModBlocks.OVERLOAD_CRYSTAL_BLOCK.get());
        if (rich) {
            spawnRichTransformationParticles(level, rodPos, centerPos);
        } else {
            spawnSimpleTransformationParticles(level, rodPos, centerPos);
        }
    }

    private static boolean hasCornerBlock(LightningStrikeRecipe recipe, Block target) {
        for (StructureRequirement req : recipe.requirements()) {
            BlockPos off = req.offset();
            if (Math.abs(off.getX()) == 1 && Math.abs(off.getZ()) == 1 && off.getY() == 0
                    && req.block() == target) {
                return true;
            }
        }
        return false;
    }

    private static final List<BlockPos> CORNER_OFFSETS = List.of(
            new BlockPos(-1, 0, -1),
            new BlockPos(1, 0, -1),
            new BlockPos(-1, 0, 1),
            new BlockPos(1, 0, 1));

    private static final List<BlockPos> EDGE_OFFSETS = List.of(
            new BlockPos(0, 0, -1),
            new BlockPos(-1, 0, 0),
            new BlockPos(1, 0, 0),
            new BlockPos(0, 0, 1));

    private static final List<BlockPos> OUTER_RING_OFFSETS = List.of(
            new BlockPos(-2, 0, -2), new BlockPos(-1, 0, -2), new BlockPos(0, 0, -2),
            new BlockPos(1, 0, -2), new BlockPos(2, 0, -2),
            new BlockPos(-2, 0, -1), new BlockPos(2, 0, -1),
            new BlockPos(-2, 0, 0), new BlockPos(2, 0, 0),
            new BlockPos(-2, 0, 1), new BlockPos(2, 0, 1),
            new BlockPos(-2, 0, 2), new BlockPos(-1, 0, 2), new BlockPos(0, 0, 2),
            new BlockPos(1, 0, 2), new BlockPos(2, 0, 2));

    private static void spawnRichTransformationParticles(ServerLevel level, BlockPos rodPos, BlockPos centerPos) {
        Vec3 rodVec = Vec3.atCenterOf(rodPos).add(0.0D, -0.2D, 0.0D);
        Vec3 centerVec = Vec3.atCenterOf(centerPos).add(0.0D, 0.55D, 0.0D);

        for (int i = 0; i < 7; i++) {
            double progress = i / 6.0D;
            Vec3 point = rodVec.lerp(centerVec, progress);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 10, 0.12D, 0.1D, 0.12D, 0.03D);
            level.sendParticles(PINK_DUST, point.x, point.y, point.z, 8, 0.08D, 0.08D, 0.08D, 0.01D);
        }

        for (BlockPos offset : EDGE_OFFSETS) {
            Vec3 from = Vec3.atCenterOf(centerPos.offset(offset)).add(0.0D, 0.55D, 0.0D);
            Vec3 toward = from.vectorTo(centerVec).scale(0.18D);
            level.sendParticles(PURPLE_DUST, from.x, from.y, from.z, 24, 0.18D, 0.14D, 0.18D, 0.01D);
            level.sendParticles(ParticleTypes.WITCH, from.x, from.y, from.z, 20, toward.x, 0.06D, toward.z, 0.18D);
        }

        for (BlockPos offset : CORNER_OFFSETS) {
            Vec3 from = Vec3.atCenterOf(centerPos.offset(offset)).add(0.0D, 0.55D, 0.0D);
            Vec3 toward = from.vectorTo(centerVec).scale(0.16D);
            level.sendParticles(PINK_DUST, from.x, from.y, from.z, 26, 0.2D, 0.16D, 0.2D, 0.01D);
            level.sendParticles(ParticleTypes.ENCHANT, from.x, from.y, from.z, 20, toward.x, 0.06D, toward.z, 0.22D);
        }

        for (int i = 0; i < OUTER_RING_OFFSETS.size(); i++) {
            BlockPos offset = OUTER_RING_OFFSETS.get(i);
            Vec3 from = Vec3.atCenterOf(centerPos.offset(offset)).add(0.0D, 0.2D + (i % 3) * 0.12D, 0.0D);
            Vec3 toward = from.vectorTo(centerVec).scale(0.08D);
            DustParticleOptions ringDust = (i & 1) == 0 ? PURPLE_DUST : PINK_DUST;
            level.sendParticles(ringDust, from.x, from.y, from.z, 12, 0.14D, 0.06D, 0.14D, 0.01D);
            level.sendParticles(ParticleTypes.ENCHANT, from.x, from.y, from.z, 8, toward.x, 0.03D, toward.z, 0.1D);
        }
    }

    private static void spawnSimpleTransformationParticles(ServerLevel level, BlockPos rodPos, BlockPos centerPos) {
        Vec3 rodVec = Vec3.atCenterOf(rodPos).add(0.0D, -0.2D, 0.0D);
        Vec3 centerVec = Vec3.atCenterOf(centerPos).add(0.0D, 0.55D, 0.0D);

        for (int i = 0; i < 5; i++) {
            double progress = i / 4.0D;
            Vec3 point = rodVec.lerp(centerVec, progress);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 6, 0.1D, 0.08D, 0.1D, 0.02D);
            level.sendParticles(CERTUS_DUST, point.x, point.y, point.z, 5, 0.07D, 0.07D, 0.07D, 0.01D);
        }

        for (BlockPos offset : CORNER_OFFSETS) {
            Vec3 from = Vec3.atCenterOf(centerPos.offset(offset)).add(0.0D, 0.55D, 0.0D);
            Vec3 toward = from.vectorTo(centerVec).scale(0.16D);
            level.sendParticles(CERTUS_DUST, from.x, from.y, from.z, 20, 0.18D, 0.14D, 0.18D, 0.01D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 14, toward.x, 0.06D, toward.z, 0.18D);
        }

        for (BlockPos offset : EDGE_OFFSETS) {
            Vec3 from = Vec3.atCenterOf(centerPos.offset(offset)).add(0.0D, 0.55D, 0.0D);
            Vec3 toward = from.vectorTo(centerVec).scale(0.16D);
            level.sendParticles(PURPLE_DUST, from.x, from.y, from.z, 18, 0.18D, 0.14D, 0.18D, 0.01D);
            level.sendParticles(ParticleTypes.ENCHANT, from.x, from.y, from.z, 14, toward.x, 0.06D, toward.z, 0.18D);
        }
    }

    private static void spawnCompletionParticles(ServerLevel level, BlockPos centerPos) {
        Vec3 centerVec = Vec3.atCenterOf(centerPos).add(0.0D, 0.7D, 0.0D);
        for (int i = 0; i < 4; i++) {
            double y = centerVec.y + i * 0.18D;
            level.sendParticles(PINK_DUST, centerVec.x, y, centerVec.z, 18, 0.24D, 0.04D, 0.24D, 0.01D);
            level.sendParticles(ParticleTypes.END_ROD, centerVec.x, y, centerVec.z, 10, 0.18D, 0.1D, 0.18D, 0.03D);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centerVec.x, centerVec.y + 0.2D, centerVec.z, 24, 0.28D, 0.28D, 0.28D, 0.02D);
        level.sendParticles(ParticleTypes.ENCHANT, centerVec.x, centerVec.y + 0.25D, centerVec.z, 32, 0.35D, 0.25D, 0.35D, 0.12D);
    }
}

