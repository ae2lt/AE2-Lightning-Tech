package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.me.helpers.PlayerSource;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWirelessIngredientSource;
import com.moakiee.ae2lt.network.jei.WirelessJeiSupplyPacket;
import com.moakiee.ae2lt.network.jei.WirelessJeiSupplyResultPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Supplies real backpack items; the machine's existing JEI handler still performs the transfer. */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class WirelessJeiSupply {
    private record Ticket(AbstractContainerMenu menu, ItemMenuHostLocator locator, IGrid grid,
                          int expires, Map<AEItemKey, Integer> offered) {}
    private static final Map<UUID, LinkedHashMap<Integer, Ticket>> TICKETS = new HashMap<>();
    private WirelessJeiSupply() {}

    public static WirelessJeiSupplyResultPacket handle(ServerPlayer player, WirelessJeiSupplyPacket packet) {
        var menu = player.containerMenu;
        if (!player.isAlive() || menu == player.inventoryMenu || menu instanceof MEStorageMenu
                || menu.containerId != packet.containerId() || !menu.stillValid(player)
                || packet.items().isEmpty() || packet.items().size() > WirelessJeiInventoryPlan.MAX_ENTRIES) {
            return result(packet, 0, List.of());
        }
        return packet.take() ? take(player, packet) : offer(player, packet);
    }

    private static WirelessJeiSupplyResultPacket offer(ServerPlayer player, WirelessJeiSupplyPacket packet) {
        var wanted = new HashMap<Item, Integer>();
        for (var stack : packet.items()) {
            if (stack.isEmpty() || stack.getCount() > WirelessJeiInventoryPlan.MAX_COUNT) return result(packet, 0, List.of());
            wanted.merge(stack.getItem(), stack.getCount(), (a, b) -> Math.min(WirelessJeiInventoryPlan.MAX_COUNT, a + b));
        }
        for (var locator : TianshuWirelessIngredientSource.locate(player)) {
            var host = TianshuWirelessIngredientSource.open(player, locator);
            if (host == null) continue;
            var grid = host.getActionableNode().getGrid();
            var source = new PlayerSource(player, host);
            var offered = new LinkedHashMap<AEItemKey, Integer>();
            for (var entry : grid.getStorageService().getCachedInventory()) {
                if (!(entry.getKey() instanceof AEItemKey key)) continue;
                int amount = wanted.getOrDefault(key.getItem(), 0);
                if (amount == 0) continue;
                // Listing alone does not grant extraction: the mounted storage sees the real player source.
                int available = (int) StorageHelper.poweredExtraction(host, host.getInventory(), key,
                        Math.min(amount, Math.max(0L, entry.getLongValue())), source, Actionable.SIMULATE);
                if (available > 0) offered.put(key, available);
                if (offered.size() >= WirelessJeiInventoryPlan.MAX_ENTRIES) break;
            }
            var tickets = TICKETS.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashMap<>());
            int now = player.server.getTickCount();
            tickets.values().removeIf(ticket -> ticket.expires() < now || ticket.menu() != player.containerMenu);
            while (tickets.size() >= 32) tickets.remove(tickets.keySet().iterator().next());
            tickets.put(packet.requestId(), new Ticket(player.containerMenu, locator, grid, now + 100, Map.copyOf(offered)));
            return result(packet, 1, offered.entrySet().stream().map(e -> e.getKey().toStack(e.getValue())).toList());
        }
        return result(packet, 0, List.of());
    }

    private static WirelessJeiSupplyResultPacket take(ServerPlayer player, WirelessJeiSupplyPacket packet) {
        var tickets = TICKETS.get(player.getUUID());
        var ticket = tickets == null ? null : tickets.remove(packet.requestId());
        if (ticket == null || ticket.menu() != player.containerMenu || ticket.expires() < player.server.getTickCount()) {
            return result(packet, 0, List.of());
        }
        var host = TianshuWirelessIngredientSource.open(player, ticket.locator());
        if (host == null || host.getActionableNode().getGrid() != ticket.grid()) return result(packet, 0, List.of());
        var requested = new LinkedHashMap<AEItemKey, Integer>();
        for (var stack : packet.items()) {
            var key = AEItemKey.of(stack);
            if (key == null || stack.getCount() > WirelessJeiInventoryPlan.MAX_COUNT) return result(packet, 0, List.of());
            int total = requested.getOrDefault(key, 0) + stack.getCount();
            if (total > ticket.offered().getOrDefault(key, 0)) return result(packet, 0, List.of());
            requested.put(key, total);
        }
        var stacks = requested.entrySet().stream().map(e -> e.getKey().toStack(e.getValue())).toList();
        var before = WirelessJeiInventoryPlan.copy(player.getInventory().items);
        var after = WirelessJeiInventoryPlan.insert(before, stacks);
        if (after == null) return result(packet, 0, List.of());
        var source = new PlayerSource(player, host);
        var storage = host.getInventory();
        double power = 0;
        for (var entry : requested.entrySet()) {
            if (StorageHelper.poweredExtraction(host, storage, entry.getKey(), entry.getValue(), source,
                    Actionable.SIMULATE) != entry.getValue()) return result(packet, 0, List.of());
            power += (double) entry.getValue() / Math.max(1, entry.getKey().getAmountPerOperation());
        }
        if (host.extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG) + 0.0001 < power
                || !host.consumeIdlePower(Actionable.MODULATE)) return result(packet, 0, List.of());
        if (host.extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG) + 0.0001 < power)
            return result(packet, 0, List.of());
        var taken = new ArrayList<ItemStack>();
        boolean committed = false;
        try {
            for (var entry : requested.entrySet()) {
                int amount = (int) StorageHelper.poweredExtraction(host, storage, entry.getKey(), entry.getValue(), source);
                if (amount > 0) taken.add(entry.getKey().toStack(amount));
                if (amount != entry.getValue()) return result(packet, 0, List.of());
            }
            // Recheck after storage callbacks; never overwrite items changed by another integration.
            for (int i = 0; i < 36; i++) {
                if (!ItemStack.matches(before.get(i), after.get(i))
                        && !ItemStack.matches(before.get(i), player.getInventory().getItem(i))) return result(packet, 0, List.of());
            }
            for (int i = 0; i < 36; i++) {
                if (!ItemStack.matches(before.get(i), after.get(i))) player.getInventory().setItem(i, after.get(i));
            }
            player.getInventory().setChanged();
            committed = true;
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
            return result(packet, 2, List.of());
        } finally {
            if (!committed) {
                // Return already-owned items without charging again. If storage changed, preserve them on the player.
                for (var stack : taken) {
                    long inserted = storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, source);
                    stack.shrink((int) inserted);
                    if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
                }
                player.containerMenu.broadcastChanges();
                player.inventoryMenu.broadcastChanges();
            }
        }
    }

    private static WirelessJeiSupplyResultPacket result(WirelessJeiSupplyPacket packet, int status, List<ItemStack> items) {
        return new WirelessJeiSupplyResultPacket(packet.containerId(), packet.requestId(), status, items);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { TICKETS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { TICKETS.clear(); }
}
