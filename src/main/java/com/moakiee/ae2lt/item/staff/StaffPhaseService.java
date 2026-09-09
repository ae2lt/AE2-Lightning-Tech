package com.moakiee.ae2lt.item.staff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.IdentityHashMap;
import java.util.function.Supplier;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class StaffPhaseService {
    private StaffPhaseService() {}
    private record UseCopies(ServerPlayer player, Map<ItemStack, StaffPhaseVault.Entry> copies) {}
    private static final ThreadLocal<UseCopies> USE_COPIES = new ThreadLocal<>();

    public static <T> T withNativeCopies(ServerPlayer player, Supplier<T> action) {
        var previous = USE_COPIES.get();
        USE_COPIES.set(new UseCopies(player, new IdentityHashMap<>()));
        try { return action.get(); }
        finally { if (previous == null) USE_COPIES.remove(); else USE_COPIES.set(previous); }
    }

    public static ItemStack registerNativeCopy(ItemStack source, ItemStack copy) {
        var scope = USE_COPIES.get();
        if (scope != null && isProjection(source) && mayUse(scope.player(), source)) {
            var entry = StaffPhaseVault.get(scope.player().server).find(source.get(ModDataComponents.MIMICRY_PROJECTION_LINK.get()));
            if (entry != null && entry.active == source) scope.copies().put(copy, entry);
        }
        return copy;
    }
    public static boolean isProjection(ItemStack stack) { return stack.getItem() instanceof StaffProjectionItem; }
    public static boolean clientView() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null || !server.isSameThread();
    }
    public static StaffProjectionView view(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.MIMICRY_PROJECTION_VIEW.get(), StaffProjectionView.EMPTY);
    }
    static ItemStack raw(Inventory inventory, int slot) {
        return slot >= 0 && slot < 36 ? inventory.items.get(slot) : slot == 40 ? inventory.offhand.getFirst() : ItemStack.EMPTY;
    }

    /** Only the exact active object in its owning player's reserved slot resolves on the server. */
    public static ItemStack resolve(ItemStack stack) {
        if (!isProjection(stack)) return stack;
        if (clientView()) return ItemStack.EMPTY;
        var vault = StaffPhaseVault.get(ServerLifecycleHooks.getCurrentServer());
        var entry = vault.find(stack.get(ModDataComponents.MIMICRY_PROJECTION_LINK.get()));
        if (entry == null || entry.holder == null || raw(entry.holder.getInventory(), entry.slot) != entry.active) return ItemStack.EMPTY;
        var scope = USE_COPIES.get();
        if (entry.active != stack && (scope == null || scope.player() != entry.holder || scope.copies().get(stack) != entry)) return ItemStack.EMPTY;
        vault.setDirty();
        return entry.original;
    }

    public static boolean validAt(Player player, int slot, ItemStack stack) {
        if (!isProjection(stack)) return true;
        var link = stack.get(ModDataComponents.MIMICRY_PROJECTION_LINK.get());
        if (link == null || !player.getUUID().equals(link.owner()) || slot != link.slot()) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return true;
        var entry = StaffPhaseVault.get(serverPlayer.server).find(link);
        return entry != null && (entry.active.isEmpty() || entry.active == stack)
                && (entry.holder == null || entry.holder == serverPlayer);
    }

    public static boolean mayUse(Player player, ItemStack stack) {
        if (!isProjection(stack)) return true;
        var link = stack.get(ModDataComponents.MIMICRY_PROJECTION_LINK.get());
        return player != null && link != null && validAt(player, link.slot(), stack)
                && raw(player.getInventory(), link.slot()) == stack;
    }

    public static boolean toggle(ServerPlayer player, ItemStack stack) {
        if (isProjection(stack)) return unlock(player, stack);
        // Accept the earlier plain transfer-lock component as an unlockable migration state.
        if (PhaseItemProtection.isLockedStaff(stack)) {
            stack.remove(ModDataComponents.MIMICRY_PHASE_LOCK.get());
            return true;
        }
        return lock(player, stack);
    }

    public static boolean lock(ServerPlayer player, ItemStack original) {
        if (!(original.getItem() instanceof MimicryStaffItem) || isProjection(original) || original.getCount() != 1) return false;
        int slot = -1;
        for (int i = 0; i < 41; i++) if (raw(player.getInventory(), i) == original) { slot = i; break; }
        if (slot < 0 || player.containerMenu.getCarried() == original) return false;
        var vault = StaffPhaseVault.get(player.server);
        var entries = vault.entries.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        if (entries.containsKey(slot)) return false;
        var entry = new StaffPhaseVault.Entry(player.getUUID(), UUID.randomUUID(), UUID.randomUUID(), slot, original);
        // Transfer ownership before constructing any publicly visible handle.
        player.getInventory().setItem(slot, ItemStack.EMPTY);
        original.remove(ModDataComponents.MIMICRY_PHASE_LOCK.get());
        entries.put(slot, entry);
        installProjection(player, entry, false);
        vault.setDirty();
        return true;
    }

    public static boolean unlock(ServerPlayer player, ItemStack projection) {
        var vault = StaffPhaseVault.get(player.server);
        var entry = vault.find(projection.get(ModDataComponents.MIMICRY_PROJECTION_LINK.get()));
        if (entry == null || entry.holder != player || entry.active != projection || raw(player.getInventory(), entry.slot) != projection) return false;
        var entries = vault.entries.get(player.getUUID());
        entries.remove(entry.slot); // Invalidate every old token before releasing the original.
        if (entries.isEmpty()) vault.entries.remove(player.getUUID());
        player.getInventory().setItem(entry.slot, ItemStack.EMPTY);
        projection.setCount(0);
        entry.original.remove(ModDataComponents.MIMICRY_PHASE_LOCK.get());
        player.getInventory().setItem(entry.slot, entry.original);
        player.getInventory().setChanged();
        syncSlot(player, entry.slot, entry.original);
        vault.setDirty();
        return true;
    }

    private static void installProjection(ServerPlayer player, StaffPhaseVault.Entry entry, boolean regenerate) {
        if (regenerate) entry.generation = UUID.randomUUID();
        var projection = new ItemStack(ModItems.MIMICRY_STAFF_PROJECTION.get());
        projection.set(ModDataComponents.MIMICRY_PROJECTION_LINK.get(), entry.link());
        entry.holder = player;
        entry.active = projection;
        sync(entry);
        player.getInventory().setItem(entry.slot, projection);
        player.getInventory().setChanged();
        syncSlot(player, entry.slot, projection);
    }

    private static void syncSlot(ServerPlayer player, int slot, ItemStack stack) {
        // DeviceHubMenu has no inventory slots. Its normal status sync cannot update a replaced
        // held item, so send the actual player-inventory slot even while the hub remains open.
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, stack));
    }

    private static boolean sync(StaffPhaseVault.Entry entry) {
        var view = StaffProjectionView.of(entry.original);
        boolean changed = !view.equals(view(entry.active));
        if (changed) entry.active.set(ModDataComponents.MIMICRY_PROJECTION_VIEW.get(), view);
        var name = entry.original.get(DataComponents.CUSTOM_NAME);
        changed |= !java.util.Objects.equals(name, entry.active.get(DataComponents.CUSTOM_NAME));
        if (name == null) entry.active.remove(DataComponents.CUSTOM_NAME);
        else entry.active.set(DataComponents.CUSTOM_NAME, name);
        return changed;
    }

    public static void tick(ServerPlayer player) {
        var vault = StaffPhaseVault.get(player.server);
        // Raw reads avoid exposing or resolving invalid tokens while normal Inventory.getItem is guarded.
        for (int i = 0; i < 41; i++) {
            var stack = raw(player.getInventory(), i);
            if (isProjection(stack) && !validAt(player, i, stack)) player.getInventory().setItem(i, ItemStack.EMPTY);
            else if (PhaseItemProtection.isLockedStaff(stack) && !isProjection(stack)) lock(player, stack);
        }
        var entries = vault.entries.getOrDefault(player.getUUID(), Map.of());
        for (var entry : List.copyOf(entries.values())) {
            entry.holder = player;
            var inSlot = raw(player.getInventory(), entry.slot);
            if (isProjection(inSlot) && entry.link().equals(inSlot.get(ModDataComponents.MIMICRY_PROJECTION_LINK.get()))
                    && (entry.active.isEmpty() || entry.active == inSlot)) {
                entry.active = inSlot; // Rebind the saved handle once after loading the player.
                if (sync(entry)) syncSlot(player, entry.slot, entry.active);
                continue;
            }
            if (!inSlot.isEmpty()) {
                // Preserve unrelated occupants. Wait for room if another protected item occupies the slot.
                if (PhaseItemProtection.isProtected(inSlot)) continue;
                int free = player.getInventory().getFreeSlot();
                if (free < 0) continue;
                player.getInventory().setItem(entry.slot, ItemStack.EMPTY);
                player.getInventory().setItem(free, inSlot);
            }
            installProjection(player, entry, true);
            vault.setDirty();
        }
    }

    public static void prepareRespawn(ServerPlayer oldPlayer, ServerPlayer replacement) {
        var vault = StaffPhaseVault.get(replacement.server);
        for (var entry : vault.entries.getOrDefault(oldPlayer.getUUID(), Map.of()).values()) {
            entry.generation = UUID.randomUUID();
            entry.active = ItemStack.EMPTY;
            entry.holder = replacement;
            var old = raw(oldPlayer.getInventory(), entry.slot);
            if (isProjection(old)) oldPlayer.getInventory().setItem(entry.slot, ItemStack.EMPTY);
        }
        vault.setDirty();
    }

    @SubscribeEvent public static void beforeTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) tick(player);
    }
    @SubscribeEvent public static void afterTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) tick(player);
    }
    @SubscribeEvent public static void swapHands(LivingSwapItemsEvent.Hands event) {
        if (PhaseItemProtection.isProtected(event.getItemSwappedToMainHand()) || PhaseItemProtection.isProtected(event.getItemSwappedToOffHand())) event.setCanceled(true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (var entry : StaffPhaseVault.get(player.server).entries.getOrDefault(player.getUUID(), Map.of()).values()) {
            entry.holder = null;
            entry.active = ItemStack.EMPTY;
        }
    }
}
