package com.moakiee.ae2lt.api.patternprovider;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.logic.wireless.support.WirelessConnectionRef;

/**
 * Public connector-facing contract for wireless pattern providers.
 *
 * <p>Addon providers can participate in AE2LT's connector, renderer and batch
 * editing flows without extending AE2LT's concrete provider implementation.
 */
public interface WirelessPatternProviderHost {
    /** World position of the provider. */
    BlockPos getProviderPos();

    /** Whether this provider currently accepts wireless machine connections. */
    boolean isWirelessProvider();

    /** Read-only live view of the configured endpoints. */
    List<? extends WirelessConnectionRef> getConnections();

    /** Adds a new endpoint or changes the selected face of an existing endpoint. */
    boolean addOrUpdateConnection(
            ResourceKey<Level> dimension, BlockPos pos, Direction boundFace);

    /** Removes an endpoint identified by dimension and block position. */
    boolean removeConnection(ResourceKey<Level> dimension, BlockPos pos);

    /** Maximum number of endpoint records this host accepts. */
    int getMaxWirelessConnections();
}

