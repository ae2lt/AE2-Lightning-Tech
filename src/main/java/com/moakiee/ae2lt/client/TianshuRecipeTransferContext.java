package com.moakiee.ae2lt.client;

import appeng.integration.modules.itemlists.EncodingHelper;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * One-screen recipe-viewer context carrying recipe/category identity into the provider picker.
 * Recipe transfer deliberately does not start encoding; callers keep the normal AE2 encode
 * lifecycle and this class remains a small metadata bridge that other integrations can reuse.
 *
 * <p>The priority scheme and JEI/EMI capture behavior are adapted from ExtendedAE Plus
 * [ClientPlus], revision 07f8373c590c0c6d845f794e7c25090e5ef5703e (GNU LGPL 3.0).
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

    /** Captures the portable recipe type/id keywords available to the JEI integration. */
    public static void captureVanillaRecipe(
            TianshuPatternEncodingTermMenu menu, Object recipeBase) {
        Recipe<?> recipe = switch (recipeBase) {
            case RecipeHolder<?> holder -> holder.value();
            case Recipe<?> direct -> direct;
            default -> null;
        };
        var queries = new TreeMap<Integer, Component>();
        String sourceKey = "";
        String recipeId = "";
        if (recipe != null) {
            var typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            sourceKey = typeId != null ? typeId.toString() : recipe.getType().toString();
            add(queries, 0, sourceKey);
        }
        if (recipeBase instanceof RecipeHolder<?> holder) {
            recipeId = holder.id().toString();
            add(queries, 1000, firstPathSegment(holder.id().getPath()));
        }
        publish(menu, sourceKey, recipeId, queries);
    }

    /** Publishes viewer-specific keywords without exposing optional viewer types to common code. */
    public static synchronized void publish(
            TianshuPatternEncodingTermMenu menu,
            String sourceKey,
            String recipeId,
            Map<Integer, Component> queries) {
        if (menu == null) return;
        var ordered = new TreeMap<Integer, Component>();
        if (queries != null) {
            queries.forEach((priority, value) -> {
                if (priority != null && value != null && !value.getString().isBlank()) {
                    ordered.putIfAbsent(priority, value.copy());
                }
            });
        }
        owner = new WeakReference<>(menu);
        snapshot = new Snapshot(
                sourceKey == null ? "" : sourceKey,
                recipeId == null ? "" : recipeId,
                Collections.unmodifiableMap(ordered));
    }

    public static synchronized Snapshot snapshotFor(TianshuPatternEncodingTermMenu menu) {
        return owner.get() == menu ? snapshot : Snapshot.EMPTY;
    }

    public static void add(Map<Integer, Component> queries, int priority, String value) {
        if (queries == null || value == null || value.isBlank()) return;
        queries.putIfAbsent(priority, Component.literal(value));
    }

    public static String firstPathSegment(String path) {
        if (path == null || path.isBlank()) return "";
        int slash = path.indexOf('/');
        return slash >= 0 ? path.substring(0, slash) : path;
    }

    public record Snapshot(String sourceKey, String recipeId, Map<Integer, Component> queries) {
        private static final Snapshot EMPTY = new Snapshot("", "", Map.of());

        public Snapshot {
            sourceKey = sourceKey == null ? "" : sourceKey;
            recipeId = recipeId == null ? "" : recipeId;
            queries = queries == null ? Map.of() : Map.copyOf(queries);
        }

        public boolean present() {
            return !queries.isEmpty();
        }
    }
}
