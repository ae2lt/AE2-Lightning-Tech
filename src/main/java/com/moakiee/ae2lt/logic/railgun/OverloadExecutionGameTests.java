package com.moakiee.ae2lt.logic.railgun;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.moakiee.ae2lt.AE2LightningTech;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class OverloadExecutionGameTests {
    private OverloadExecutionGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void canceledDeathCallbackFallsBackToNormalSettlement(GameTestHelper helper) {
        var target = new CancelingZombie(helper.getLevel());

        OverloadExecutionService.completeNormalDeath(
                target,
                helper.getLevel().damageSources().genericKill(),
                target.getMaxHealth());

        helper.assertTrue(target.dead, "Fallback did not commit the LivingEntity death state");
        helper.assertTrue(target.getPose() == Pose.DYING, "Fallback did not enter the dying pose");
        helper.succeed();
    }

    private static final class CancelingZombie extends Zombie {
        private CancelingZombie(Level level) {
            super(level);
        }

        @Override
        public void die(DamageSource source) {
            // Model a third-party entity callback that cancels its own settlement.
        }

        @Override
        public void dropAllDeathLoot(DamageSource source) {
            // Keep the isolated fallback test free from loot-table side effects.
        }
    }
}
