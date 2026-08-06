package com.moakiee.ae2lt.mixin;

import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes AE2's side-effect-free pattern candidate builder so duplicate interception can happen
 * before the encoded slot or blank-pattern inventory is mutated.
 */
@Mixin(PatternEncodingTermMenu.class)
public interface PatternEncodingTermMenuAccessor {
    @Invoker("encodePattern")
    ItemStack ae2lt$encodePatternCandidate();
}
