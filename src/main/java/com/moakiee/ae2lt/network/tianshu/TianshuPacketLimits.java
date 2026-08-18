package com.moakiee.ae2lt.network.tianshu;

import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceLimits;
import io.netty.handler.codec.DecoderException;

/** Shared hard limits for terminal-maintenance payloads and UI-created state. */
public final class TianshuPacketLimits {
    public static final int MAX_LIST_ENTRIES = InventoryMaintenanceLimits.MAX_ENTRIES;

    private TianshuPacketLimits() {
    }

    public static int requireListSize(String field, int size) {
        if (size < 0 || size > MAX_LIST_ENTRIES) {
            throw new IllegalArgumentException(
                    "Invalid " + field + " count " + size + " (maximum "
                            + MAX_LIST_ENTRIES + ')');
        }
        return size;
    }

    public static int requireDecodedListSize(String field, int size) {
        if (size < 0 || size > MAX_LIST_ENTRIES) {
            throw new DecoderException(
                    "Invalid " + field + " count " + size + " (maximum "
                            + MAX_LIST_ENTRIES + ')');
        }
        return size;
    }
}
