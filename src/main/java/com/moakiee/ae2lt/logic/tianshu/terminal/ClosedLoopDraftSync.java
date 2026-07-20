package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.menu.guisync.PacketWritable;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Quantities and output marks that are not representable by an ItemStack slot alone. */
public record ClosedLoopDraftSync(
        List<Long> memberCopies,
        List<Integer> outputRoles) implements PacketWritable {
    public static final int MEMBER_SLOTS = 27;
    public static final int OUTPUT_SLOTS = 9;

    public ClosedLoopDraftSync {
        memberCopies = List.copyOf(memberCopies);
        outputRoles = List.copyOf(outputRoles);
        if (memberCopies.size() != MEMBER_SLOTS || outputRoles.size() != OUTPUT_SLOTS) {
            throw new IllegalArgumentException("invalid closed-loop draft sync dimensions");
        }
        for (long copies : memberCopies) {
            if (copies < 0L) throw new IllegalArgumentException("negative member copies");
        }
        for (int role : outputRoles) {
            if (role < 0 || role > 2) throw new IllegalArgumentException("invalid output role");
        }
    }

    public ClosedLoopDraftSync(RegistryFriendlyByteBuf data) {
        this(readCopies(data), readRoles(data));
    }

    public static ClosedLoopDraftSync empty() {
        return new ClosedLoopDraftSync(
                Collections.nCopies(MEMBER_SLOTS, 0L),
                Collections.nCopies(OUTPUT_SLOTS, 0));
    }

    public long copies(int slot) {
        return slot >= 0 && slot < memberCopies.size() ? memberCopies.get(slot) : 0L;
    }

    public int outputRole(int slot) {
        return slot >= 0 && slot < outputRoles.size() ? outputRoles.get(slot) : 0;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        for (long copies : memberCopies) data.writeVarLong(copies);
        for (int role : outputRoles) data.writeVarInt(role);
    }

    private static List<Long> readCopies(RegistryFriendlyByteBuf data) {
        var result = new java.util.ArrayList<Long>(MEMBER_SLOTS);
        for (int i = 0; i < MEMBER_SLOTS; i++) result.add(data.readVarLong());
        return result;
    }

    private static List<Integer> readRoles(RegistryFriendlyByteBuf data) {
        var result = new java.util.ArrayList<Integer>(OUTPUT_SLOTS);
        for (int i = 0; i < OUTPUT_SLOTS; i++) result.add(data.readVarInt());
        return result;
    }
}
