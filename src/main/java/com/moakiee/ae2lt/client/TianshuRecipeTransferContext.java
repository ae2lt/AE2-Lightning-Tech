package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * One-screen recipe-viewer context carrying recipe/category identity into the provider picker.
 * This class does not start encoding itself; the optional Alt shortcut starts it only after the
 * viewer confirms a successful transfer, while this remains a reusable metadata bridge.
 *
 * <p>The JEI/EMI capture behavior is adapted from ExtendedAE Plus [ClientPlus], revision
 * 07f8373c590c0c6d845f794e7c25090e5ef5703e (GNU LGPL 3.0). Unlike ClientPlus, this bridge does
 * not expose multiple switchable search conditions: the picker has one source field and one
 * alias field. Viewer keywords are retained only as default values that can be copied into the
 * alias field.
 */
public final class TianshuRecipeTransferContext {
    private static final int ENCODING_RESULT_GRACE_TICKS = 5;
    private static WeakReference<TianshuPatternEncodingTermMenu> owner = new WeakReference<>(null);
    private static Snapshot snapshot = Snapshot.EMPTY;
    private static ItemStack boundEncodedPattern = ItemStack.EMPTY;
    private static ItemStack encodingSourcePattern = ItemStack.EMPTY;
    private static ItemStack pendingEncodedPattern = ItemStack.EMPTY;
    private static boolean encodingPending;
    private static boolean encodingAckReceived;
    private static int encodingBindingDeadline;

    private TianshuRecipeTransferContext() {
    }

    /** Captures the stable recipe type and exact recipe ID available to recipe viewers. */
    public static void captureVanillaRecipe(
            TianshuPatternEncodingTermMenu menu, Object recipeBase) {
        captureVanillaRecipe(menu, recipeBase, List.of());
    }

    /**
     * Captures the stable vanilla recipe identity while retaining viewer-specific aliases.
     * The vanilla recipe type remains authoritative whenever a real recipe is available.
     */
    public static void captureVanillaRecipe(
            TianshuPatternEncodingTermMenu menu,
            Object recipeBase,
            Iterable<String> additionalAliases) {
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
            sourceKey = typeId == null ? "" : typeId.toString();
            addDefaultAlias(defaultAliases, sourceKey);
        }
        if (recipeBase instanceof RecipeHolder<?> holder) {
            recipeId = holder.id().toString();
            addDefaultAlias(defaultAliases, firstPathSegment(holder.id().getPath()));
        }
        if (additionalAliases != null) {
            additionalAliases.forEach(value -> addDefaultAlias(defaultAliases, value));
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
        boundEncodedPattern = ItemStack.EMPTY;
        resetPendingEncoding();
    }

    public static synchronized Snapshot snapshotFor(TianshuPatternEncodingTermMenu menu) {
        return owner.get() == menu ? snapshot : Snapshot.EMPTY;
    }

    public static synchronized void clear(TianshuPatternEncodingTermMenu menu) {
        if (menu == null) return;
        owner = new WeakReference<>(menu);
        snapshot = Snapshot.EMPTY;
        boundEncodedPattern = ItemStack.EMPTY;
        resetPendingEncoding();
    }

    /**
     * Marks an encode request before it is sent to the server. The encoded slot and GuiSync
     * acknowledgement are synchronized independently, so either one may reach the client first.
     */
    public static synchronized void beginEncoding(
            TianshuPatternEncodingTermMenu menu, ItemStack currentPattern) {
        if (owner.get() != menu || snapshot.sourceKey().isBlank()) {
            resetPendingEncoding();
            return;
        }
        encodingSourcePattern = copyOrEmpty(currentPattern);
        pendingEncodedPattern = ItemStack.EMPTY;
        encodingPending = true;
        encodingAckReceived = false;
        encodingBindingDeadline = menu.getPlayer().tickCount + 40;
    }

    /**
     * Binds newly transferred viewer metadata to its first encoded pattern. Re-encoding may retain
     * that metadata only for the exact same stack; changing the draft must not carry an old recipe
     * ID into a different pattern.
     */
    public static synchronized void acceptEncodedPattern(
            TianshuPatternEncodingTermMenu menu, ItemStack encodedPattern) {
        if (owner.get() != menu || snapshot.sourceKey().isBlank()) return;
        if (encodingPending) {
            if (!encodingAckReceived) {
                encodingAckReceived = true;
                encodingBindingDeadline =
                        menu.getPlayer().tickCount + ENCODING_RESULT_GRACE_TICKS;
            }
            if (isNewEncodingResult(encodedPattern)) {
                bindEncodedPattern(encodedPattern);
            } else if (!pendingEncodedPattern.isEmpty()) {
                bindEncodedPattern(pendingEncodedPattern);
            }
            return;
        }
        if (encodedPattern == null || encodedPattern.isEmpty()) {
            snapshot = Snapshot.EMPTY;
            boundEncodedPattern = ItemStack.EMPTY;
            return;
        }
        if (boundEncodedPattern.isEmpty()) {
            boundEncodedPattern = encodedPattern.copy();
            return;
        }
        // Compare the complete stack instead of a hash: data-component or count differences are
        // meaningful for encoded patterns, and a collision must never revive stale recipe data.
        if (!ItemStack.matches(boundEncodedPattern, encodedPattern)) {
            snapshot = Snapshot.EMPTY;
            boundEncodedPattern = ItemStack.EMPTY;
        }
    }

    /**
     * Keeps an already bound context while its exact pattern is temporarily removed or reinserted.
     * An empty slot is only a dormant state; inserting any different pattern invalidates the
     * context.
     */
    public static synchronized boolean retainAfterEncodedSlotChange(
            TianshuPatternEncodingTermMenu menu, ItemStack current) {
        // Removing a pattern never proves that its recipe metadata is stale. Keep the context
        // dormant until another non-empty pattern provides something meaningful to compare.
        if (current == null || current.isEmpty()) return true;
        finishExpiredEncoding(menu, current);
        if (encodingPending && owner.get() == menu && !snapshot.sourceKey().isBlank()) {
            if (isNewEncodingResult(current)) {
                pendingEncodedPattern = current.copy();
                if (encodingAckReceived) bindEncodedPattern(current);
            }
            return true;
        }
        if (owner.get() != menu
                || snapshot.sourceKey().isBlank()
                || boundEncodedPattern.isEmpty()) {
            return false;
        }
        return ItemStack.matches(boundEncodedPattern, current);
    }

    /**
     * Returns whether the current encoded stack is safe to use for a triggered upload. A short
     * grace period is needed only when the acknowledgement arrived before the slot update.
     */
    public static synchronized boolean isEncodingResultReady(
            TianshuPatternEncodingTermMenu menu, ItemStack current) {
        finishExpiredEncoding(menu, current);
        if (!encodingPending || owner.get() != menu || snapshot.sourceKey().isBlank()) {
            return true;
        }
        if (isNewEncodingResult(current)) {
            pendingEncodedPattern = current.copy();
            if (encodingAckReceived) bindEncodedPattern(current);
        }
        return !encodingPending;
    }

    private static void finishExpiredEncoding(
            TianshuPatternEncodingTermMenu menu, ItemStack current) {
        if (!encodingPending || owner.get() != menu
                || menu.getPlayer().tickCount <= encodingBindingDeadline) {
            return;
        }
        if (encodingAckReceived) {
            if (!pendingEncodedPattern.isEmpty()) {
                bindEncodedPattern(pendingEncodedPattern);
            } else if (current != null && !current.isEmpty()) {
                bindEncodedPattern(current);
            } else if (!encodingSourcePattern.isEmpty()) {
                bindEncodedPattern(encodingSourcePattern);
            } else {
                resetPendingEncoding();
            }
        } else {
            // The server did not acknowledge a valid encoding result. Preserve the captured
            // recipe metadata for a retry, but do not bind it to an unconfirmed slot change.
            resetPendingEncoding();
        }
    }

    private static boolean isNewEncodingResult(ItemStack pattern) {
        return pattern != null
                && !pattern.isEmpty()
                && (encodingSourcePattern.isEmpty()
                        || !ItemStack.matches(encodingSourcePattern, pattern));
    }

    private static void bindEncodedPattern(ItemStack pattern) {
        boundEncodedPattern = pattern.copy();
        resetPendingEncoding();
    }

    private static ItemStack copyOrEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    private static void resetPendingEncoding() {
        encodingSourcePattern = ItemStack.EMPTY;
        pendingEncodedPattern = ItemStack.EMPTY;
        encodingPending = false;
        encodingAckReceived = false;
        encodingBindingDeadline = 0;
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
