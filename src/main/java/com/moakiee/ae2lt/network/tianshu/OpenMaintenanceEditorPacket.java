package com.moakiee.ae2lt.network.tianshu;

import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.NetworkInit;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import appeng.api.stacks.AEKey;

public record OpenMaintenanceEditorPacket(int containerId, AEKey key) implements CustomPacketPayload {
    public static final Type<OpenMaintenanceEditorPacket> TYPE =
            new Type<>(NetworkInit.id("open_maintenance_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMaintenanceEditorPacket> STREAM_CODEC =
            StreamCodec.ofMember(OpenMaintenanceEditorPacket::write, OpenMaintenanceEditorPacket::decode);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        AEKey.STREAM_CODEC.encode(buf, key);
    }

    private static OpenMaintenanceEditorPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenMaintenanceEditorPacket(buf.readVarInt(), AEKey.STREAM_CODEC.decode(buf));
    }

    @Override public Type<OpenMaintenanceEditorPacket> type() { return TYPE; }

    public static void handle(OpenMaintenanceEditorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof TianshuPatternEncodingTermMenu menu
                    && menu.containerId == packet.containerId()) {
                menu.openMaintenanceEditor(packet.key());
            }
        });
    }
}
