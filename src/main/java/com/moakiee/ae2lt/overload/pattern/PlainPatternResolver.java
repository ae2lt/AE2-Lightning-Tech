package com.moakiee.ae2lt.overload.pattern;

import net.minecraft.world.item.ItemStack;

/**
 * Boundary adapter that reparses a stored plain pattern stack back into a
 * host-neutral {@link ParsedPatternDefinition}.
 * <p>
 * The concrete AE2/ExtendedAE decoding path is intentionally deferred.
 */
@FunctionalInterface
public interface PlainPatternResolver {
    ParsedPatternDefinition resolve(ItemStack sourcePatternStack);
}
