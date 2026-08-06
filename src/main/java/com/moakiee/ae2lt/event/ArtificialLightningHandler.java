package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.item.OverloadCrystalItem;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class ArtificialLightningHandler {
    private static final String HELD_TICKS_TAG = "ae2lt.overload_held_ticks";
    private static final int SUMMON_DELAY_TICKS = 200;
    /**
     * Granularity of the held-state countdown on the server. With this set to 4, the
     * inventory scan and NBT counter run at 5 Hz instead of 20 Hz, cutting per-player
     * scan cost by 75% while keeping the bolt cadence within ~200ms of the configured
     * 10s delay.
     */
    private static final int HELD_TICK_INTERVAL = 4;

    private ArtificialLightningHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel serverLevel) || player.isSpectator()) {
            return;
        }

        // Sample at HELD_TICK_INTERVAL granularity. The hot path here used to scan up to
        // 37 inventory slots every tick for every player; throttling collapses that to
        // 5 Hz without changing the user-visible 10s cadence.
        if ((player.tickCount % HELD_TICK_INTERVAL) != 0) {
            return;
        }

        boolean carryingOverloadCrystal = isCarryingConfiguredCrystal(player);

        if (!carryingOverloadCrystal) {
            player.getPersistentData().remove(HELD_TICKS_TAG);
            return;
        }

        int heldTicks = player.getPersistentData().getInt(HELD_TICKS_TAG) + HELD_TICK_INTERVAL;
        if (heldTicks < SUMMON_DELAY_TICKS) {
            player.getPersistentData().putInt(HELD_TICKS_TAG, heldTicks);
            return;
        }

        player.getPersistentData().putInt(HELD_TICKS_TAG, 0);
        spawnArtificialLightning(serverLevel, player.position(), player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
    }

    public static void spawnArtificialLightning(ServerLevel level, Vec3 position, @Nullable ServerPlayer cause) {
        LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(level);
        if (lightningBolt == null) {
            return;
        }

        lightningBolt.moveTo(position);
        lightningBolt.setVisualOnly(false);
        // Intentionally do not mark this bolt as natural weather lightning.
        // It may be captured by lightning collectors, but only real weather lightning
        // is allowed to trigger the nearby lightning-rod structure transformation.
        if (cause != null) {
            lightningBolt.setCause(cause);
        }

        level.addFreshEntity(lightningBolt);
    }

    private static boolean isCarryingConfiguredCrystal(Player player) {
        if (AE2LTCommonConfig.artificialLightningTriggerFromHotbar()) {
            if (player.getOffhandItem().getItem() instanceof OverloadCrystalItem) {
                return true;
            }

            for (int slot = 0; slot < 9; slot++) {
                if (player.getInventory().items.get(slot).getItem() instanceof OverloadCrystalItem) {
                    return true;
                }
            }
        }

        if (AE2LTCommonConfig.artificialLightningTriggerFromBackpack()) {
            for (int slot = 9; slot < player.getInventory().items.size(); slot++) {
                if (player.getInventory().items.get(slot).getItem() instanceof OverloadCrystalItem) {
                    return true;
                }
            }
        }

        return false;
    }
}
