package com.moakiee.ae2lt.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorDamageHandler;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.celestweave.service.ArmorCapabilityCollector;
import com.moakiee.ae2lt.celestweave.state.ArmorRuntimeRegistry;
import com.moakiee.ae2lt.registry.ModItems;

/** Executable coverage for the Forge adaptations of the 1.21 phase-system semantics. */
@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class CelestweavePhaseSemanticsGameTests {
    private static final String EMPTY_TEMPLATE = "bastion/mobs/empty";
    private static final float EPSILON = 1.0E-3F;

    private CelestweavePhaseSemanticsGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void completeShieldCancelsBeforeVanillaDamageProcessing(GameTestHelper helper) {
        ServerPlayer player = newTestPlayer(helper, "shield-target");
        ItemStack chest = equipChest(
                helper,
                player,
                ModItems.CELESTWEAVE_SUBMODULE_MULTIDIMENSIONAL_PROTECTION.get());
        try {
            player.setHealth(player.getMaxHealth());
            player.setAbsorptionAmount(4.0F);
            float healthBefore = player.getHealth();
            float absorptionBefore = player.getAbsorptionAmount();

            boolean accepted = player.hurt(helper.getLevel().damageSources().genericKill(), 12.0F);

            helper.assertFalse(accepted, "A completely shielded hit must return false from LivingEntity.hurt");
            assertClose(helper, healthBefore, player.getHealth(),
                    "A completely shielded hit changed player health");
            assertClose(helper, absorptionBefore, player.getAbsorptionAmount(),
                    "A completely shielded hit consumed absorption");
            helper.assertTrue(player.invulnerableTime == 0,
                    "A completely shielded hit reached vanilla invulnerability processing");
            helper.succeed();
        } finally {
            cleanupArmorState(player, chest);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void reflectUsesOriginalHurtAmountAcrossInvulnerabilityFrames(GameTestHelper helper) {
        var player = new FinalDamageDispatchServerPlayer(helper.getLevel());
        ItemStack chest = equipChest(helper, player, ModItems.CELESTWEAVE_SUBMODULE_REFLECT.get());
        ArmorEnergyBuffer.write(chest, helper.getLevel().registryAccess(), 100_000L);
        try {
            var attacker = new RecordingServerPlayer(helper.getLevel());
            // genericKill bypasses a freshly constructed player's spawn protection. A player
            // attacker prevents difficulty scaling; the test target explicitly admits PVP below.
            DamageSource source = new DamageSource(
                    helper.getLevel()
                            .registryAccess()
                            .registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(DamageTypes.GENERIC_KILL),
                    attacker);

            hurtUnconnectedPlayer(player, source, 4.0F);
            hurtUnconnectedPlayer(player, source, 10.0F);

            assertClose(helper, 4.2F, attacker.reflectedDamage,
                    "The second reflection used the cooldown delta instead of the original 10 damage");

            var fallbackAttacker = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 1));
            fallbackAttacker.setHealth(fallbackAttacker.getMaxHealth());
            CelestweaveArmorDamageHandler.onPre(new LivingDamageEvent(
                    player,
                    helper.getLevel().damageSources().mobAttack(fallbackAttacker),
                    2.0F));
            assertClose(helper, fallbackAttacker.getMaxHealth() - 0.6F, fallbackAttacker.getHealth(),
                    "The completed hurt call left stale original-damage scope behind");
            helper.succeed();
        } finally {
            cleanupArmorState(player, chest);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void originalDamageScopeIsClearedWhenActuallyHurtThrows(GameTestHelper helper) {
        var player = new ThrowingServerPlayer(helper.getLevel());
        ItemStack chest = equipChest(helper, player, ModItems.CELESTWEAVE_SUBMODULE_REFLECT.get());
        ArmorEnergyBuffer.write(chest, helper.getLevel().registryAccess(), 100_000L);
        try {
            boolean threw = false;
            try {
                player.hurt(helper.getLevel().damageSources().genericKill(), 11.0F);
            } catch (ExpectedDamageException expected) {
                threw = true;
            }
            helper.assertTrue(threw, "The throwing player did not exercise the actuallyHurt wrapper");

            var attacker = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
            attacker.setHealth(attacker.getMaxHealth());
            CelestweaveArmorDamageHandler.onPre(new LivingDamageEvent(
                    player,
                    helper.getLevel().damageSources().mobAttack(attacker),
                    2.0F));
            assertClose(helper, attacker.getMaxHealth() - 0.6F, attacker.getHealth(),
                    "Exceptional damage processing leaked the original 11 damage into the next event");
            helper.succeed();
        } finally {
            cleanupArmorState(player, chest);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void payloadTeleportAuthorizationIsSenderBoundAndFinallySafe(GameTestHelper helper) {
        ServerPlayer sender = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "payload-sender"));
        ServerPlayer other = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "payload-other"));

        helper.assertFalse(PhaseFlightMovementGuard.isSelfTeleportAuthorized(sender),
                "The sender started with stale payload authorization");
        PhaseFlightMovementGuard.runAsPlayerPayloadTeleport(sender, () -> {
            helper.assertTrue(PhaseFlightMovementGuard.isSelfTeleportAuthorized(sender),
                    "The sending player was not authorized inside its payload task");
            helper.assertFalse(PhaseFlightMovementGuard.isSelfTeleportAuthorized(other),
                    "One player's payload authorized a different player");
            PhaseFlightMovementGuard.runAsPlayerPayloadTeleport(sender, () ->
                    helper.assertTrue(PhaseFlightMovementGuard.isSelfTeleportAuthorized(sender),
                            "Nested payload authorization lost the outer sender"));
            helper.assertTrue(PhaseFlightMovementGuard.isSelfTeleportAuthorized(sender),
                    "Closing a nested payload scope cleared the outer scope");
        });
        helper.assertFalse(PhaseFlightMovementGuard.isSelfTeleportAuthorized(sender),
                "A completed payload task leaked teleport authorization");

        boolean threw = false;
        try {
            PhaseFlightMovementGuard.runAsPlayerPayloadTeleport(sender, () -> {
                throw new ExpectedPayloadException();
            });
        } catch (ExpectedPayloadException expected) {
            threw = true;
        } finally {
            PhaseFlightMovementGuard.clear(sender);
            PhaseFlightMovementGuard.clear(other);
        }
        helper.assertTrue(threw, "The exceptional payload path was not exercised");
        helper.assertFalse(PhaseFlightMovementGuard.isSelfTeleportAuthorized(sender),
                "An exceptional payload task leaked teleport authorization");
        helper.succeed();
    }

    private static ItemStack equipChest(GameTestHelper helper, ServerPlayer player, Item... modules) {
        var registries = helper.getLevel().registryAccess();
        ItemStack chest = new ItemStack(ModItems.CELESTWEAVE_CORE.get());
        CelestweaveArmorState.setSlot(
                chest,
                registries,
                CelestweaveArmorState.SLOT_CORE,
                new ItemStack(ModItems.ULTIMATE_OVERLOAD_CORE.get()));
        UUID armorId = CelestweaveArmorState.ensureArmorId(chest);
        for (Item module : modules) {
            ItemStack moduleStack = new ItemStack(module);
            helper.assertTrue(CelestweaveArmorState.installOneModule(chest, registries, moduleStack),
                    "Failed to install test armor module: " + module);
            String moduleId = CelestweaveArmorState.moduleTypeId(moduleStack);
            ArmorRuntimeRegistry.setSubmoduleRuntimeActive(armorId, moduleId, true);
        }
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        ArmorCapabilityCollector.clearCache(player);
        return chest;
    }

    private static void cleanupArmorState(ServerPlayer player, ItemStack chest) {
        ArmorCapabilityCollector.clearCache(player);
        CelestweaveArmorState.clearTransientRuntimeAndCaches(chest);
        PhaseFlightMovementGuard.clear(player);
    }

    private static void assertClose(GameTestHelper helper, float expected, float actual, String message) {
        helper.assertTrue(Math.abs(expected - actual) <= EPSILON,
                message + ": expected " + expected + ", got " + actual);
    }

    private static void hurtUnconnectedPlayer(
            FinalDamageDispatchServerPlayer player,
            DamageSource source,
            float amount) {
        int dispatchesBefore = player.finalDamageDispatches;
        try {
            player.hurt(source, amount);
        } catch (NullPointerException expectedPostDamageBroadcast) {
            if (player.connection != null || player.finalDamageDispatches != dispatchesBefore + 1) {
                throw expectedPostDamageBroadcast;
            }
        }
        if (player.finalDamageDispatches != dispatchesBefore + 1) {
            throw new AssertionError("hurt did not reach the final-damage dispatch");
        }
    }

    private static ServerPlayer newTestPlayer(GameTestHelper helper, String name) {
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = false;
        return player;
    }

    private static final class ThrowingServerPlayer extends ServerPlayer {
        private ThrowingServerPlayer(ServerLevel level) {
            super(
                    level.getServer(),
                    level,
                    new GameProfile(UUID.randomUUID(), "damage-thrower"));
        }

        @Override
        protected void actuallyHurt(DamageSource source, float amount) {
            throw new ExpectedDamageException();
        }
    }

    private static final class FinalDamageDispatchServerPlayer extends ServerPlayer {
        private int finalDamageDispatches;

        private FinalDamageDispatchServerPlayer(ServerLevel level) {
            super(
                    level.getServer(),
                    level,
                    new GameProfile(UUID.randomUUID(), "final-damage-dispatcher"));
        }

        @Override
        protected void actuallyHurt(DamageSource source, float amount) {
            // Preserve hurt's cooldown-reduced amount without invoking unrelated handlers that
            // require a real ServerGamePacketListener connection in the test environment.
            CelestweaveArmorDamageHandler.onPre(new LivingDamageEvent(this, source, amount));
            finalDamageDispatches++;
        }

        @Override
        public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) {
            return true;
        }
    }

    private static final class RecordingServerPlayer extends ServerPlayer {
        private float reflectedDamage;

        private RecordingServerPlayer(ServerLevel level) {
            super(
                    level.getServer(),
                    level,
                    new GameProfile(UUID.randomUUID(), "reflection-recorder"));
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            reflectedDamage += amount;
            return true;
        }
    }

    private static final class ExpectedDamageException extends RuntimeException {
    }

    private static final class ExpectedPayloadException extends RuntimeException {
    }
}
