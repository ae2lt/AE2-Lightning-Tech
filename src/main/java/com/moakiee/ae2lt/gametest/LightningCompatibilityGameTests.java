package com.moakiee.ae2lt.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.event.NaturalLightningTransformationHandler;
import com.moakiee.ae2lt.lightning.strike.LightningStrikeRecipe;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class LightningCompatibilityGameTests {
    private static final String NATURAL_TRANSFORM_CHECKED_TAG = "ae2lt.natural_transform_checked";
    private static final String ITEM_TRANSFORM_CHECKED_TAG = "ae2lt.lightning_item_transform_checked";

    private LightningCompatibilityGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void processesLightningSubclassThatDoesNotCallSuperTick(GameTestHelper helper) {
        var lightning = new NoSuperTickLightningBolt(helper.getLevel());
        BlockPos spawnPos = helper.absolutePos(new BlockPos(0, 2, 0));
        lightning.setPos(Vec3.atBottomCenterOf(spawnPos));
        // Modded/artificial lightning can also call this setter. Only its call
        // from ServerLevel#tickChunk may grant the natural-weather marker.
        lightning.setVisualOnly(false);
        helper.getLevel().addFreshEntity(lightning);

        helper.runAfterDelay(2, () -> {
            var data = lightning.getPersistentData();
            helper.assertTrue(data.getBoolean(NATURAL_TRANSFORM_CHECKED_TAG),
                    "The server pre-tick dispatcher must run structure lightning processing");
            helper.assertTrue(data.getBoolean(ITEM_TRANSFORM_CHECKED_TAG),
                    "The server pre-tick dispatcher must run item lightning processing");
            helper.assertTrue(!data.getBoolean(NaturalLightningTransformationHandler.NATURAL_WEATHER_LIGHTNING_TAG),
                    "Artificial lightning must not acquire the natural-weather marker");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void artificialSubclassTransformsItems(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var blank = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("ae2lt", "overload_alloy_blank")), 3));
        var dust = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("ae2lt", "overload_crystal_dust")), 4));
        blank.setNoGravity(true);
        dust.setNoGravity(true);
        level.addFreshEntity(blank);
        level.addFreshEntity(dust);
        var lightning = new NoSuperTickLightningBolt(level);
        lightning.setPos(Vec3.atBottomCenterOf(pos));
        level.addFreshEntity(lightning);
        helper.runAfterDelay(2, () -> {
            var output = BuiltInRegistries.ITEM.get(new ResourceLocation("ae2lt", "overload_alloy"));
            int count = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
                    .filter(item -> item.getItem().is(output))
                    .mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertTrue(count == 1, "Artificial subclass lightning must produce exactly one alloy");
            helper.assertTrue(!blank.isAlive() && !dust.isAlive(), "The recipe must consume both inputs");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void artificialSubclassTransformsSimpleStructure(GameTestHelper helper) {
        checkStructure(helper, "simple_cracked_budding_overload_crystal", false, true);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void artificialSubclassCannotTransformNaturalOnlyStructure(GameTestHelper helper) {
        checkStructure(helper, "rich_flawless_budding_overload_crystal", false, false);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void naturallyMarkedLightningTransformsNaturalOnlyStructure(GameTestHelper helper) {
        // This checks consumption of the marker; weather generation itself is
        // validated separately by applying ServerLevelMixin at server startup.
        checkStructure(helper, "rich_flawless_budding_overload_crystal", true, true);
    }

    private static void checkStructure(GameTestHelper helper, String recipeName,
            boolean natural, boolean shouldTransform) {
        var level = helper.getLevel();
        var recipe = (LightningStrikeRecipe) level.getRecipeManager().byKey(
                new ResourceLocation("ae2lt", "lightning_strike/" + recipeName)).orElseThrow();
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(center, recipe.centerInput().defaultBlockState());
        for (var requirement : recipe.requirements()) {
            level.setBlockAndUpdate(center.offset(requirement.offset()), requirement.block().defaultBlockState());
        }
        level.setBlockAndUpdate(center.above(), Blocks.LIGHTNING_ROD.defaultBlockState());
        var lightning = new NoSuperTickLightningBolt(level);
        lightning.setPos(Vec3.atBottomCenterOf(center.above()));
        if (natural) {
            lightning.getPersistentData().putBoolean(
                    NaturalLightningTransformationHandler.NATURAL_WEATHER_LIGHTNING_TAG, true);
        }
        level.addFreshEntity(lightning);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(level.getBlockState(center).is(
                            shouldTransform ? recipe.centerOutput() : recipe.centerInput()),
                    "Structure transformation must respect the natural-lightning requirement");
            for (var requirement : recipe.requirements()) {
                var state = level.getBlockState(center.offset(requirement.offset()));
                helper.assertTrue(shouldTransform && requirement.consume()
                                ? state.isAir() : state.is(requirement.block()),
                        "Structure ingredients must only be consumed by a successful transformation");
            }
            helper.succeed();
        });
    }

    private static final class NoSuperTickLightningBolt extends LightningBolt {
        private NoSuperTickLightningBolt(Level level) {
            super(EntityType.LIGHTNING_BOLT, level);
        }

        @Override
        public void tick() {
            baseTick();
            discard();
        }
    }
}
