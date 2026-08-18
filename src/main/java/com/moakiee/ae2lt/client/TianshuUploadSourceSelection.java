package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.List;

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
        // An encoded pattern does not retain its originating recipe type. Do not present its
        // primary output ID/name as recipe metadata: a manually inserted pattern must leave both
        // the source and alias fields empty instead of publishing a plausible but false identity.
        return Selection.EMPTY;
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
                    ? defaultAliases.get(0)
                    : "";
        }
    }
}
