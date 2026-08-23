package com.moakiee.ae2lt.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.moakiee.ae2lt.AE2LightningTech;

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
        helper.getLevel().addFreshEntity(lightning);

        helper.runAfterDelay(2, () -> {
            var data = lightning.getPersistentData();
            helper.assertTrue(data.getBoolean(NATURAL_TRANSFORM_CHECKED_TAG),
                    "The server pre-tick dispatcher must run structure lightning processing");
            helper.assertTrue(data.getBoolean(ITEM_TRANSFORM_CHECKED_TAG),
                    "The server pre-tick dispatcher must run item lightning processing");
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
