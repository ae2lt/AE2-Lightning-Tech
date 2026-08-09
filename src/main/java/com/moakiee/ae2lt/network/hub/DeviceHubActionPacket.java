package com.moakiee.ae2lt.network.hub;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;

/**
 * Client → Server: hub UI actions.
 * <p>
 * Action codes: 0=SELECT_TAB, 1=TOGGLE_MODULE, 2=TOGGLE_TERRAIN, 3=TOGGLE_PVP,
 * 4=SELECT_MODULE, 5=CYCLE_MODULE_CONFIG, 6=TOGGLE_SOUND, 7=TOGGLE_CHAIN_DAMAGE,
 * 8=TOGGLE_OVERLOAD_REMOVAL_MODE, 9=TOGGLE_CHARGED_SPLASH.
 */
public record DeviceHubActionPacket(int action, int value) {

    public static final int ACTION_SELECT_TAB = 0;
    public static final int ACTION_TOGGLE_MODULE = 1;
    public static final int ACTION_TOGGLE_TERRAIN = 2;
    public static final int ACTION_TOGGLE_PVP = 3;
    public static final int ACTION_SELECT_MODULE = 4;
    public static final int ACTION_CYCLE_MODULE_CONFIG = 5;
    public static final int ACTION_TOGGLE_SOUND = 6;
    public static final int ACTION_TOGGLE_CHAIN_DAMAGE = 7;
    public static final int ACTION_TOGGLE_OVERLOAD_REMOVAL_MODE = 8;
    public static final int ACTION_TOGGLE_CHARGED_SPLASH = 9;

    public static DeviceHubActionPacket decode(FriendlyByteBuf buf) {
        return new DeviceHubActionPacket(buf.readVarInt(), buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeVarInt(value);
    }

    public static void handle(DeviceHubActionPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
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
                case ACTION_TOGGLE_CHAIN_DAMAGE -> menu.toggleRailgunChainDamage();
                case ACTION_TOGGLE_OVERLOAD_REMOVAL_MODE -> menu.toggleRailgunOverloadRemovalMode();
                case ACTION_TOGGLE_CHARGED_SPLASH -> menu.toggleRailgunChargedSplash();
            }
        });
        ctx.setPacketHandled(true);
    }
}
