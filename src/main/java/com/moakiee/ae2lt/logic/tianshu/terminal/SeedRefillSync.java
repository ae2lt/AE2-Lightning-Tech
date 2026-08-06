package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.stacks.AEKey;
import appeng.menu.guisync.PacketWritable;
import com.moakiee.ae2lt.logic.tianshu.loop.TianshuSeedRefillService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Outcome of the last manual closed-loop seed refill, shown in the terminal status area. */
public record SeedRefillSync(int state, List<Entry> missing) implements PacketWritable {
    public static final int STATE_NONE = 0;
    public static final int STATE_COMPLETE = 1;
    public static final int STATE_MISSING = 2;
    public static final int STATE_UNAVAILABLE = 3;

    private static final int MAX_MISSING_ENTRIES = 16;
    private static final SeedRefillSync NONE = new SeedRefillSync(STATE_NONE, List.of());

    public record Entry(AEKey what, long amount) {
    }

    public SeedRefillSync {
        missing = List.copyOf(missing);
        if (state < STATE_NONE || state > STATE_UNAVAILABLE) {
            throw new IllegalArgumentException("invalid seed refill state: " + state);
        }
    }

    public SeedRefillSync(RegistryFriendlyByteBuf data) {
        this(data.readVarInt(), readMissing(data));
    }

    public static SeedRefillSync none() {
        return NONE;
    }

    public static SeedRefillSync of(TianshuSeedRefillService.RefillResult result) {
        if (!result.available()) return new SeedRefillSync(STATE_UNAVAILABLE, List.of());
        if (result.missing().isEmpty()) return new SeedRefillSync(STATE_COMPLETE, List.of());
        var entries = new ArrayList<Entry>();
        for (var entry : result.missing().entrySet()) {
            if (entries.size() >= MAX_MISSING_ENTRIES) break;
            entries.add(new Entry(entry.getKey(), entry.getValue()));
        }
        return new SeedRefillSync(STATE_MISSING, entries);
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        data.writeVarInt(state);
        data.writeVarInt(missing.size());
        for (var entry : missing) {
            AEKey.writeKey(data, entry.what());
            data.writeVarLong(entry.amount());
        }
    }

    private static List<Entry> readMissing(RegistryFriendlyByteBuf data) {
        int size = data.readVarInt();
        var result = new ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) {
            result.add(new Entry(AEKey.readKey(data), data.readVarLong()));
        }
        return result;
    }
}
