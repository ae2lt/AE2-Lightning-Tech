package com.moakiee.ae2lt.menu;

import appeng.api.config.CopyMode;
import appeng.api.config.Settings;
import appeng.api.inventories.ISegmentedInventory;
import appeng.blockentity.misc.CellWorkbenchBlockEntity;
import appeng.menu.AEBaseMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuHostLocator;
import com.moakiee.ae2lt.AE2LightningTech;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** A player's manual workstations survive menu replacements within the same terminal interaction. */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class TianshuWorkstationSession {
    private static final Map<ServerPlayer, Pending> PENDING = new IdentityHashMap<>();

    private TianshuWorkstationSession() {}

    static void retain(TianshuCraftingTermMenu menu, CellWorkbenchBlockEntity workbench) {
        var player = (ServerPlayer) menu.getPlayer();
        // A second replacement can happen before the new menu's first broadcast.
        restore(menu, workbench);
        finish(player);
        var config = workbench.getConfig().createMenuWrapper();
        var marks = new ArrayList<ItemStack>(config.size());
        for (int i = 0; i < config.size(); i++) marks.add(config.getStackInSlot(i).copy());
        var cells = workbench.getSubInventory(ISegmentedInventory.CELLS);
        var cell = cells.getStackInSlot(0);
        var copyMode = workbench.getConfigManager().getSetting(Settings.COPY_MODE);
        var locator = menu.getLocator();
        var anchor = locator instanceof ItemMenuHostLocator item ? item.locateItem(player) : ItemStack.EMPTY;
        var work = menu.takeWorkState();
        // Move ownership out of the removed menu before any inventory-return callback can run.
        cells.setItemDirect(0, ItemStack.EMPTY);
        PENDING.put(player, new Pending(player.level().dimension(), locator, anchor, cell, marks, copyMode, work));
        // Player.remove may close this menu after the logout event, with no later server tick.
        if (!player.isAlive() || player.hasDisconnected()) finish(player);
    }

    static void restore(TianshuCraftingTermMenu menu, CellWorkbenchBlockEntity workbench) {
        var player = (ServerPlayer) menu.getPlayer();
        var state = PENDING.get(player);
        if (state == null || !state.matches(player, menu)) return;
        PENDING.remove(player);
        var cells = workbench.getSubInventory(ISegmentedInventory.CELLS);
        if (!cells.getStackInSlot(0).isEmpty()) {
            returnStack(player, state.cell);
        } else {
            workbench.getConfigManager().putSetting(Settings.COPY_MODE, state.copyMode);
            var config = workbench.getConfig().createMenuWrapper();
            for (int i = 0; i < Math.min(config.size(), state.marks.size()); i++)
                config.setItemDirect(i, state.marks.get(i));
            cells.setItemDirect(0, state.cell);
        }
        menu.restoreWorkState(state.work);
    }

    // ServerPlayer closes the old menu before installing its replacement. Check after that transition,
    // not inside removed(), so CraftConfirmMenu and WUT terminal changes can keep the same workbench.
    @SubscribeEvent
    public static void afterServerTick(ServerTickEvent.Post event) {
        for (var player : List.copyOf(PENDING.keySet())) {
            if (player.getServer() != event.getServer()) continue;
            var state = PENDING.get(player);
            if (!player.isAlive() || player.hasDisconnected()
                    || !(player.containerMenu instanceof AEBaseMenu menu) || !state.matches(player, menu))
                finish(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) finish(player);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (var player : List.copyOf(PENDING.keySet()))
            if (player.getServer() == event.getServer()) finish(player);
    }

    private static void finish(ServerPlayer player) {
        var state = PENDING.remove(player);
        if (state != null) {
            returnStack(player, state.cell);
            for (var stack : state.work.inputs()) returnStack(player, stack);
        }
    }

    static void returnStack(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.isAlive() || player.hasDisconnected()) player.drop(stack, false);
        else player.getInventory().placeItemBackInInventory(stack);
    }

    private record Pending(ResourceKey<Level> dimension, MenuHostLocator locator, ItemStack anchor,
                           ItemStack cell, List<ItemStack> marks, CopyMode copyMode, TianshuCraftingTermMenu.WorkState work) {
        boolean matches(ServerPlayer player, AEBaseMenu menu) {
            return locator != null && dimension.equals(player.level().dimension()) && locator.equals(menu.getLocator())
                    && (!(locator instanceof ItemMenuHostLocator item) || item.locateItem(player) == anchor);
        }

    }
}
