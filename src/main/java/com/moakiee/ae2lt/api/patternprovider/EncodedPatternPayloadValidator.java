package com.moakiee.ae2lt.api.patternprovider;

import net.minecraft.world.item.ItemStack;

/**
 * Optional contract for encoded-pattern items whose item identity alone does
 * not prove that a usable payload is present.
 */
@FunctionalInterface
public interface EncodedPatternPayloadValidator {
    boolean hasEncodedPatternPayload(ItemStack stack);
}
