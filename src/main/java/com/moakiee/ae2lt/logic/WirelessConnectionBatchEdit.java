package com.moakiee.ae2lt.logic;

import java.util.List;
import java.util.function.Function;

/** @deprecated Use {@code com.moakiee.thunderbolt.api.wireless.WirelessConnectionBatchEdit}. */
@Deprecated(forRemoval = false)
public final class WirelessConnectionBatchEdit {
    private WirelessConnectionBatchEdit() {}

    public record Plan<T>(boolean deselecting, List<T> disconnect, List<T> update, List<T> connect) {
        public boolean hasChanges() {
            return !disconnect.isEmpty() || !update.isEmpty() || !connect.isEmpty();
        }
    }

    public static <T, C, D, F> Plan<T> planSingleFacePerTarget(
            Iterable<T> targets,
            D dimension,
            Iterable<C> connections,
            F face,
            Function<C, D> dimensionGetter,
            Function<C, T> posGetter,
            Function<C, F> faceGetter) {
        return wrap(com.moakiee.thunderbolt.api.wireless.WirelessConnectionBatchEdit
                .planSingleFacePerTarget(
                        targets, dimension, connections, face,
                        dimensionGetter, posGetter, faceGetter));
    }

    public static <T, C, D, F> Plan<T> planMultiFacePerTarget(
            Iterable<T> targets,
            D dimension,
            Iterable<C> connections,
            F face,
            Function<C, D> dimensionGetter,
            Function<C, T> posGetter,
            Function<C, F> faceGetter) {
        return wrap(com.moakiee.thunderbolt.api.wireless.WirelessConnectionBatchEdit
                .planMultiFacePerTarget(
                        targets, dimension, connections, face,
                        dimensionGetter, posGetter, faceGetter));
    }

    private static <T> Plan<T> wrap(
            com.moakiee.thunderbolt.api.wireless.WirelessConnectionBatchEdit.Plan<T> plan) {
        return new Plan<>(plan.deselecting(), plan.disconnect(), plan.update(), plan.connect());
    }
}
