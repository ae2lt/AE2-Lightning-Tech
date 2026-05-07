package com.moakiee.ae2lt.overload.pattern;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;

import com.moakiee.ae2lt.item.OverloadPatternItem;

/**
 * Decoder that exposes overload patterns to AE2's crafting system.
 */
public final class OverloadPatternDecoder implements IPatternDetailsDecoder {
    public static final OverloadPatternDecoder INSTANCE = new OverloadPatternDecoder();

    private OverloadPatternDecoder() {
    }

    @Override
    public boolean isEncodedPattern(ItemStack stack) {
        return stack.getItem() instanceof OverloadPatternItem overloadPatternItem
                && overloadPatternItem.hasPayload(stack);
    }

    @Override
    public @Nullable IPatternDetails decodePattern(AEItemKey what, Level level) {
        if (what == null || !(what.getItem() instanceof OverloadPatternItem overloadPatternItem)) {
            return null;
        }

        var payload = overloadPatternItem.readPayload(what.toStack()).orElse(null);
        if (payload == null || payload.requiredHostKind() != PatternExecutionHostKind.OVERLOADED_PATTERN_PROVIDER) {
            return null;
        }

        var sourceStack = payload.sourcePattern().toItemStack(level.registryAccess());
        var sourceDetails = PatternDetailsHelper.decodePattern(sourceStack, level);
        if (sourceDetails == null || sourceDetails instanceof OverloadedProviderOnlyPatternDetails) {
            return null;
        }

        var parsed = OverloadPatternSupport.toParsedDefinition(sourceStack, sourceDetails, level.registryAccess());
        var overloadDetails = new OverloadPatternDetails(parsed, payload.encodedPattern());
        return new Ae2OverloadPatternDetails(what, overloadDetails, sourceDetails);
    }
}
