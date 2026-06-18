package com.moakiee.ae2lt.network.hub;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.network.NetworkInit;

/**
 * Client → Server: hub UI actions.
 * <p>
 * Action codes: 0=SELECT_TAB, 1=TOGGLE_MODULE, 2=TOGGLE_TERRAIN, 3=TOGGLE_PVP,
 * 4=SELECT_MODULE, 5=CYCLE_MODULE_CONFIG, 6=TOGGLE_SOUND.
 */
public record DeviceHubActionPacket(int action, int value) implements CustomPacketPayload {

    public static final int ACTION_SELECT_TAB = 0;
    public static final int ACTION_TOGGLE_MODULE = 1;
    public static final int ACTION_TOGGLE_TERRAIN = 2;
    public static final int ACTION_TOGGLE_PVP = 3;
    public static final int ACTION_SELECT_MODULE = 4;
    public static final int ACTION_CYCLE_MODULE_CONFIG = 5;
    public static final int ACTION_TOGGLE_SOUND = 6;

    public static final Type<DeviceHubActionPacket> TYPE =
            new Type<>(NetworkInit.id("device_hub_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceHubActionPacket> STREAM_CODEC =
            StreamCodec.ofMember(DeviceHubActionPacket::write, DeviceHubActionPacket::decode);

    @Override
    public Type<DeviceHubActionPacket> type() {
        return TYPE;
    }

    public static DeviceHubActionPacket decode(RegistryFriendlyByteBuf buf) {
        return new DeviceHubActionPacket(buf.readVarInt(), buf.readVarInt());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeVarInt(value);
    }

    public static void handle(DeviceHubActionPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof DeviceHubMenu menu)) return;
            menu.setPlayer(player);
            switch (pkt.action()) {
                case ACTION_SELECT_TAB -> menu.selectTab(pkt.value());
                case ACTION_TOGGLE_MODULE -> menu.toggleModule(pkt.value());
                case ACTION_TOGGLE_TERRAIN -> menu.toggleRailgunTerrain();
                case ACTION_TOGGLE_PVP -> menu.toggleRailgunPvp();
                case ACTION_SELECT_MODULE -> menu.selectModule(pkt.value());
                case ACTION_CYCLE_MODULE_CONFIG -> menu.cycleSelectedModuleConfig(pkt.value());
                case ACTION_TOGGLE_SOUND -> menu.toggleRailgunSound();
            }
        });
    }
}
