package com.moakiee.ae2lt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.blockentity.OverloadDeviceWorkbenchBlockEntity;

public class OverloadDeviceWorkbenchBlock extends AEBaseEntityBlock<OverloadDeviceWorkbenchBlockEntity> {
    public OverloadDeviceWorkbenchBlock() {
        // The model has open sections between its base, work surface and top.
        // Treating it as a full occluding cube makes adjacent blocks cull their
        // entire shared face, which leaves visible holes through those sections.
        super(metalProps().noOcclusion().forceSolidOn());
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        var blockEntity = getBlockEntity(level, pos);
        if (blockEntity != null) {
            if (!level.isClientSide()) {
                blockEntity.openMenu(player, MenuLocators.forBlockEntity(blockEntity));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }
}
