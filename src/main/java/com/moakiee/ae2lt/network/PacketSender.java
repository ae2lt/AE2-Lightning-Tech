package com.moakiee.ae2lt.network;

import net.minecraft.server.level.ServerPlayer;

/**
 * Thin client/server dispatch facade used by menus and screens.
 *
 * <p>1.20.1 Forge: everything funnels through the {@link NetworkInit#CHANNEL}
 * SimpleChannel, so every message class must be registered in
 * {@link NetworkInit#register()} before it can be sent.</p>
 */
public final class PacketSender {

    private PacketSender() {
    }

    /** Client → server (PLAY_TO_SERVER registered messages). */
    public static void sendToServer(Object message) {
        NetworkInit.sendToServer(message);
    }

    /** Server → a single player (PLAY_TO_CLIENT registered messages). */
    public static void sendToPlayer(ServerPlayer player, Object message) {
        NetworkInit.sendToPlayer(player, message);
    }
}
