package com.moakiee.ae2lt.client;

import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWirelessIngredientSource;
import com.moakiee.ae2lt.logic.tianshu.terminal.WirelessJeiInventoryPlan;
import com.moakiee.ae2lt.network.jei.WirelessJeiSupplyPacket;
import com.moakiee.ae2lt.network.jei.WirelessJeiSupplyResultPacket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.common.Internal;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Adds a real-inventory refill before the existing handler, without registering any new machine handlers. */
public final class JeiWirelessSupplyClient {
    @FunctionalInterface
    public interface NativeTransfer {
        @Nullable IRecipeTransferError transfer(boolean maximum, boolean doTransfer);
    }
    private record Key(Object recipe, Object handler) {}
    private static final class Offer {
        final int id;
        final long requestedAt = Util.getMillis();
        List<ItemStack> items;
        long fingerprint = Long.MIN_VALUE;
        List<ItemStack> normalPlan = List.of();
        Offer(int id) { this.id = id; }
    }
    private record Pending(IRecipeTransferHandler<AbstractContainerMenu, Object> handler,
                           AbstractContainerMenu menu, Object recipe, IRecipeSlotsView slots,
                           Player player, boolean maximum, Screen screen, Offer offer, boolean taking,
                           NativeTransfer nativeTransfer) {}
    private static final Map<Key, Offer> OFFERS = new LinkedHashMap<>();
    private static AbstractContainerMenu currentMenu;
    private static Pending pending;
    private static int sequence;
    private JeiWirelessSupplyClient() {}

    @Nullable
    public static IRecipeTransferError transfer(IRecipeTransferHandler<AbstractContainerMenu, Object> handler,
            AbstractContainerMenu menu, Object recipe, IRecipeSlotsView slots, Player player,
            boolean maximum, boolean doTransfer, NativeTransfer nativeTransfer) {
        if (!AE2LTClientConfig.jeiWirelessSupply() || menu instanceof MEStorageMenu || menu == player.inventoryMenu
                || menu != player.containerMenu || TianshuWirelessIngredientSource.locate(player).isEmpty()) {
            return nativeTransfer.transfer(maximum, doTransfer);
        }
        var nativeError = nativeTransfer.transfer(maximum, false);
        // A missing handler/canHandle rejection remains authoritative, including custom handlers.
        if (nativeError != null && nativeError.getType() == IRecipeTransferError.Type.INTERNAL) return nativeError;
        if (allows(nativeError) && !maximum && !Screen.hasShiftDown()) {
            return doTransfer ? nativeTransfer.transfer(false, true) : nativeError;
        }
        if (currentMenu != menu) { clear(); currentMenu = menu; }
        if (pending != null && Util.getMillis() - pending.offer().requestedAt > 5000) pending = null;
        if (pending != null && doTransfer) return hint(false, "waiting");

        var key = new Key(recipe, handler);
        var offer = OFFERS.get(key);
        if (offer == null || Util.getMillis() - offer.requestedAt > (offer.items == null ? 3000 : 1000)) {
            var wanted = queryItems(slots);
            if (wanted.isEmpty()) return doTransfer && allows(nativeError)
                    ? nativeTransfer.transfer(maximum, true) : nativeError;
            offer = new Offer(++sequence);
            while (OFFERS.size() >= 32) OFFERS.remove(OFFERS.keySet().iterator().next());
            OFFERS.put(key, offer);
            PacketDistributor.sendToServer(new WirelessJeiSupplyPacket(menu.containerId, offer.id, false, wanted));
        }
        var operation = new Pending(handler, menu, recipe, slots, player, maximum,
                Minecraft.getInstance().screen, offer, false, nativeTransfer);
        if (offer.items == null) {
            if (doTransfer) { pending = operation; return hint(false, "waiting"); }
            return nativeError;
        }
        var plan = plan(operation);
        if (plan.isEmpty()) return doTransfer && allows(nativeError)
                ? nativeTransfer.transfer(maximum, true) : nativeError;
        if (doTransfer) beginTake(operation, plan);
        return hint(!doTransfer, doTransfer ? "waiting" : "available");
    }

    /** Requests only item types occurring in this recipe, retaining exact returned components for JEI to compare. */
    private static List<ItemStack> queryItems(IRecipeSlotsView slots) {
        var wanted = new LinkedHashMap<Item, Integer>();
        for (var slot : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
            var alternatives = slot.getItemStacks().map(ItemStack::getItem).distinct().toList();
            for (var item : alternatives) wanted.merge(item, 64, (a, b) -> Math.min(WirelessJeiInventoryPlan.MAX_COUNT, a + b));
            if (wanted.size() > WirelessJeiInventoryPlan.MAX_ENTRIES) return List.of();
        }
        return wanted.entrySet().stream().map(e -> new ItemStack(e.getKey(), e.getValue())).toList();
    }

    private static List<ItemStack> plan(Pending operation) {
        var offer = operation.offer();
        long fingerprint = operation.menu().getStateId();
        for (var slot : operation.menu().slots) {
            var stack = slot.getItem();
            fingerprint = fingerprint * 31 + ItemStack.hashItemAndComponents(stack) * 31L + stack.getCount();
        }
        if (offer.fingerprint != fingerprint) {
            offer.fingerprint = fingerprint;
            offer.normalPlan = buildNormalPlan(operation);
        }
        if (!operation.maximum()) return WirelessJeiInventoryPlan.copy(offer.normalPlan);
        return expandPlan(operation, offer.normalPlan);
    }

    private static List<ItemStack> buildNormalPlan(Pending operation) {
        if (allows(preview(operation, List.of()))) return List.of();
        // Pick a usable alternative for each input first. Adding all tag variants can fill the
        // last empty inventory slots before a different required ingredient has a chance to fit.
        var result = new ArrayList<ItemStack>();
        var inputs = new ArrayList<>(operation.slots().getSlotViews(RecipeIngredientRole.INPUT));
        inputs.sort(java.util.Comparator.comparingLong(input -> input.getItemStacks().count()));
        var existing = operation.menu().slots.stream()
                .filter(s -> !s.isFake() && s.mayPickup(operation.player()) && s.mayPlace(s.getItem()))
                .map(s -> s.getItem().copy()).toList();
        for (var input : inputs) {
            int required = input.getItemStacks().mapToInt(ItemStack::getCount).max().orElse(0);
            for (var stack : existing) {
                if (stack.isEmpty() || input.getItemStacks().noneMatch(s -> equivalent(s, stack))) continue;
                int used = Math.min(required, stack.getCount());
                stack.shrink(used); required -= used;
                if (required == 0) break;
            }
            if (required == 0) continue;
            var candidates = operation.offer().items.stream()
                    .filter(offer -> input.getItemStacks().anyMatch(s -> equivalent(s, offer)))
                    .sorted(java.util.Comparator.<ItemStack>comparingInt(offer ->
                            result.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, offer)) ? 0
                            : operation.player().getInventory().items.stream()
                                    .anyMatch(s -> ItemStack.isSameItemSameComponents(s, offer)) ? 1 : 2)
                            .thenComparing(java.util.Comparator.comparingInt(ItemStack::getCount).reversed())).toList();
            for (var offered : candidates) {
                int needed = Math.min(required, input.getItemStacks().filter(s -> equivalent(s, offered))
                        .mapToInt(ItemStack::getCount).max().orElse(0));
                int planned = result.stream().filter(s -> ItemStack.isSameItemSameComponents(s, offered)).mapToInt(ItemStack::getCount).sum();
                int previous = result.size();
                addFitting(operation.player(), result, offered, Math.min(needed, offered.getCount() - planned));
                if (result.size() > previous) break;
            }
        }
        if (!allows(preview(operation, result))) {
            // A custom handler may require a different combination; retain the broader native
            // preview path when all relevant alternatives can fit.
            result.clear();
            addAllAlternatives(operation, result);
        }
        if (!allows(preview(operation, result))) return List.of();
        return minimize(operation, result);
    }

    private static void addAllAlternatives(Pending operation, List<ItemStack> result) {
        for (var offered : operation.offer().items) {
            int needed = 0;
            for (var input : operation.slots().getSlotViews(RecipeIngredientRole.INPUT)) {
                int count = input.getItemStacks().filter(s -> equivalent(s, offered)).mapToInt(ItemStack::getCount).max().orElse(0);
                needed = Math.min(WirelessJeiInventoryPlan.MAX_COUNT, needed + count);
            }
            int amount = Math.min(needed, offered.getCount());
            if (amount > 0) addFitting(operation.player(), result, offered, amount);
        }
    }

    private static List<ItemStack> minimize(Pending operation, List<ItemStack> result) {
        // Let the existing handler identify the actual deficit, including items already in machine slots.
        for (int i = result.size() - 1; i >= 0; i--) {
            var stack = result.get(i);
            int low = 0, high = stack.getCount();
            while (low < high) {
                int middle = (low + high) >>> 1;
                var candidate = WirelessJeiInventoryPlan.copy(result);
                if (middle == 0) candidate.remove(i); else candidate.set(i, stack.copyWithCount(middle));
                if (allows(preview(operation, candidate))) high = middle; else low = middle + 1;
            }
            if (low == 0) result.remove(i); else result.set(i, stack.copyWithCount(low));
        }
        return result;
    }

    private static List<ItemStack> expandPlan(Pending operation, List<ItemStack> minimum) {
        var targets = new LinkedHashMap<appeng.api.stacks.AEItemKey, Integer>();
        for (var input : operation.slots().getSlotViews(RecipeIngredientRole.INPUT)) {
            var candidates = operation.offer().items.stream()
                    .filter(offer -> input.getItemStacks().anyMatch(s -> equivalent(s, offer)))
                    .sorted(java.util.Comparator.comparingInt(offer -> {
                        if (minimum.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s, offer))) return 0;
                        if (operation.menu().slots.stream().anyMatch(s -> ItemStack.isSameItemSameComponents(s.getItem(), offer))) return 1;
                        return 2;
                    })).toList();
            if (!candidates.isEmpty()) {
                var chosen = candidates.getFirst();
                targets.merge(appeng.api.stacks.AEItemKey.of(chosen), Math.min(64, chosen.getMaxStackSize()), Integer::sum);
            }
        }
        var result = WirelessJeiInventoryPlan.copy(minimum);
        for (var entry : targets.entrySet()) {
            var stack = entry.getKey().toStack();
            int present = operation.menu().slots.stream().filter(s -> !s.isFake())
                    .map(s -> s.getItem()).filter(s -> ItemStack.isSameItemSameComponents(s, stack)).mapToInt(ItemStack::getCount).sum();
            int planned = result.stream().filter(s -> ItemStack.isSameItemSameComponents(s, stack)).mapToInt(ItemStack::getCount).sum();
            int available = operation.offer().items.stream().filter(s -> ItemStack.isSameItemSameComponents(s, stack))
                    .mapToInt(ItemStack::getCount).sum();
            int additional = Math.min(entry.getValue() - present - planned, available - planned);
            if (additional > 0) addFitting(operation.player(), result, stack, additional);
        }
        return allows(preview(operation, result)) ? result : minimum;
    }

    private static void addFitting(Player player, List<ItemStack> plan, ItemStack stack, int limit) {
        int low = 0, high = limit;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            var candidate = WirelessJeiInventoryPlan.copy(plan);
            candidate.add(stack.copyWithCount(middle));
            if (WirelessJeiInventoryPlan.insert(player.getInventory().items, candidate) != null) low = middle;
            else high = middle - 1;
        }
        if (low > 0) plan.add(stack.copyWithCount(low));
    }

    private static boolean equivalent(ItemStack a, ItemStack b) {
        return Internal.getJeiRuntime().getJeiHelpers().getStackHelper().isEquivalent(a, b, UidContext.Recipe);
    }

    @Nullable
    private static IRecipeTransferError preview(Pending operation, List<ItemStack> materials) {
        var inventory = operation.player().getInventory().items;
        var simulated = WirelessJeiInventoryPlan.insert(inventory, materials);
        if (simulated == null) return hint(false, "no_space");
        var original = new ArrayList<>(inventory);
        try {
            for (int i = 0; i < 36; i++) inventory.set(i, simulated.get(i));
            return operation.nativeTransfer().transfer(operation.maximum(), false);
        } finally {
            for (int i = 0; i < 36; i++) inventory.set(i, original.get(i));
        }
    }

    private static void beginTake(Pending operation, List<ItemStack> plan) {
        pending = new Pending(operation.handler(), operation.menu(), operation.recipe(), operation.slots(),
                operation.player(), operation.maximum(), operation.screen(), operation.offer(), true, operation.nativeTransfer());
        PacketDistributor.sendToServer(new WirelessJeiSupplyPacket(operation.menu().containerId, operation.offer().id, true, plan));
    }

    public static void receive(WirelessJeiSupplyResultPacket packet) {
        try {
            applyResult(packet);
        } catch (RuntimeException error) {
            com.mojang.logging.LogUtils.getLogger().warn("Existing JEI handler failed after wireless refill", error);
            clear();
            var player = Minecraft.getInstance().player;
            if (player != null) player.displayClientMessage(Component.translatable("ae2lt.tianshu.jei_supply.retry"), true);
        }
    }

    private static void applyResult(WirelessJeiSupplyResultPacket packet) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.containerMenu != currentMenu
                || currentMenu.containerId != packet.containerId() || !AE2LTClientConfig.jeiWirelessSupply()) { clear(); return; }
        for (var offer : OFFERS.values()) {
            if (offer.id == packet.requestId()) { offer.items = WirelessJeiInventoryPlan.copy(packet.items()); offer.fingerprint = Long.MIN_VALUE; }
        }
        var operation = pending;
        if (operation == null || operation.offer().id != packet.requestId()) return;
        pending = null;
        if (minecraft.screen != operation.screen()) return;
        if (!operation.taking() && packet.status() == 1) {
            var materials = plan(operation);
            if (!materials.isEmpty()) { beginTake(operation, materials); return; }
        } else if (operation.taking() && packet.status() == 2) {
            var error = operation.nativeTransfer().transfer(operation.maximum(), false);
            if (allows(error)) {
                error = operation.nativeTransfer().transfer(operation.maximum(), true);
                if (allows(error) && minecraft.screen instanceof RecipesGui gui) gui.onClose();
                clear();
                return;
            }
        }
        OFFERS.clear();
        minecraft.player.displayClientMessage(Component.translatable("ae2lt.tianshu.jei_supply.retry"), true);
    }

    public static void clear() { OFFERS.clear(); pending = null; currentMenu = null; }
    private static boolean allows(@Nullable IRecipeTransferError error) { return error == null || error.getType().allowsTransfer; }
    private static IRecipeTransferError hint(boolean allowed, String message) {
        return new IRecipeTransferError() {
            @Override public Type getType() { return allowed ? Type.COSMETIC : Type.USER_FACING; }
            @Override public void getTooltip(ITooltipBuilder tooltip) { tooltip.add(Component.translatable("ae2lt.tianshu.jei_supply." + message)); }
        };
    }
}
