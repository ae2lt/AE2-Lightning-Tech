package com.moakiee.ae2lt.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Implemented by machine menus whose host can open the shared frequency
 * binding GUI from a toolbar button.
 */
public interface FrequencyBindingMenu {
    BlockPos getFrequencyBindingBlockPos();

    default int getFrequencyBindingToken() {
        return ((AbstractContainerMenu) this).containerId;
    }
}
