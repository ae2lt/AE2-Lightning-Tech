package com.moakiee.ae2lt.integration.ae2wtlib;

import com.moakiee.ae2lt.registry.ModItems;

import de.mari_023.ae2wtlib.api.registration.UpgradeHelper;
import de.mari_023.ae2wtlib.api.registration.AddTerminalEvent;
import de.mari_023.ae2wtlib.api.gui.Icon;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import appeng.client.gui.style.StyleManager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;

/**
 * Soft-optional ae2wtlib integration entry point.
 *
 * <p>This class references ae2wtlib API types directly, so it must only be
 * class-loaded when ae2wtlib is present. Callers gate access behind
 * {@code ModList.get().isLoaded("ae2wtlib")}.</p>
 */
public final class Ae2wtlibIntegration {
    private static TianshuWirelessTerminalItem terminalItem;

    private Ae2wtlibIntegration() {
    }

    /**
     * Registers the overloaded frequency card as a one-slot upgrade for every
     * ae2wtlib wireless terminal (and the universal terminal). If ae2wtlib has
     * not finished its own upgrade registration yet, {@link UpgradeHelper}
     * queues this and applies it once it is ready.
     */
    public static void register() {
        UpgradeHelper.addUpgradeToAllTerminals(ModItems.OVERLOADED_FREQUENCY_CARD.get(), 1);
    }

    /** Must run from the mod constructor, before ae2wtlib executes its registration event. */
    public static void registerTerminalDefinition() {
        AddTerminalEvent.register(event -> {
            if (terminalItem == null) terminalItem = new TianshuWirelessTerminalItem();
            event.builder(
                        "tianshu_pattern_encoding",
                        TianshuWirelessMenuHost::new,
                        TianshuWirelessPatternEncodingTermMenu.TYPE,
                        terminalItem,
                        Icon.PATTERN_ENCODING)
                .translationKey("item.ae2lt.tianshu_wireless_pattern_encoding_terminal")
                .upgradeCount(4)
                .addTerminal();
        });
    }

    public static DeferredItem<Item> registerTerminalItem(DeferredRegister.Items items) {
        return items.register("tianshu_wireless_pattern_encoding_terminal",
                () -> {
                    if (terminalItem == null) terminalItem = new TianshuWirelessTerminalItem();
                    return terminalItem;
                });
    }

    public static DeferredHolder<MenuType<?>, MenuType<TianshuWirelessPatternEncodingTermMenu>>
    registerTerminalMenu(DeferredRegister<MenuType<?>> menus) {
        return menus.register("tianshu_wireless_pattern_encoding_terminal",
                () -> TianshuWirelessPatternEncodingTermMenu.TYPE);
    }

    public static void registerTerminalScreen(RegisterMenuScreensEvent event) {
        event.register(TianshuWirelessPatternEncodingTermMenu.TYPE,
                Ae2wtlibIntegration::createTerminalScreen);
    }

    private static TianshuWirelessPatternEncodingTermScreen createTerminalScreen(
            TianshuWirelessPatternEncodingTermMenu menu, Inventory inventory, Component title) {
        return new TianshuWirelessPatternEncodingTermScreen(menu, inventory, title,
                StyleManager.loadStyleDoc(
                        "/screens/terminals/tianshu_wireless_pattern_encoding_terminal.json"));
    }
}
