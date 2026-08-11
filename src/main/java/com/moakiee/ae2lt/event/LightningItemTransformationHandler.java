package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.lightning.LightningTransformService;
import com.moakiee.ae2lt.lightning.ProtectedItemEntityHelper;
import com.moakiee.ae2lt.logic.research.ResearchRitualService;
import com.moakiee.ae2lt.network.EasterEggPacket;
import com.moakiee.ae2lt.network.NetworkInit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class LightningItemTransformationHandler {
    private static final String TRANSFORMATION_CHECKED_TAG = "ae2lt.lightning_item_transform_checked";
    private static final ResourceLocation FUMO_BLOCK_ID =
            new ResourceLocation("appliedcreate", "whichball_skin_doll");
    private static final int EASTER_EGG_SEARCH_RADIUS = 3;

    private LightningItemTransformationHandler() {
    }

    public static void handleLightningTick(LightningBolt lightningBolt) {
        if (!(lightningBolt.level() instanceof ServerLevel serverLevel)) {
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
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel serverLevel)) {
            return;
        }

        for (var entity : serverLevel.getAllEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                ProtectedItemEntityHelper.tick(itemEntity);
            }
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
                        NetworkInit.sendToPlayer(player, new EasterEggPacket());
                    }
                }
                return;
            }
        }
    }
}

