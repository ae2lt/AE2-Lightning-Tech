package com.moakiee.ae2lt.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyMenu;

/**
 * Block for the Wireless Overloaded Controller (normal version).
 * Opens the frequency management GUI on right-click.
 */
public class WirelessOverloadedControllerBlock extends OverloadedControllerBlock {

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof WirelessOverloadedControllerBlockEntity be) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                // Same membership gate as the receiver: a bound
                // controller rejects non-members on PRIVATE, but
                // ENCRYPTED still opens so strangers can type the
                // password in the Selection tab (and be enrolled via
                // {@code enrollAsUser} on a hash match). Unbound
                // controllers stay open so the placer can configure.
                int freqId = be.getFrequencyId();
                if (freqId > 0) {
                    var manager = WirelessFrequencyManager.get();
                    var freq = manager == null ? null : manager.getFrequency(freqId);
                    if (freq != null
                            && !freq.getPlayerAccess(sp).canUse()
                            && freq.getSecurity() != FrequencySecurityLevel.ENCRYPTED) {
                        sp.displayClientMessage(
                                Component.translatable("ae2lt.gui.error.no_access")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return InteractionResult.sidedSuccess(false);
                    }
                }
                sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, inv, p) -> new FrequencyMenu(id, inv, be),
                        be.getBlockState().getBlock().getName()
                ), buf -> FrequencyMenu.writeExtraData(buf, be));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useWithoutItem(state, level, pos, player, hitResult);
    }
}
