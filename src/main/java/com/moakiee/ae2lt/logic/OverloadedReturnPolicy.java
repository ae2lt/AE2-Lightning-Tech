package com.moakiee.ae2lt.logic;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.thunderbolt.ae2.overload.model.MatchMode;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadPatternDetails;
import net.minecraft.core.RegistryAccess;

/** Output filtering and LOCK_UNTIL_RESULT matching derived from loaded patterns. */
final class OverloadedReturnPolicy {
    private static final String TAG_UNLOCK_MATCH_MODE = "Ae2ltUnlockMatchMode";
    private static final String TAG_UNLOCK_TEMPLATE = "Ae2ltUnlockTemplate";

    @Nullable
    private AllowedOutputFilter outputFilter;
    private boolean outputFilterDirty = true;
    @Nullable
    private MatchMode unlockMatchMode;
    @Nullable
    private ItemStack unlockTemplate;

    AllowedOutputFilter outputFilter(List<IPatternDetails> patterns) {
        if (!outputFilterDirty && outputFilter != null) {
            return outputFilter;
        }
        outputFilter = collectOutputFilter(patterns);
        outputFilterDirty = false;
        return outputFilter;
    }

    void patternsChanged() {
        outputFilter = null;
        outputFilterDirty = true;
    }

    void synchronizeUnlockRule(IPatternDetails pattern, boolean lockUntilResult) {
        clearUnlockRule();
        if (!lockUntilResult
                || !(pattern instanceof OverloadedProviderOnlyPatternDetails overload)) {
            return;
        }

        var details = overload.overloadPatternDetailsView();
        int outputIndex = resolveUnlockOutputIndex(pattern, details);
        if (outputIndex < 0 || outputIndex >= details.outputs().size()) {
            return;
        }
        var output = details.outputs().get(outputIndex);
        unlockMatchMode = output.matchMode();
        unlockTemplate = output.template();
    }

    boolean matchesUnlock(GenericStack unlockStack, GenericStack returnedStack) {
        if (unlockMatchMode != MatchMode.ID_ONLY) {
            return unlockStack.what().equals(returnedStack.what());
        }

        Item expectedItem = null;
        if (unlockTemplate != null && !unlockTemplate.isEmpty()) {
            expectedItem = unlockTemplate.getItem();
        } else if (unlockStack.what() instanceof AEItemKey unlockItemKey) {
            expectedItem = unlockItemKey.getItem();
        }
        return expectedItem != null
                && returnedStack.what() instanceof AEItemKey returnedItemKey
                && returnedItemKey.getItem() == expectedItem;
    }

    void clearUnlockRule() {
        unlockMatchMode = null;
        unlockTemplate = null;
    }

    void writeToNBT(CompoundTag tag, RegistryAccess registries) {
        if (unlockMatchMode != null) {
            tag.putString(TAG_UNLOCK_MATCH_MODE, unlockMatchMode.name());
        }
        if (unlockTemplate != null && !unlockTemplate.isEmpty()) {
            tag.put(TAG_UNLOCK_TEMPLATE, unlockTemplate.saveOptional(registries));
        }
    }

    void readFromNBT(CompoundTag tag, RegistryAccess registries) {
        clearUnlockRule();
        if (tag.contains(TAG_UNLOCK_MATCH_MODE, Tag.TAG_STRING)) {
            try {
                unlockMatchMode = MatchMode.valueOf(
                        tag.getString(TAG_UNLOCK_MATCH_MODE));
            } catch (IllegalArgumentException ignored) {
                unlockMatchMode = null;
            }
        }
        if (tag.contains(TAG_UNLOCK_TEMPLATE, Tag.TAG_COMPOUND)) {
            unlockTemplate = ItemStack.parseOptional(
                    registries, tag.getCompound(TAG_UNLOCK_TEMPLATE));
            if (unlockTemplate.isEmpty()) {
                unlockTemplate = null;
            }
        }
        patternsChanged();
    }

    private static AllowedOutputFilter collectOutputFilter(
            List<IPatternDetails> patterns) {
        var filter = new AllowedOutputFilter();
        for (var pattern : patterns) {
            if (pattern instanceof OverloadedProviderOnlyPatternDetails overload) {
                var ae2Outputs = pattern.getOutputs();
                var overloadOutputs = overload.overloadPatternDetailsView().outputs();
                int count = Math.min(ae2Outputs.size(), overloadOutputs.size());
                for (int i = 0; i < count; i++) {
                    var key = ae2Outputs.get(i).what();
                    if (overloadOutputs.get(i).matchMode() == MatchMode.ID_ONLY) {
                        filter.allowIdOnly(key);
                    } else {
                        filter.allowStrict(key);
                    }
                }
                continue;
            }
            for (var output : pattern.getOutputs()) {
                filter.allowStrict(output.what());
            }
        }
        return filter;
    }

    private static int resolveUnlockOutputIndex(
            IPatternDetails pattern, OverloadPatternDetails overloadDetails) {
        var actualOutputs = pattern.getOutputs();
        var overloadOutputs = overloadDetails.outputs();
        int count = Math.min(actualOutputs.size(), overloadOutputs.size());
        if (count <= 0) {
            return -1;
        }

        var primaryOutput = pattern.getPrimaryOutput();
        for (int i = 0; i < count; i++) {
            var candidate = actualOutputs.get(i);
            if (candidate.what().equals(primaryOutput.what())
                    && candidate.amount() == primaryOutput.amount()) {
                return i;
            }
        }
        for (int i = 0; i < count; i++) {
            if (overloadOutputs.get(i).primaryOutput()) {
                return i;
            }
        }
        return 0;
    }
}

