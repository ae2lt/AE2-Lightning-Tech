package com.moakiee.ae2lt.client;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Builds the initial provider-filter selection shared by the visible picker and direct upload.
 */
final class TianshuUploadSourceSelection {
    private TianshuUploadSourceSelection() {
    }

    static Selection collect(TianshuPatternEncodingTermMenu menu) {
        var recipeContext = TianshuRecipeTransferContext.snapshotFor(menu);
        if (!recipeContext.sourceKey().isBlank()) {
            return new Selection(
                    recipeContext.sourceKey(), recipeContext.defaultAliases(), true);
        }

        ItemStack stack = menu.getSlots(SlotSemantics.ENCODED_PATTERN).stream()
                .map(slot -> slot.getItem())
                .filter(item -> !item.isEmpty())
                .findFirst()
                .orElse(ItemStack.EMPTY);
        var player = Minecraft.getInstance().player;
        if (stack.isEmpty() || player == null) return Selection.EMPTY;
        var details = PatternDetailsHelper.decodePattern(stack, player.level());
        var output = details == null ? null : details.getPrimaryOutput();
        if (output == null || output.what() == null) return Selection.EMPTY;
        var key = output.what();
        return new Selection(
                key.getId().toString(),
                List.of(key.getDisplayName().getString(), key.getModId()),
                false);
    }

    record Selection(
            String sourceKey, List<String> defaultAliases, boolean selectFirstDefault) {
        private static final Selection EMPTY = new Selection("", List.of(), false);

        Selection {
            sourceKey = sourceKey == null ? "" : sourceKey;
            defaultAliases = defaultAliases == null ? List.of() : List.copyOf(defaultAliases);
        }

        String savedAlias() {
            String alias = AE2LTClientConfig.findUploadAlias(sourceKey);
            return alias == null ? "" : alias.strip();
        }

        String initialQuery() {
            String saved = savedAlias();
            if (!saved.isBlank()) return saved;
            return selectFirstDefault && !defaultAliases.isEmpty()
                    ? defaultAliases.getFirst()
                    : "";
        }
    }
}
