package com.moakiee.ae2lt.grid.wirelesslink;

import java.util.ArrayList;
import java.util.List;

public final class WirelessLinkSideProbeOrder {
    private static final List<String> ALL_SIDES = List.of("down", "up", "north", "south", "west", "east");

    private WirelessLinkSideProbeOrder() {
    }

    public static List<String> forPreferredSide(String preferredSideName) {
        if (preferredSideName == null || preferredSideName.isBlank()) {
            return ALL_SIDES;
        }

        var result = new ArrayList<String>(ALL_SIDES.size());
        if (ALL_SIDES.contains(preferredSideName)) {
            result.add(preferredSideName);
        }
        for (var side : ALL_SIDES) {
            if (!side.equals(preferredSideName)) {
                result.add(side);
            }
        }
        return result;
    }
}
