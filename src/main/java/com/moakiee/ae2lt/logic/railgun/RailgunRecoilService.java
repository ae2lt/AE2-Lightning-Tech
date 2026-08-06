package com.moakiee.ae2lt.logic.railgun;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.item.railgun.RailgunChargeTier;
import com.moakiee.ae2lt.network.railgun.RailgunRecoilFxPacket;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.celestweave.phase.CelestweaveEquipmentAccess;

/**
 * Applies recoil to charged-fire only. Pushes the player backward + fires a
 * client packet for view-pitch + camera shake. Sets {@code hurtMarked = true}
 * so the velocity is synced to the client and resets {@code fallDistance} so
 * the recoil doesn't kill players from chained jumps.
 */
public final class RailgunRecoilService {

    /** Persistent-data key set when recoil happens; LivingFallEvent reads this and cancels fall. */
    public static final String RECOIL_GRACE_TAG = "ae2lt.railgun_recoil_grace";

    private RailgunRecoilService() {}

    public static void apply(ServerPlayer player, RailgunChargeTier tier) {
        if (tier == RailgunChargeTier.HV) return;
        if (wearingFullCelestweaveSet(player)) return;

        double speed = switch (tier) {
            case EHV1 -> RailgunDefaults.RECOIL_SPEED_TIER1;
            case EHV2 -> RailgunDefaults.RECOIL_SPEED_TIER2;
            case EHV3 -> RailgunDefaults.RECOIL_SPEED_TIER3;
            default -> 0.0D;
        };
        float pitchUp = switch (tier) {
            case EHV1 -> 6f;
            case EHV2 -> 12f;
            case EHV3 -> 20f;
            default -> 0f;
        };

        if (player.isCrouching()) {
            speed *= RailgunDefaults.RECOIL_CROUCH_MUL;
            pitchUp *= (float) RailgunDefaults.RECOIL_CROUCH_MUL;
        }
        if (!player.onGround()) {
            speed *= RailgunDefaults.RECOIL_AIRBORNE_MUL;
        }

        // Wall-check: any non-air collidable block 1 cell behind kills speed.
        Vec3 backDir = player.getLookAngle().scale(-1).normalize();
        Level level = player.level();
        BlockPos bp = BlockPos.containing(player.position().add(backDir));
        if (!level.getBlockState(bp).isAir()
                && !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
            speed = 0.0D;
        }

        Vec3 push = backDir.scale(speed);
        PhaseFlightMovementGuard.runAsSelfMovement(
                player,
                () -> player.setDeltaMovement(player.getDeltaMovement().add(push)));
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        player.getPersistentData().putLong(RECOIL_GRACE_TAG, level.getGameTime() + 60L);

        PacketDistributor.sendToPlayer(player, new RailgunRecoilFxPacket(pitchUp, tier.ordinal()));
    }

    private static boolean wearingFullCelestweaveSet(ServerPlayer player) {
        return CelestweaveEquipmentAccess.findArmor(player, EquipmentSlot.HEAD).is(ModItems.CELESTWEAVE_OCULUS.get())
                && CelestweaveEquipmentAccess.findArmor(player, EquipmentSlot.CHEST).is(ModItems.CELESTWEAVE_CORE.get())
                && CelestweaveEquipmentAccess.findArmor(player, EquipmentSlot.LEGS).is(ModItems.CELESTWEAVE_CONDUIT.get())
                && CelestweaveEquipmentAccess.findArmor(player, EquipmentSlot.FEET).is(ModItems.CELESTWEAVE_STRIDE.get());
    }

    public static boolean inRecoilGrace(ServerPlayer player) {
        long t = player.getPersistentData().getLong(RECOIL_GRACE_TAG);
        if (t == 0L) return false;
        return player.level().getGameTime() <= t;
    }
}
