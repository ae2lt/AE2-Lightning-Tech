package com.moakiee.ae2lt.logic;

public final class FirmamentDustGenerationRules {
    public static final String END_DIMENSION_ID = "minecraft:the_end";
    public static final String UP_SIDE_ID = "up";
    public static final int INTERVAL_TICKS = 200;

    private FirmamentDustGenerationRules() {
    }

    public static boolean shouldGenerate(String dimensionId, String sideId, int hostY, int maxBuildHeight) {
        return END_DIMENSION_ID.equals(dimensionId)
                && UP_SIDE_ID.equals(sideId)
                && hostY + 1 >= maxBuildHeight;
    }
}
