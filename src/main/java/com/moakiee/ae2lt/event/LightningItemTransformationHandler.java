package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.lightning.LightningTransformService;
import com.moakiee.ae2lt.lightning.ProtectedItemEntityHelper;
import com.moakiee.ae2lt.logic.research.ResearchRitualService;
import com.moakiee.ae2lt.network.EasterEggPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class LightningItemTransformationHandler {
    private static final String TRANSFORMATION_CHECKED_TAG = "ae2lt.lightning_item_transform_checked";
    private static final ResourceLocation FUMO_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath("appliedcreate", "whichball_skin_doll");
    private static final int EASTER_EGG_SEARCH_RADIUS = 3;

    private LightningItemTransformationHandler() {
    }

    @SubscribeEvent
    public static void onLightningTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LightningBolt lightningBolt)
                || !(lightningBolt.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var data = lightningBolt.getPersistentData();
        if (data.getBoolean(TRANSFORMATION_CHECKED_TAG)) {
            return;
        }

        data.putBoolean(TRANSFORMATION_CHECKED_TAG, true);
        ResearchRitualService.handleLightning(serverLevel, lightningBolt);
        LightningTransformService.handleLightning(serverLevel, lightningBolt);
        checkEasterEgg(serverLevel, lightningBolt);
    }

    @SubscribeEvent
    public static void onEntityStruckByLightning(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof ItemEntity itemEntity
                && (ProtectedItemEntityHelper.isProtectedItem(itemEntity)
                        || ProtectedItemEntityHelper.isFireproofItem(itemEntity))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        if (event.getEntity() instanceof ItemEntity itemEntity
                && ProtectedItemEntityHelper.shouldIgnoreDamage(itemEntity, event.getSource())) {
            event.setInvulnerable(true);
        }
    }

    @SubscribeEvent
    public static void onItemTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof ItemEntity itemEntity) {
            ProtectedItemEntityHelper.tick(itemEntity);
        }
    }

    private static void checkEasterEgg(ServerLevel level, LightningBolt lightningBolt) {
        var fumoOpt = BuiltInRegistries.BLOCK.getOptional(FUMO_BLOCK_ID);
        if (fumoOpt.isEmpty()) {
            return;
        }
        Block fumoBlock = fumoOpt.get();

        BlockPos center = BlockPos.containing(lightningBolt.position());
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-EASTER_EGG_SEARCH_RADIUS, -1, -EASTER_EGG_SEARCH_RADIUS),
                center.offset(EASTER_EGG_SEARCH_RADIUS, 2, EASTER_EGG_SEARCH_RADIUS))) {
            if (level.getBlockState(pos).is(fumoBlock)) {
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 64 * 64) {
                        PacketDistributor.sendToPlayer(player, new EasterEggPacket());
                    }
                }
                return;
            }
        }
    }
}
