package com.moakiee.ae2lt.device.network;

import org.jetbrains.annotations.Nullable;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;

public record BindingResolveResult(
        @Nullable IGrid grid,
        @Nullable IWirelessAccessPoint accessPoint,
        @Nullable FailureReason failure
) {
    public enum FailureReason {
        NOT_BOUND,
        DIM_NOT_LOADED,
        NO_AP,
        INACTIVE_AP,
        OUT_OF_RANGE,
        WRONG_DIMENSION
    }

    public static BindingResolveResult ok(IGrid grid, IWirelessAccessPoint accessPoint) {
        return new BindingResolveResult(grid, accessPoint, null);
    }

    public static BindingResolveResult fail(FailureReason failure) {
        return new BindingResolveResult(null, null, failure);
    }

    public boolean success() {
        return failure == null && grid != null;
    }
}
