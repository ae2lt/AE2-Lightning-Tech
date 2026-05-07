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

import appeng.block.AEBaseEntityBlock;

import com.moakiee.ae2lt.blockentity.WirelessReceiverBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyMenu;

/**
 * Wireless receiver block. Right-click to open the frequency selection GUI.
 */
public class WirelessReceiverBlock extends AEBaseEntityBlock<WirelessReceiverBlockEntity> {

    public WirelessReceiverBlock() {
        super(metalProps());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof WirelessReceiverBlockEntity be) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                // Gate menu-open on frequency membership. Non-members
                // are rejected on PRIVATE frequencies; ENCRYPTED is a
                // deliberate escape hatch so strangers can still open
                // the GUI to enter the password (Selection tab auto-
                // enrolls them via {@code enrollAsUser} on a correct
                // hash match). Unbound devices (freqId <= 0) are
                // always accessible so whoever places the block can
                // assign a frequency.
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
