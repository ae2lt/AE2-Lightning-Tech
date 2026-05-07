package com.moakiee.ae2lt.overload.pattern;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.PatternDetailsHelper;

/**
 * Converts a normal AE2 pattern item into the host-neutral parsed definition
 * used by overload conversion and later editing.
 */
public final class Ae2PlainPatternResolver implements PlainPatternResolver {
    private final Level level;

    public Ae2PlainPatternResolver(Level level) {
        this.level = level;
    }

    @Override
    public ParsedPatternDefinition resolve(ItemStack sourcePatternStack) {
        var sourceDetails = PatternDetailsHelper.decodePattern(sourcePatternStack, level);
        if (sourceDetails == null) {
            throw new IllegalArgumentException("could not decode source pattern stack: " + sourcePatternStack);
        }
        return OverloadPatternSupport.toParsedDefinition(sourcePatternStack, sourceDetails, level.registryAccess());
    }
}
