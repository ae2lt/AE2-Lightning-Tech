package com.moakiee.ae2lt.logic;

import java.lang.ref.WeakReference;

import net.minecraft.server.level.ServerLevel;

import com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;

/** Owns EJECT-mode registry entries and capability invalidation. */
final class OverloadedEjectController {
    private final OverloadedPatternProviderBlockEntity host;

    OverloadedEjectController(OverloadedPatternProviderBlockEntity host) {
        this.host = host;
    }

    void refresh() {
        var level = host.getLevel();
        // Integrated-server clients share static state with the server. Client
        // block-entity sync must never unregister server-side eject entries.
        if (level != null && level.isClientSide()) {
            return;
        }

        invalidate(EjectModeRegistry.unregisterAll(host, true));
        if (host.getReturnMode() != ReturnMode.EJECT
                || host.getProviderMode() != ProviderMode.WIRELESS
                || !(level instanceof ServerLevel providerLevel)) {
            return;
        }

        for (var connection : host.getConnections()) {
            if (!connection.dimension().equals(providerLevel.dimension())) {
                continue;
            }
            var targetLevel = providerLevel.getServer().getLevel(
                    connection.dimension());
            if (targetLevel == null) {
                continue;
            }

            var adjacentPos = connection.pos().relative(connection.boundFace());
            var queryFace = connection.boundFace().getOpposite();
            var ghostBlockEntity = new GhostOutputBlockEntity(adjacentPos);
            ghostBlockEntity.setLevel(targetLevel);

            EjectModeRegistry.register(
                    targetLevel.dimension(),
                    adjacentPos.asLong(),
                    queryFace,
                    new EjectModeRegistry.EjectEntry(
                            new WeakReference<>(host),
                            ghostBlockEntity,
                            providerLevel.dimension(),
                            host.getBlockPos()));
            // Forge 1.20.1 has no Level.invalidateCapabilities; capabilities are
            // re-queried lazily per getCapability call, so no cache busting needed.
        }
    }

    void clear() {
        invalidate(EjectModeRegistry.unregisterAll(host, true));
    }

    private void invalidate(Iterable<EjectModeRegistry.DimPos> positions) {
        var level = host.getLevel();
        if (!(level instanceof ServerLevel providerLevel)) {
            return;
        }
        var server = providerLevel.getServer();
        for (var position : positions) {
            var targetLevel = server.getLevel(position.dimension());
            if (targetLevel != null) {
                // Forge 1.20.1: no capability cache to invalidate (see refresh()).
            }
        }
    }
}
