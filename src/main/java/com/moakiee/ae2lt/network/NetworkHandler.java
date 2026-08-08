package com.moakiee.ae2lt.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/** Lightweight server-side packet dispatch helpers. */
public final class NetworkHandler {

    private NetworkHandler() {}

    /**
     * Send to all players tracking the given chunk. Uses the chunk's tracker
     * list directly (O(trackers)), and the list naturally includes any player
     * standing in that chunk — callers don't need a separate self-send.
     *
     * <p>1.20.1 Forge: dispatches through {@link NetworkInit#CHANNEL} (SimpleChannel),
     * which requires every payload class to be registered via
     * {@link NetworkInit#register()} first.</p>
     */
    public static void sendToTrackingChunk(ServerLevel level, ChunkPos chunkPos, Object payload) {
        for (ServerPlayer p : level.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
            NetworkInit.sendToPlayer(p, payload);
        }
    }
}
