package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.api.frequency.FrequencyBindingHost;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record SelectFrequencyPacket(
        int token,
        BlockPos blockPos, int frequencyId, String password
) {

    public static void encode(SelectFrequencyPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.token);
        buf.writeBlockPos(pkt.blockPos);
        buf.writeInt(pkt.frequencyId);
        buf.writeUtf(pkt.password, WirelessFrequency.MAX_PASSWORD_LENGTH);
    }

    public static SelectFrequencyPacket decode(FriendlyByteBuf buf) {
        return new SelectFrequencyPacket(
                buf.readVarInt(),
                buf.readBlockPos(),
                buf.readInt(),
                buf.readUtf(WirelessFrequency.MAX_PASSWORD_LENGTH));
    }

    public static void handle(SelectFrequencyPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        var ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            FrequencyMenu menu = FrequencyMenu.validateToken(player, pkt.token);
            if (menu == null || !menu.getBlockPos().equals(pkt.blockPos)) {
                NetworkInit.sendToPlayer(player, new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                return;
            }

            var level = player.serverLevel();
            var be = level.getBlockEntity(pkt.blockPos);

            // Resolve the device's CURRENT frequency so the block-op
            // access gate below knows what permission to verify. Any
            // non-wireless block entity at this pos is a REJECTED
            // target — controllers and frequency-bound devices are the only things
            // with a frequency binding.
            int currentFreqId;
            if (be instanceof WirelessOverloadedControllerBlockEntity ctrl) {
                currentFreqId = ctrl.getFrequencyId();
            } else if (be instanceof FrequencyBindingHost bindingHost) {
                currentFreqId = bindingHost.getFrequencyId();
            } else {
                NetworkInit.sendToPlayer(player,
                        new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                return;
            }

            var manager = WirelessFrequencyManager.get();
            if (manager == null) return;

            // Block-op access gate: if the device is currently bound to
            // a frequency AND the player is trying to change that
            // binding (disconnect OR switch-to-different), they need
            // at least USE-level access on the CURRENT freq. Skips:
            //   - unbound devices (currentFreqId <= 0): fresh config
            //   - same-id re-select (pkt.frequencyId == currentFreqId):
            //     this is how an ENCRYPTED outsider submits their
            //     password to JOIN the current freq; the later
            //     {@code canPlayerAccess} check does the real work.
            boolean changingBinding = pkt.frequencyId != currentFreqId;
            if (changingBinding && currentFreqId > 0) {
                var currentFreq = manager.getFrequency(currentFreqId);
                if (currentFreq != null && !currentFreq.canPlayerAccess(player, "")) {
                    NetworkInit.sendToPlayer(player,
                            new FrequencyResponsePacket(FrequencyResponsePacket.NO_PERMISSION));
                    return;
                }
            }

            // disconnect
            if (pkt.frequencyId <= 0) {
                if (be instanceof WirelessOverloadedControllerBlockEntity ctrl) {
                    ctrl.clearFrequency();
                } else {
                    ((FrequencyBindingHost) be).clearFrequency();
                }
                // DataSlot handles freqId sync back to client
                return;
            }

            WirelessFrequency freq = manager.getFrequency(pkt.frequencyId);
            if (freq == null) {
                NetworkInit.sendToPlayer(player,
                        new FrequencyResponsePacket(FrequencyResponsePacket.INVALID_FREQUENCY));
                return;
            }

            if (!freq.canPlayerAccess(player, pkt.password)) {
                if (freq.getSecurity() == FrequencySecurityLevel.ENCRYPTED
                        && !freq.getPlayerAccess(player).canUse()
                        && pkt.password.isBlank()) {
                    NetworkInit.sendToPlayer(player,
                            new FrequencyResponsePacket(FrequencyResponsePacket.REQUIRE_PASSWORD));
                } else if (freq.getSecurity() == FrequencySecurityLevel.ENCRYPTED
                        && !freq.getPlayerAccess(player).canUse()) {
                    NetworkInit.sendToPlayer(player,
                            new FrequencyResponsePacket(FrequencyResponsePacket.REJECTED));
                } else {
                    NetworkInit.sendToPlayer(player,
                            new FrequencyResponsePacket(FrequencyResponsePacket.NO_PERMISSION));
                }
                return;
            }

            // Durable auto-enroll: anyone who cleared the
            // {@code canPlayerAccess} check above got there via one of
            //   (a) existing member, or
            //   (b) PUBLIC fallback USER access, or
            //   (c) ENCRYPTED + correct password.
            // Cases (b) and (c) leave the player with access but no
            // persistent membership entry, so the Members tab and
            // subsequent session re-opens would forget them. Promoting
            // both to a real USER row makes access stable and the
            // member list consistent with "who actually uses this freq".
            if (!freq.isMember(player) && freq.enrollAsUser(player)) {
                manager.markModified();
                SyncFrequencyDetailPacket.broadcastMembersTo(player.getServer(), pkt.frequencyId);
            }

            if (be instanceof WirelessOverloadedControllerBlockEntity
                    && !manager.canRegisterTransmitter(pkt.frequencyId, level.dimension(), pkt.blockPos)) {
                NetworkInit.sendToPlayer(player,
                        new FrequencyResponsePacket(FrequencyResponsePacket.FREQUENCY_IN_USE));
                return;
            }

            if (be instanceof WirelessOverloadedControllerBlockEntity ctrl) {
                ctrl.setFrequency(pkt.frequencyId);
            } else {
                ((FrequencyBindingHost) be).setFrequency(pkt.frequencyId);
            }
            // DataSlot handles freqId sync; members may have been updated above
        });
        ctx.setPacketHandled(true);
    }
}

