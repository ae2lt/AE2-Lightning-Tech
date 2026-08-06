package com.moakiee.ae2lt.api.frequency;

import java.util.Optional;
import java.util.OptionalInt;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Public, frozen entry point for the AE2LT wireless frequency system.
 *
 * <p>Server-side query methods return empty / {@code false} when the provider
 * has not yet been initialised (i.e. before AE2LT's common setup completes).
 * Mutating methods throw {@link IllegalStateException} in that window.
 */
public final class FrequencyApi {
    private static volatile FrequencyApiProvider provider;

    private FrequencyApi() {}

    /** Frequency id bound to the given BE, or empty if it is not a frequency host. Server-thread only. */
    public static OptionalInt getBoundFrequencyId(BlockEntity blockEntity) {
        var p = provider;
        return p == null ? OptionalInt.empty() : p.getBoundFrequencyId(blockEntity);
    }

    /** Look up a frequency's metadata by id. Server-thread only. */
    public static Optional<FrequencyInfo> getFrequencyInfo(MinecraftServer server, int frequencyId) {
        var p = provider;
        return p == null ? Optional.empty() : p.getFrequencyInfo(server, frequencyId);
    }

    /** Look up the transmitter (dimension + position) bound to a frequency. Server-thread only. */
    public static Optional<TransmitterInfo> getTransmitter(MinecraftServer server, int frequencyId) {
        var p = provider;
        return p == null ? Optional.empty() : p.getTransmitter(server, frequencyId);
    }

    /** True if {@code frequencyId} is a currently registered frequency. Server-thread only. */
    public static boolean isValidFrequency(MinecraftServer server, int frequencyId) {
        var p = provider;
        return p != null && p.isValidFrequency(server, frequencyId);
    }

    /**
     * Create a binding access for the given host BE. The host's BE must
     * extend {@code appeng.blockentity.grid.AENetworkedBlockEntity}.
     */
    public static FrequencyBindingAccess createBinding(FrequencyBindingHost host) {
        var p = provider;
        if (p == null) throw new IllegalStateException("FrequencyApi provider not yet initialised");
        return p.createBinding(host);
    }

    /**
     * Client-side helper: open the shared frequency binding screen. The menu
     * must implement {@link FrequencyBindingMenuHost}. Typically invoked from
     * a button's {@code onPress} callback inside a custom {@code Screen}.
     */
    public static void openBindingScreen(AbstractContainerMenu menu) {
        if (!(menu instanceof FrequencyBindingMenuHost)) {
            throw new IllegalArgumentException(
                    "Menu " + menu.getClass().getName() + " does not implement FrequencyBindingMenuHost");
        }
        var p = provider;
        if (p == null) throw new IllegalStateException("FrequencyApi provider not yet initialised");
        p.openBindingScreen(menu);
    }

    @ApiStatus.Internal
    public static void setProvider(FrequencyApiProvider newProvider) {
        if (provider != null) {
            throw new IllegalStateException("FrequencyApi provider already set");
        }
        provider = newProvider;
    }
}
