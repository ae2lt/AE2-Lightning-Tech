package com.moakiee.ae2lt.logic.tianshu;

import net.minecraft.core.BlockPos;

/** Fixed 7x7x7 role template converted from the reference _.nbt structure. */
public final class TianshuMultiblockTemplate {
    public static final int SIZE = 7;
    public static final BlockPos CONTROLLER = new BlockPos(6, 0, 3);
    public static final BlockPos LOWER_PORT = new BlockPos(3, 0, 3);
    public static final BlockPos UPPER_PORT = new BlockPos(3, 6, 3);

    public static TianshuMultiblockRole roleAt(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (!inside(x) || !inside(y) || !inside(z)) {
            return TianshuMultiblockRole.IGNORED;
        }
        if (pos.equals(CONTROLLER)) {
            return TianshuMultiblockRole.CONTROLLER;
        }
        if (pos.equals(LOWER_PORT) || pos.equals(UPPER_PORT)) {
            return TianshuMultiblockRole.PORT_CANDIDATE;
        }
        if (betweenTwoAndFour(x) && betweenTwoAndFour(y) && betweenTwoAndFour(z)) {
            return TianshuMultiblockRole.CORE_RESERVED;
        }
        if (betweenOneAndFive(x) && betweenOneAndFive(y) && betweenOneAndFive(z)
                && (x == 1 || x == 5 || y == 1 || y == 5 || z == 1 || z == 5)) {
            return TianshuMultiblockRole.GLASS;
        }
        if ((y == 0 || y == 6) && betweenTwoAndFour(x) && betweenTwoAndFour(z)) {
            return TianshuMultiblockRole.COOLING;
        }
        int boundaryCount = boundary(x) + boundary(y) + boundary(z);
        if (boundaryCount >= 2) {
            return TianshuMultiblockRole.CASING;
        }
        if (y == 0 || y == 6) {
            return TianshuMultiblockRole.CASING;
        }
        return TianshuMultiblockRole.IGNORED;
    }

    private static int boundary(int value) {
        return value == 0 || value == 6 ? 1 : 0;
    }

    private static boolean inside(int value) {
        return value >= 0 && value < SIZE;
    }

    private static boolean betweenOneAndFive(int value) {
        return value >= 1 && value <= 5;
    }

    private static boolean betweenTwoAndFour(int value) {
        return value >= 2 && value <= 4;
    }

    private TianshuMultiblockTemplate() {
    }
}
