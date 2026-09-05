package com.moakiee.ae2lt.block;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.blockentity.PigmeeSynthesisStationBlockEntity;
import com.moakiee.ae2lt.menu.PigmeeSynthesisStationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A Pigmee-themed crafting terminal that reads adjacent non-ME capabilities.
 *
 * <p>The block deliberately has no ME storage capability of its own. It is a
 * terminal facade for an adjacent item/fluid capability and needs no network.</p>
 */
public final class PigmeeSynthesisStationBlock
        extends AEBaseEntityBlock<PigmeeSynthesisStationBlockEntity> {
    public PigmeeSynthesisStationBlock() {
        super(metalProps().noOcclusion());
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult) {
        var blockEntity = getBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            MenuOpener.open(
                    PigmeeSynthesisStationMenu.TYPE,
                    player,
                    MenuLocators.forBlockEntity(blockEntity));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
