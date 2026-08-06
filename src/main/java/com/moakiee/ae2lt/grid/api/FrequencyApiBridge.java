package com.moakiee.ae2lt.grid.api;

import java.util.Optional;
import java.util.OptionalInt;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import com.moakiee.ae2lt.api.frequency.FrequencyApiProvider;
import com.moakiee.ae2lt.api.frequency.FrequencyBindingAccess;
import com.moakiee.ae2lt.api.frequency.FrequencyBindingHost;
import com.moakiee.ae2lt.api.frequency.FrequencyInfo;
import com.moakiee.ae2lt.api.frequency.FrequencySecurity;
import com.moakiee.ae2lt.api.frequency.TransmitterInfo;
import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequency;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.network.OpenFrequencyMenuPacket;

/**
 * Internal bridge between the public {@code api.frequency} surface and the
 * private {@link WirelessFrequencyManager} / {@link FrequencyBindingHelper}.
 * Installed at mod commonSetup.
 */
public final class FrequencyApiBridge implements FrequencyApiProvider {
    @Override
    public OptionalInt getBoundFrequencyId(BlockEntity blockEntity) {
        if (blockEntity instanceof FrequencyBindingHost host) {
            int id = host.getFrequencyId();
            return id > 0 ? OptionalInt.of(id) : OptionalInt.empty();
        }
        if (blockEntity instanceof WirelessFrequencyManager.WirelessTransmitterNodeProvider provider) {
            int id = provider.getTransmitterFrequencyId();
            return id > 0 ? OptionalInt.of(id) : OptionalInt.empty();
        }
        return OptionalInt.empty();
    }

    @Override
    public Optional<FrequencyInfo> getFrequencyInfo(MinecraftServer server, int frequencyId) {
        var manager = WirelessFrequencyManager.get();
        if (manager == null || frequencyId <= 0) return Optional.empty();
        WirelessFrequency freq = manager.getFrequency(frequencyId);
        if (freq == null) return Optional.empty();
        return Optional.of(new FrequencyInfo(
                freq.getId(),
                freq.getName(),
                freq.getColor(),
                freq.getOwnerUUID(),
                toApiSecurity(freq.getSecurity())));
    }

    @Override
    public Optional<TransmitterInfo> getTransmitter(MinecraftServer server, int frequencyId) {
        var manager = WirelessFrequencyManager.get();
        if (manager == null || frequencyId <= 0) return Optional.empty();
        var entry = manager.findTransmitter(frequencyId);
        if (entry == null) return Optional.empty();
        return Optional.of(new TransmitterInfo(entry.dimension(), entry.pos(), entry.advanced()));
    }

    @Override
    public boolean isValidFrequency(MinecraftServer server, int frequencyId) {
        var manager = WirelessFrequencyManager.get();
        return manager != null && manager.isFrequencyValid(frequencyId);
    }

    @Override
    public FrequencyBindingAccess createBinding(FrequencyBindingHost host) {
        return new FrequencyBindingHelper(host);
    }

    @Override
    public void openBindingScreen(AbstractContainerMenu menu) {
        PacketDistributor.sendToServer(OpenFrequencyMenuPacket.forBlock());
    }

    private static FrequencySecurity toApiSecurity(FrequencySecurityLevel level) {
        return switch (level) {
            case PUBLIC -> FrequencySecurity.PUBLIC;
            case ENCRYPTED -> FrequencySecurity.ENCRYPTED;
            case PRIVATE -> FrequencySecurity.PRIVATE;
        };
    }
}
