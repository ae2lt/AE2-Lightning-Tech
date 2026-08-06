package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;

public class OverloadedInterfaceBlock extends AE2LTBaseEntityBlock<OverloadedInterfaceBlockEntity> {

    public OverloadedInterfaceBlock() {
        super(metalProps().forceSolidOn());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (InteractionUtil.isInAlternateUseMode(player)) {
            return InteractionResult.PASS;
        }

        var be = this.getBlockEntity(level, pos);
        if (be != null) {
            if (!level.isClientSide()) {
                be.openMenu(player, MenuLocators.forBlockEntity(be));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }
}

