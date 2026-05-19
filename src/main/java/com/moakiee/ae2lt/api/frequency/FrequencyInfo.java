package com.moakiee.ae2lt.api.frequency;

import java.util.UUID;

/**
 * Immutable snapshot of a frequency's public metadata.
 *
 * @param id       Stable integer id ({@code > 0}). {@code -1} represents unbound elsewhere but is never returned here.
 * @param name     Display name, up to 24 characters.
 * @param color    Packed 0xRRGGBB color.
 * @param owner    UUID of the player who created the frequency.
 * @param security Access level required to use the frequency.
 */
public record FrequencyInfo(int id, String name, int color, UUID owner, FrequencySecurity security) {
}
