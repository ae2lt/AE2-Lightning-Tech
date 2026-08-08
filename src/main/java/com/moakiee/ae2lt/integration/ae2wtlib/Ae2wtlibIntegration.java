package com.moakiee.ae2lt.integration.ae2wtlib;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;

import com.moakiee.ae2lt.item.TianshuWirelessPatternEncodingTerminalItem;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;

import de.mari_023.ae2wtlib.UpgradeHelper;
import de.mari_023.ae2wtlib.terminal.IUniversalWirelessTerminalItem;
import de.mari_023.ae2wtlib.wut.WUTHandler;

/**
 * AE2WTLib integration entry point (Forge API).
 *
 * <p>Registers the wireless Tianshu terminal with ae2wtlib's WUT handler so it
 * appears in the Wireless Universal Terminal and accepts the overloaded
 * frequency card in its upgrade slots. ae2wtlib is optional: every public
 * entry point here must only be called after an ae2wtlib presence check, since
 * this class (and {@link TianshuWTItem} / {@link TianshuWTMenuHost}) reference
 * ae2wtlib types and would otherwise fail to load. AE2WTLib's own
 * {@code UpgradeHelper} queues upgrades until its terminal registration has
 * finished, which makes the registration order safe.</p>
 */
public final class Ae2wtlibIntegration {
    public static final String TIANSHU_TERMINAL_NAME = "tianshu_pattern_encoding";

    private static TianshuWirelessPatternEncodingTerminalItem tianshuTerminal;
    private static boolean terminalRegistrationRequested;

    private Ae2wtlibIntegration() {
    }

    /**
     * Shared by the DeferredRegister and AE2WTLib's terminal definition. It cannot be
     * created during mod construction because {@code ItemWT} needs the intrusive item
     * registry to be writable. Only call when ae2wtlib is present.
     */
    public static synchronized TianshuWirelessPatternEncodingTerminalItem terminal() {
        if (tianshuTerminal == null) {
            tianshuTerminal = new TianshuWTItem();
        }
        return tianshuTerminal;
    }

    /**
     * Installs the terminal definition into the WUT handler during mod construction.
     * The menu-host factory mirrors the constructor shape of
     * {@link TianshuWTMenuHost}; the lambda adapts the boxed
     * slot parameter to the host's primitive one.
     */
    public static synchronized void registerTerminal() {
        if (terminalRegistrationRequested) {
            return;
        }

        WUTHandler.addTerminal(
                TIANSHU_TERMINAL_NAME,
                Ae2wtlibIntegration::tryOpen,
                TianshuWTMenuHost::new,
                TianshuWirelessPatternEncodingTermMenu.TYPE,
                (IUniversalWirelessTerminalItem) terminal());
        terminalRegistrationRequested = true;
    }

    /**
     * Creates the ae2wtlib menu host for the standalone item (used by the item's
     * {@code getMenuHost} when ae2wtlib is present, so the frequency-card remote
     * link works for the handheld terminal as well).
     */
    public static TianshuWTMenuHost createMenuHost(Player player, int slot, ItemStack stack) {
        return new TianshuWTMenuHost(player, slot, stack,
                (p, subMenu) -> tryOpen(player, MenuLocators.forInventorySlot(slot), stack, true));
    }

    private static boolean tryOpen(Player player, MenuLocator locator, ItemStack stack,
            boolean returningFromSubmenu) {
        return MenuOpener.open(TianshuWirelessPatternEncodingTermMenu.TYPE,
                player, locator, returningFromSubmenu);
    }

    /**
     * Fails fast if the WUT handler did not pick up the terminal definition.
     * Only call when ae2wtlib is present.
     */
    public static void verifyTerminalRegistration() {
        var definition = WUTHandler.wirelessTerminals.get(TIANSHU_TERMINAL_NAME);
        if (definition == null
                || definition.item() != ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get()) {
            throw new IllegalStateException("AE2WTLib did not register the wireless Tianshu terminal");
        }
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
}
