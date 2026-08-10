package com.moakiee.ae2lt.me.cell;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import appeng.api.config.CondenserOutput;
import appeng.core.definitions.AEItems;

/**
 * Output modes supported by the ME Void Cell.
 *
 * <p>Adapted from ExtendedAE's 1.21.1 {@code VoidMode} under LGPL-3.0;
 * see {@code THIRD_PARTY_NOTICES.md}.</p>
 */
public enum VoidCellMode implements StringRepresentable {
    TRASH(Items.AIR, CondenserOutput.TRASH),
    MATTER_BALLS(AEItems.MATTER_BALL, CondenserOutput.MATTER_BALLS),
    SINGULARITY(AEItems.SINGULARITY, CondenserOutput.SINGULARITY);

    private final ItemLike output;
    private final CondenserOutput condenserOutput;

    VoidCellMode(ItemLike output, CondenserOutput condenserOutput) {
        this.output = output;
        this.condenserOutput = condenserOutput;
    }

    public ItemLike getOutput() {
        return output;
    }

    public int getRequiredPower() {
        return condenserOutput.requiredPower;
    }

    @Override
    public String getSerializedName() {
        return name();
    }

    public static VoidCellMode fromSerializedName(String name) {
        if (name == null || name.isBlank()) {
            return TRASH;
        }
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TRASH;
        }
    }

    public static VoidCellMode fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : TRASH;
    }
}
