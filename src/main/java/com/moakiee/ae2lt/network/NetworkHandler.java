package com.moakiee.ae2lt.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

/** Lightweight server-side packet dispatch helpers (railgun specific). */
public final class NetworkHandler {

    private NetworkHandler() {}

    /**
     * Send to all players tracking the given chunk. Uses the chunk's tracker
     * list directly (O(trackers)), and the list naturally includes any player
     * standing in that chunk — callers don't need a separate self-send.
     */
    public static void sendToTrackingChunk(ServerLevel level, ChunkPos chunkPos, CustomPacketPayload payload) {
        for (ServerPlayer p : level.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
            PacketDistributor.sendToPlayer(p, payload);
        }
    }
}
