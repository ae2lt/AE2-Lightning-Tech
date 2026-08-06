package com.moakiee.ae2lt.api.frequency;

import java.util.Optional;
import java.util.OptionalInt;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * SPI bridged by the AE2LT mod itself at common setup time. Addon authors
 * must not implement this interface — call {@link FrequencyApi} instead.
 */
@ApiStatus.Internal
public interface FrequencyApiProvider {
    OptionalInt getBoundFrequencyId(BlockEntity blockEntity);

    Optional<FrequencyInfo> getFrequencyInfo(MinecraftServer server, int frequencyId);

    Optional<TransmitterInfo> getTransmitter(MinecraftServer server, int frequencyId);

    boolean isValidFrequency(MinecraftServer server, int frequencyId);

    FrequencyBindingAccess createBinding(FrequencyBindingHost host);

    /** Client-thread only: send the open-binding-menu request to the server. */
    void openBindingScreen(AbstractContainerMenu menu);
}
