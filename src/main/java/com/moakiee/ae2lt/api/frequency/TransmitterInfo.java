package com.moakiee.ae2lt.api.frequency;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Location of the wireless controller (transmitter) bound to a frequency.
 *
 * @param dimension Dimension key of the controller's level.
 * @param pos       Block position of the controller.
 * @param advanced  True when the controller is the cross-dimensional advanced variant.
 */
public record TransmitterInfo(ResourceKey<Level> dimension, BlockPos pos, boolean advanced) {
}
