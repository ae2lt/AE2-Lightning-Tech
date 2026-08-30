package com.moakiee.ae2lt.integration.ae2wtlib;

import java.util.Objects;
import java.util.function.IntFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.networking.IGridNode;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;

import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;

import de.mari_023.ae2wtlib.wut.WUTHandler;

/** Shared resolver for AE2WTLib terminals using an overloaded frequency card. */
public final class WirelessTerminalFrequencyLink {
    public enum RouteKind {
        NO_FREQUENCY,
        UNAVAILABLE,
        RESOLVED
    }

    /**
     * One lookup's result, not a cached connection. A resolved but unpowered node still takes
     * precedence over native routing. Power is checked only when the caller needs it, just as
     * before: actionable-node lookup itself must not probe power or require an active channel.
     */
    public record Resolution(RouteKind kind, @Nullable IGridNode node) {
        public Resolution {
            Objects.requireNonNull(kind, "kind");
            if ((kind == RouteKind.RESOLVED) != (node != null)) {
                throw new IllegalArgumentException("Only a resolved frequency route has a node");
            }
        }

        public boolean usesFrequencyRoute() {
            return kind == RouteKind.RESOLVED;
        }

        public boolean isNetworkPowered() {
            return WirelessTerminalFrequencyLink.isNetworkPowered(node);
        }
    }

    private static final Resolution NO_FREQUENCY = new Resolution(RouteKind.NO_FREQUENCY, null);
    private static final Resolution UNAVAILABLE = new Resolution(RouteKind.UNAVAILABLE, null);

    private WirelessTerminalFrequencyLink() {
    }

    /**
     * Compatibility entry point returning the node, or null when native routing should apply.
     */
    @Nullable
    public static IGridNode resolve(Player player, ItemStack terminalStack) {
        return resolveRoute(player, terminalStack).node();
    }

    /**
     * Preserves the pre-menu lookup's global slot capacity, also used by WTMenuHost's private
     * quantum-link inventory. This is not the same as ItemMenuHost.getUpgrades(): its visible
     * inventory has two slots for a standalone item, or two per installed WUT terminal.
     * Do not coerce the hosted overload to this capacity, or truncate this lookup to two slots.
     */
    public static Resolution resolveRoute(Player player, ItemStack terminalStack) {
        if (terminalStack.isEmpty()) {
            return NO_FREQUENCY;
        }
        return resolveRoute(player,
                UpgradeInventories.forItem(terminalStack, WUTHandler.getUpgradeCardCount()));
    }

    /** Resolves the advanced transmitter targeted by the first bound frequency card. */
    @Nullable
    public static IGridNode resolve(Player player, IUpgradeInventory upgrades) {
        return resolveRoute(player, upgrades).node();
    }

    /**
     * Uses the real acting player and the host's authoritative inventory. The nullable player
     * accepted by ItemWT.getLinkedGrid is only a message recipient and is not a substitute here.
     * Card ownership and auto-connect are deliberately not new runtime gates in this adapter.
     */
    public static Resolution resolveRoute(Player player, IUpgradeInventory upgrades) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return NO_FREQUENCY;
        }
        return resolveRoute(findBoundFrequency(upgrades), frequencyId -> {
            var manager = WirelessFrequencyManager.get();
            return manager == null
                    ? null
                    : manager.resolveAdvancedNode(frequencyId, serverLevel.getServer());
        });
    }

    /** The node resolver remains responsible for advanced-controller eligibility and live-node lookup. */
    static Resolution resolveRoute(int frequencyId, IntFunction<IGridNode> nodeResolver) {
        if (frequencyId <= 0) {
            return NO_FREQUENCY;
        }
        var node = nodeResolver.apply(frequencyId);
        return node == null ? UNAVAILABLE : new Resolution(RouteKind.RESOLVED, node);
    }

    /** A resolved frequency route is usable only while its target ME network is powered. */
    public static boolean isNetworkPowered(@Nullable IGridNode node) {
        return node != null && node.isPowered();
    }

    private static int findBoundFrequency(IUpgradeInventory upgrades) {
        for (int slot = 0; slot < upgrades.size(); slot++) {
            var stack = upgrades.getStackInSlot(slot);
            if (stack.getItem() instanceof OverloadedFrequencyCardItem) {
                var data = OverloadedFrequencyCardItem.getData(stack);
                if (data.isBound()) {
                    return data.frequencyId();
                }
            }
        }
        return -1;
    }
}
