package com.moakiee.ae2lt.client;

import appeng.integration.modules.itemlists.EncodingHelper;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * One-screen recipe-viewer context carrying recipe/category identity into the provider picker.
 * Recipe transfer deliberately does not start encoding; callers keep the normal AE2 encode
 * lifecycle and this class remains a small metadata bridge that other integrations can reuse.
 *
 * <p>The JEI/EMI capture behavior is adapted from ExtendedAE Plus [ClientPlus], revision
 * 07f8373c590c0c6d845f794e7c25090e5ef5703e (GNU LGPL 3.0). Unlike ClientPlus, this bridge does
 * not expose multiple switchable search conditions: the picker has one source field and one
 * alias field. Viewer keywords are retained only as default values that can be copied into the
 * alias field.
 */
public final class TianshuRecipeTransferContext {
    private static WeakReference<TianshuPatternEncodingTermMenu> owner = new WeakReference<>(null);
    private static Snapshot snapshot = Snapshot.EMPTY;

    private TianshuRecipeTransferContext() {
    }

    public static boolean isSupportedCraftingRecipe(Object recipeBase) {
        return recipeBase instanceof RecipeHolder<?> holder
                && EncodingHelper.isSupportedCraftingRecipe(holder.value());
    }

    /** Captures the stable recipe type and exact recipe ID available to the JEI integration. */
    public static void captureVanillaRecipe(
            TianshuPatternEncodingTermMenu menu, Object recipeBase) {
        Recipe<?> recipe = switch (recipeBase) {
            case RecipeHolder<?> holder -> holder.value();
            case Recipe<?> direct -> direct;
            default -> null;
        };
        String sourceKey = "";
        String recipeId = "";
        var defaultAliases = new ArrayList<String>();
        if (recipe != null) {
            var typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            sourceKey = typeId != null ? typeId.toString() : recipe.getType().toString();
            addDefaultAlias(defaultAliases, sourceKey);
        }
        if (recipeBase instanceof RecipeHolder<?> holder) {
            recipeId = holder.id().toString();
            addDefaultAlias(defaultAliases, firstPathSegment(holder.id().getPath()));
        }
        publish(menu, sourceKey, recipeId, defaultAliases);
    }

    /** Publishes viewer metadata without exposing optional viewer types to common code. */
    public static synchronized void publish(
            TianshuPatternEncodingTermMenu menu,
            String sourceKey,
            String recipeId,
            Iterable<String> defaultAliases) {
        if (menu == null) return;
        var aliases = new LinkedHashSet<String>();
        if (defaultAliases != null) {
            defaultAliases.forEach(value -> {
                if (value != null && !value.isBlank()) aliases.add(value);
            });
        }
        owner = new WeakReference<>(menu);
        snapshot = new Snapshot(
                sourceKey == null ? "" : sourceKey,
                recipeId == null ? "" : recipeId,
                List.copyOf(aliases));
    }

    public static synchronized Snapshot snapshotFor(TianshuPatternEncodingTermMenu menu) {
        return owner.get() == menu ? snapshot : Snapshot.EMPTY;
    }

    public static void addDefaultAlias(List<String> aliases, String value) {
        if (aliases != null && value != null && !value.isBlank() && !aliases.contains(value)) {
            aliases.add(value);
        }
    }

    public static String firstPathSegment(String path) {
        if (path == null || path.isBlank()) return "";
        int slash = path.indexOf('/');
        return slash >= 0 ? path.substring(0, slash) : path;
    }

    public record Snapshot(String sourceKey, String recipeId, List<String> defaultAliases) {
        private static final Snapshot EMPTY = new Snapshot("", "", List.of());

        public Snapshot {
            sourceKey = sourceKey == null ? "" : sourceKey;
            recipeId = recipeId == null ? "" : recipeId;
            defaultAliases = defaultAliases == null ? List.of() : List.copyOf(defaultAliases);
        }
    }
}
