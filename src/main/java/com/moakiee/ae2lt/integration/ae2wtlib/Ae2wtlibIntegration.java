package com.moakiee.ae2lt.integration.ae2wtlib;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.TianshuWirelessPatternEncodingTerminalItem;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessPatternEncodingTermMenuHost;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.resources.ResourceLocation;

import de.mari_023.ae2wtlib.api.gui.Icon;
import de.mari_023.ae2wtlib.api.registration.AddTerminalEvent;
import de.mari_023.ae2wtlib.api.registration.UpgradeHelper;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;

/**
 * AE2WTLib integration entry point.
 *
 * <p>The API module is embedded in AE2LT, allowing the wireless item and its terminal definition
 * to exist without the optional full AE2WTLib mod. Features supplied by the full implementation,
 * such as the universal terminal and cross-mod upgrade registration, remain guarded by its mod
 * presence.</p>
 */
public final class Ae2wtlibIntegration {
    public static final String TIANSHU_TERMINAL_NAME = "tianshu_pattern_encoding";
    private static final Icon TIANSHU_TERMINAL_ICON = new Icon(
            0, 0, 16, 16,
            new Icon.Texture(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID,
                    "textures/gui/icons/wireless_tianshu_pattern_encoding_terminal.png"),
                    16, 16));

    /**
     * Shared by the DeferredRegister and AE2WTLib's terminal definition. It cannot be created
     * during mod construction because ItemWT needs the intrusive item registry to be writable.
     */
    private static TianshuWirelessPatternEncodingTerminalItem tianshuTerminal;

    private static boolean terminalRegistrationRequested;

    private Ae2wtlibIntegration() {
    }

    /**
     * Creates the terminal only while the item registry is open. Depending on cross-mod event
     * dispatch order, either AE2LT's DeferredRegister or AE2WTLib's callback can be the first
     * caller; both must receive the exact same ItemWT instance.
     */
    public static synchronized TianshuWirelessPatternEncodingTerminalItem terminal() {
        if (tianshuTerminal == null) {
            tianshuTerminal = new TianshuWirelessPatternEncodingTerminalItem();
        }
        return tianshuTerminal;
    }

    /**
     * Installs the handler during mod construction. AddTerminalEvent is a one-shot callback list
     * that is consumed during item registration, not a normal NeoForge event-bus event.
     */
    public static synchronized void registerTerminal() {
        if (terminalRegistrationRequested) {
            return;
        }

        AddTerminalEvent.register(event -> event.builder(
                        TIANSHU_TERMINAL_NAME,
                        TianshuWirelessPatternEncodingTermMenuHost::new,
                        TianshuWirelessPatternEncodingTermMenu.TYPE,
                        terminal(),
                        TIANSHU_TERMINAL_ICON)
                .addTerminal());
        terminalRegistrationRequested = true;
    }

    /** Fails fast if an incompatible API/event ordering prevented the definition from registering. */
    public static void verifyTerminalRegistration() {
        if (!WTDefinition.exists(TIANSHU_TERMINAL_NAME)
                || WTDefinition.of(TIANSHU_TERMINAL_NAME).item()
                        != ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get()) {
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
