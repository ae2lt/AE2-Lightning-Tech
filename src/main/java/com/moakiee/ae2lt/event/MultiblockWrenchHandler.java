package com.moakiee.ae2lt.event;

import appeng.api.util.DimensionalBlockPos;
import appeng.util.InteractionUtil;
import appeng.util.Platform;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.block.WrenchDisassemblableBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class MultiblockWrenchHandler {
    private MultiblockWrenchHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWrenchDisassemble(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        var level = event.getLevel();
        var pos = event.getPos();
        var state = level.getBlockState(pos);

        if (event.isCanceled()
                || player.isSpectator()
                || event.getHand() != InteractionHand.MAIN_HAND
                || !InteractionUtil.isInAlternateUseMode(player)
                || !InteractionUtil.canWrenchDisassemble(event.getItemStack())
                || !supportsWrenchDisassembly(state.getBlock())) {
            return;
        }

        event.setCanceled(true);
        if (!Platform.hasPermissions(new DimensionalBlockPos(level, pos), player)) {
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));

        if (level instanceof ServerLevel serverLevel) {
            var blockEntity = level.getBlockEntity(pos);
            var drops = Block.getDrops(state, serverLevel, pos, blockEntity, player, event.getItemStack());
            for (var drop : drops) {
                player.getInventory().placeItemBackInInventory(drop);
            }

            var block = state.getBlock();
            block.playerWillDestroy(level, pos, state, player);
            level.removeBlock(pos, false);
            block.destroy(level, pos, state);
        }

        level.playSound(
                player,
                pos,
                SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                SoundSource.BLOCKS,
                0.7F,
                1.0F);
    }

    static boolean supportsWrenchDisassembly(Block block) {
        return block instanceof WrenchDisassemblableBlock;
    }
}
