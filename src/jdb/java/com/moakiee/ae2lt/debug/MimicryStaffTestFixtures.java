package com.moakiee.ae2lt.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = "ae2lt", bus = EventBusSubscriber.Bus.MOD)
public final class MimicryStaffTestFixtures {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, ResourceLocation.parse("ae2lt_staff:custom_progress_ore"), () ->
                new Block(Block.Properties.of().strength(-1, 1500).requiresCorrectToolForDrops()) {
                    @Override
                    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
                        if (player.isShiftKeyDown()) return 0;
                        return player.getDigSpeed(state, pos) / (player.hasCorrectToolForDrops(state, player.level(), pos) ? 500 : 3000);
                    }
                });
    }
}
