package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Simple ordered registry of {@link MachineAdapter} singletons.
 * <p>
 * On each dispatch attempt the registry is scanned front-to-back;
 * the first adapter whose {@code supports()} returns {@code true} is used.
 * Adapters registered later are checked <em>first</em> (stack order),
 * so third-party / specialised adapters naturally override the built-in fallback.
 * <p>
 * Not thread-safe — all registration must happen during mod init (common setup).
 */
public final class MachineAdapterRegistry {

    private static final List<MachineAdapter> ADAPTERS = new ArrayList<>();

    private MachineAdapterRegistry() {}

    /**
     * Register an adapter.  Newer registrations are checked before older ones,
     * so a specialised adapter added later will shadow the generic fallback.
     */
    public static void register(MachineAdapter adapter) {
        ADAPTERS.add(0, adapter);
    }

    /**
     * Find the first adapter that recognises the block at {@code (level, pos)}.
     *
     * @return the matching adapter, or {@code null} if no adapter supports this target
     */
    @Nullable
    public static MachineAdapter find(ServerLevel level, BlockPos pos) {
        for (var adapter : ADAPTERS) {
            if (adapter.supports(level, pos)) {
                return adapter;
            }
        }
        return null;
    }

    /** Read-only view for debugging / introspection. */
    public static List<MachineAdapter> getAll() {
        return Collections.unmodifiableList(ADAPTERS);
    }

    /**
     * Called once during mod common-setup to register built-in adapters.
     * The AE2-native adapter is always registered last (= lowest priority).
     */
    public static void init() {
        register(AE2NativeMachineAdapter.INSTANCE);
    }
}
