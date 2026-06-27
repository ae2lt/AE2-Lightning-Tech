package com.moakiee.ae2lt.logic.craft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

public final class MatrixMultiblockTemplate {
    public static final int SIZE_X = 7;
    public static final int SIZE_Y = 11;
    public static final int SIZE_Z = 7;
    public static final BlockPos CONTROLLER_LOCAL = new BlockPos(0, 5, 3);
    public static final BlockPos CRAFTING_CENTER_LOCAL = new BlockPos(3, 5, 3);
    public static final int CRAFTING_SLOT_COUNT = 81;
    public static final int PATTERN_BAY_SLOT_COUNT = 50;
    public static final int PORT_CANDIDATE_COUNT = 3;

    private static final String[][] LAYERS = {
            {
                    "SSSSSSS",
                    "SSSSSSS",
                    "SSSSSSS",
                    "SSSSSSS",
                    "SSSSSSS",
                    "SSSSSSS",
                    "SSSSSSS"
            },
            {
                    "SPPPPPS",
                    "PTTTTTP",
                    "PTTTTTP",
                    "PTTTTTP",
                    "PTTTTTP",
                    "PTTTTTP",
                    "SPPPPPS"
            },
            {
                    "SSSSSSS",
                    "SPPPPPS",
                    "SPPPPPS",
                    "SPPPPPS",
                    "SPPPPPS",
                    "SPPPPPS",
                    "SSSSSSS"
            },
            {
                    "S.....S",
                    ".GGGGG.",
                    ".GHHHG.",
                    ".GHHHG.",
                    ".GHHHG.",
                    ".GGGGG.",
                    "S.....S"
            },
            {
                    "S.PPP.S",
                    ".GHHHG.",
                    "PHHHHHP",
                    "PHHHHHP",
                    "PHHHHHP",
                    ".GHHHG.",
                    "S.PPP.S"
            },
            {
                    "SPPIPPS",
                    "PGHHHGP",
                    "PHHHHHP",
                    "CHHHHHI",
                    "PHHHHHP",
                    "PGHHHGP",
                    "SPPIPPS"
            },
            {
                    "S.PPP.S",
                    ".GHHHG.",
                    "PHHHHHP",
                    "PHHHHHP",
                    "PHHHHHP",
                    ".GHHHG.",
                    "S.PPP.S"
            },
            {
                    "S.....S",
                    ".GGGGG.",
                    ".GHHHG.",
                    ".GHHHG.",
                    ".GHHHG.",
                    ".GGGGG.",
                    "S.....S"
            },
            {
                    "SSSSSSS",
                    "SPPPPPS",
                    "SPPPPPS",
                    "SPPPPPS",
                    "SPPPPPS",
                    "SPPPPPS",
                    "SSSSSSS"
            },
            {
                    "SPPPPPS",
                    "PTTTTTP",
                    "PTTTTTP",
                    "PTTTTTP",
                    "PTTTTTP",
                    "PTTTTTP",
                    "SPPPPPS"
            },
            {
                    "SSSSSSS",
                    "SSSSSSS",
                    "SSSSSSS",
                    "SSSSSSS",
                    "SSSSSSS",
                    "SSSSSSS",
                    "SSSSSSS"
            }
    };

    private static final List<Entry> ENTRIES = createEntries();

    private MatrixMultiblockTemplate() {
    }

    public static MatrixMultiblockRole roleAt(BlockPos localPos) {
        return roleAt(localPos.getX(), localPos.getY(), localPos.getZ());
    }

    public static MatrixMultiblockRole roleAt(int x, int y, int z) {
        if (x < 0 || x >= SIZE_X || y < 0 || y >= SIZE_Y || z < 0 || z >= SIZE_Z) {
            return MatrixMultiblockRole.EMPTY;
        }
        return MatrixMultiblockRole.fromTemplateKey(LAYERS[y][z].charAt(x));
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    private static List<Entry> createEntries() {
        var entries = new ArrayList<Entry>(SIZE_X * SIZE_Y * SIZE_Z);
        for (int y = 0; y < SIZE_Y; y++) {
            for (int z = 0; z < SIZE_Z; z++) {
                String row = LAYERS[y][z];
                if (row.length() != SIZE_X) {
                    throw new IllegalStateException("Invalid matrix template row at y=" + y + ", z=" + z);
                }
                for (int x = 0; x < SIZE_X; x++) {
                    entries.add(new Entry(new BlockPos(x, y, z), MatrixMultiblockRole.fromTemplateKey(row.charAt(x))));
                }
            }
        }
        return List.copyOf(entries);
    }

    public record Entry(BlockPos localPos, MatrixMultiblockRole role) {
    }
}
