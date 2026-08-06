package com.moakiee.ae2lt.logic;

import net.minecraftforge.fluids.FluidStack;

public final class FluidStackHelper {
    private FluidStackHelper() {
    }

    public static boolean sameFluidAndTag(FluidStack first, FluidStack second) {
        return first.isFluidEqual(second) && FluidStack.areFluidStackTagsEqual(first, second);
    }
}
