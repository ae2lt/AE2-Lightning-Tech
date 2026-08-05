package com.moakiee.ae2lt.crafting.timewheel;

/** A planning-only pattern that must be submitted to one compatible time-wheel CPU pool. */
public interface TimeWheelPoolRestrictedPattern {
    boolean acceptsTimeWheelPool(TimeWheelCraftingCpuPoolHost host);
}
