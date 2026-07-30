package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.LinkedHashSet;
import java.util.List;
import mezz.jei.api.gui.IRecipeLayoutDrawable;

/**
 * Carries the JEI category through its universal transfer call. JEI passes only the recipe display
 * object to transfer handlers, which is not necessarily a Minecraft {@code Recipe}.
 *
 * <p>This helper must remain outside every package declared as a Mixin package. Mixin package
 * classes are reserved for transformation and cannot be loaded directly at runtime.
 */
public final class JeiRecipeTransferMetadata {
    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private JeiRecipeTransferMetadata() {
    }

    public static void begin(
            TianshuPatternEncodingTermMenu menu, IRecipeLayoutDrawable<?> recipeLayout) {
        clear();
        if (menu == null || recipeLayout == null || recipeLayout.getRecipeCategory() == null) return;
        var category = recipeLayout.getRecipeCategory();
        var type = category.getRecipeType();
        String sourceKey = type == null || type.getUid() == null ? "" : type.getUid().toString();
        var aliases = new LinkedHashSet<String>();
        if (!sourceKey.isBlank()) aliases.add(sourceKey);
        if (category.getTitle() != null && !category.getTitle().getString().isBlank()) {
            aliases.add(category.getTitle().getString());
        }
        CURRENT.set(new Snapshot(menu, sourceKey, List.copyOf(aliases)));
    }

    public static Snapshot snapshotFor(TianshuPatternEncodingTermMenu menu) {
        var snapshot = CURRENT.get();
        return snapshot != null && snapshot.menu() == menu ? snapshot : Snapshot.EMPTY;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record Snapshot(
            TianshuPatternEncodingTermMenu menu, String sourceKey, List<String> defaultAliases) {
        private static final Snapshot EMPTY = new Snapshot(null, "", List.of());
    }
}
