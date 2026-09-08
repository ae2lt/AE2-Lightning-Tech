package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.inventories.ISegmentedInventory;
import appeng.api.storage.ITerminalHost;

/** The ordinary AE2 crafting inventory plus access to the same Tianshu maintenance service. */
public interface TianshuCraftingTerminalHost extends ITerminalHost, ISegmentedInventory, TianshuTerminalHost {
}
