package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.menu.OverloadAlloyAnvilMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A stationary anvil. Work, XP and enchantments use the native anvil menu. */
public final class OverloadAlloyAnvilBlock extends AnvilBlock {
    public static final MapCodec<AnvilBlock> CODEC = simpleCodec(OverloadAlloyAnvilBlock::new);
    public OverloadAlloyAnvilBlock(Properties properties) { super(properties); }
    @Override public MapCodec<AnvilBlock> codec() { return CODEC; }
    @Override protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) ->
                new OverloadAlloyAnvilMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
                Component.translatable("block.ae2lt.overload_alloy_anvil"));
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // The mounted alloy anvil has no falling state or falling-entity damage cycle.
    }
}
