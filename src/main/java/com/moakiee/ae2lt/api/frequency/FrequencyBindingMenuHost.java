package com.moakiee.ae2lt.api.frequency;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Marker for {@link AbstractContainerMenu} subclasses whose host block entity
 * supports the frequency binding UI. Implementing this lets the screen call
 * {@link FrequencyApi#openBindingScreen(AbstractContainerMenu)} from a button
 * to invoke AE2LT's shared frequency selection GUI.
 */
public interface FrequencyBindingMenuHost {
    /** Position of the bound block entity in the menu's level. */
    BlockPos getFrequencyBindingBlockPos();

    /** Token the server uses to verify the open request. Defaults to the menu's container id. */
    default int getFrequencyBindingToken() {
        return ((AbstractContainerMenu) this).containerId;
    }
}
