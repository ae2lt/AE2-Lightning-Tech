package com.moakiee.ae2lt.menu;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.slot.AppEngSlot;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.api.pattern.PatternProviderUiProfile;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessDispatchMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessSpeedMode;
import com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic;

public class OverloadedPatternProviderMenu extends PatternProviderMenu implements FrequencyBindingMenu {

    public static final MenuType<OverloadedPatternProviderMenu> TYPE = Ae2ltMenuBuilder.buildUnregistered(
            MenuTypeBuilder
                    .create((id, playerInventory, host) ->
                            new OverloadedPatternProviderMenu(id, playerInventory, host), PatternProviderLogicHost.class),
            new ResourceLocation(
                    AE2LightningTech.MODID, "overloaded_pattern_provider"));

    private static final int SLOTS_PER_PAGE = 36;
    private static final int PROFILE_PACKAGED = 1;
    private static final int PROFILE_MODE_SWITCH = 1 << 1;
    private static final int PROFILE_FILTERED_IMPORT = 1 << 2;
    private static final int PROFILE_WIRELESS_TUNING = 1 << 3;
    private static final int PROFILE_BLOCKING_MODE = 1 << 4;
    private static final int DEFAULT_PROFILE_FLAGS = PROFILE_MODE_SWITCH
            | PROFILE_FILTERED_IMPORT
            | PROFILE_WIRELESS_TUNING
            | PROFILE_BLOCKING_MODE;

    @GuiSync(10)
    public int providerMode;

    @GuiSync(11)
    public int returnMode;

    @GuiSync(12)
    public int filteredImport;

    @GuiSync(13)
    public int wirelessDispatchMode;

    @GuiSync(16)
    public int wirelessSpeedMode;

    @GuiSync(17)
    public int uiProfileFlags = DEFAULT_PROFILE_FLAGS;

    @GuiSync(18)
    public String titleTranslationKey = PatternProviderUiProfile.DEFAULT_TITLE_TRANSLATION_KEY;

    @GuiSync(14)
    public int currentPage;

    @GuiSync(15)
    public int totalPages;

    private final PatternProviderLogicHost host;
    private int lastShownPage = -1;

    public OverloadedPatternProviderMenu(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        this(TYPE, id, playerInventory, host);
    }

    protected OverloadedPatternProviderMenu(MenuType<? extends OverloadedPatternProviderMenu> menuType,
                                            int id,
                                            Inventory playerInventory,
                                            PatternProviderLogicHost host) {
        super(menuType, id, playerInventory, host);
        this.host = host;

        registerClientAction("toggleMode", this::toggleMode);
        registerClientAction("toggleAutoReturn", this::toggleAutoReturn);
        registerClientAction("toggleWirelessDispatchMode", this::toggleWirelessDispatchMode);
        registerClientAction("toggleWirelessSpeedMode", this::toggleWirelessSpeedMode);
        registerClientAction("toggleFilteredImport", this::toggleFilteredImport);
        registerClientAction("nextPage", this::nextPage);
        registerClientAction("prevPage", this::prevPage);

        showPage(0);
        lastShownPage = -1;
    }

    public void showPage(int page) {
        if (page == lastShownPage) return;
        lastShownPage = page;

        List<net.minecraft.world.inventory.Slot> patternSlots =
                getSlots(SlotSemantics.ENCODED_PATTERN);

        int totalSlots = patternSlots.size();
        int start = page * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, totalSlots);

        for (int i = 0; i < totalSlots; i++) {
            var slot = patternSlots.get(i);
            if (slot instanceof AppEngSlot aeSlot) {
                aeSlot.setActive(i >= start && i < end);
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            providerMode = be.getProviderMode().ordinal();
            returnMode = be.getReturnMode().ordinal();
            filteredImport = be.isFilteredImport() ? 1 : 0;
            wirelessDispatchMode = be.getWirelessDispatchMode().ordinal();
            wirelessSpeedMode = be.getWirelessSpeedMode().ordinal();
            syncUiProfile(be);
            var logic = (OverloadedPatternProviderLogic) be.getLogic();
            currentPage = logic.getCurrentPage();
            totalPages = logic.getTotalPages();
            logic.syncReturnPageViewFromFull();
        }
        showPage(currentPage);
        super.broadcastChanges();
    }

    // -- Server-side action handlers --

    private void toggleMode() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            if (!isModeSwitchVisible(be)) return;
            var current = be.getProviderMode();
            be.setProviderMode(current == ProviderMode.NORMAL ? ProviderMode.WIRELESS : ProviderMode.NORMAL);
        }
    }

    private void toggleAutoReturn() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            var values = ReturnMode.values();
            var current = be.getReturnMode();
            be.setReturnMode(values[(current.ordinal() + 1) % values.length]);
        }
    }

    private void toggleWirelessDispatchMode() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            if (!isWirelessTuningVisible(be)) return;
            var values = WirelessDispatchMode.values();
            var current = be.getWirelessDispatchMode();
            be.setWirelessDispatchMode(values[(current.ordinal() + 1) % values.length]);
        }
    }

    private void toggleWirelessSpeedMode() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            if (!isWirelessTuningVisible(be)) return;
            var values = WirelessSpeedMode.values();
            var current = be.getWirelessSpeedMode();
            be.setWirelessSpeedMode(values[(current.ordinal() + 1) % values.length]);
        }
    }

    private void toggleFilteredImport() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            if (!isFilteredImportVisible(be)) return;
            be.setFilteredImport(!be.isFilteredImport());
        }
    }

    private void syncUiProfile(OverloadedPatternProviderBlockEntity be) {
        var profile = be instanceof PatternProviderUiProfile uiProfile ? uiProfile : null;
        int flags = 0;
        if (profile != null && profile.ae2lt$isPackagedProviderUi()) flags |= PROFILE_PACKAGED;
        if (profile == null || profile.ae2lt$isModeSwitchVisible()) flags |= PROFILE_MODE_SWITCH;
        if (profile == null || profile.ae2lt$isFilteredImportVisible()) flags |= PROFILE_FILTERED_IMPORT;
        if (profile == null || profile.ae2lt$isWirelessTuningVisible()) flags |= PROFILE_WIRELESS_TUNING;
        if (profile == null || profile.ae2lt$isBlockingModeVisible()) flags |= PROFILE_BLOCKING_MODE;
        uiProfileFlags = flags;
        titleTranslationKey = profile == null
                ? PatternProviderUiProfile.DEFAULT_TITLE_TRANSLATION_KEY
                : profile.ae2lt$titleTranslationKey();
        if (titleTranslationKey == null || titleTranslationKey.isBlank()) {
            titleTranslationKey = PatternProviderUiProfile.DEFAULT_TITLE_TRANSLATION_KEY;
        }
    }

    private static boolean isModeSwitchVisible(OverloadedPatternProviderBlockEntity be) {
        return !(be instanceof PatternProviderUiProfile profile) || profile.ae2lt$isModeSwitchVisible();
    }

    private static boolean isFilteredImportVisible(OverloadedPatternProviderBlockEntity be) {
        return !(be instanceof PatternProviderUiProfile profile) || profile.ae2lt$isFilteredImportVisible();
    }

    private static boolean isWirelessTuningVisible(OverloadedPatternProviderBlockEntity be) {
        return !(be instanceof PatternProviderUiProfile profile) || profile.ae2lt$isWirelessTuningVisible();
    }

    private void nextPage() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            var logic = (OverloadedPatternProviderLogic) be.getLogic();
            logic.setCurrentPage(logic.getCurrentPage() + 1);
        }
    }

    private void prevPage() {
        if (isServerSide() && host instanceof OverloadedPatternProviderBlockEntity be) {
            var logic = (OverloadedPatternProviderLogic) be.getLogic();
            logic.setCurrentPage(logic.getCurrentPage() - 1);
        }
    }

    // -- Public forwarding methods for Screen button callbacks --

    public void clientToggleMode() {
        sendClientAction("toggleMode");
    }

    public void clientToggleAutoReturn() {
        sendClientAction("toggleAutoReturn");
    }

    public void clientToggleWirelessDispatchMode() {
        sendClientAction("toggleWirelessDispatchMode");
    }

    public void clientToggleWirelessSpeedMode() {
        sendClientAction("toggleWirelessSpeedMode");
    }

    public void clientToggleFilteredImport() {
        sendClientAction("toggleFilteredImport");
    }

    // -- Client helpers --

    public boolean isWirelessMode() {
        return providerMode == ProviderMode.WIRELESS.ordinal();
    }

    public boolean isAutoReturnEnabled() {
        return returnMode != ReturnMode.OFF.ordinal();
    }

    public int getReturnModeOrdinal() {
        return returnMode;
    }

    public boolean isFilteredImport() {
        return isFilteredImportVisible() && filteredImport != 0;
    }

    public boolean isEvenDistributionMode() {
        return wirelessDispatchMode == WirelessDispatchMode.EVEN_DISTRIBUTION.ordinal();
    }

    public boolean isFastSpeedMode() {
        return isWirelessTuningVisible() && wirelessSpeedMode == WirelessSpeedMode.FAST.ordinal();
    }

    public boolean isPackagedProviderUi() {
        return (uiProfileFlags & PROFILE_PACKAGED) != 0;
    }

    public boolean isModeSwitchVisible() {
        return (uiProfileFlags & PROFILE_MODE_SWITCH) != 0;
    }

    public boolean isFilteredImportVisible() {
        return (uiProfileFlags & PROFILE_FILTERED_IMPORT) != 0;
    }

    public boolean isWirelessTuningVisible() {
        return (uiProfileFlags & PROFILE_WIRELESS_TUNING) != 0;
    }

    public boolean isBlockingModeVisible() {
        return (uiProfileFlags & PROFILE_BLOCKING_MODE) != 0;
    }

    public String getTitleTranslationKey() {
        return titleTranslationKey;
    }

    public void clientNextPage() {
        sendClientAction("nextPage");
    }

    public void clientPrevPage() {
        sendClientAction("prevPage");
    }

    @Override
    public net.minecraft.core.BlockPos getFrequencyBindingBlockPos() {
        if (host instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            return be.getBlockPos();
        }
        throw new IllegalStateException("Frequency binding host is not a block entity: " + host);
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }
}

