package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class WirelessConnectionBatchEdit {
    private WirelessConnectionBatchEdit() {
    }

    public record Plan<T>(
            boolean deselecting,
            List<T> disconnect,
            List<T> update,
            List<T> connect
    ) {
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
        var targetList = copyTargets(targets);
        if (targetList.isEmpty()) {
            return emptyPlan();
        }

        boolean allSelected = true;
        for (var target : targetList) {
            var existing = findTargetConnection(
                    connections, dimension, target, dimensionGetter, posGetter);
            if (existing == null || !Objects.equals(faceGetter.apply(existing), face)) {
                allSelected = false;
                break;
            }
        }

        if (allSelected) {
            return new Plan<>(true, targetList, List.of(), List.of());
        }

        var update = new ArrayList<T>();
        var connect = new ArrayList<T>();
        for (var target : targetList) {
            var existing = findTargetConnection(
                    connections, dimension, target, dimensionGetter, posGetter);
            if (existing == null) {
                connect.add(target);
            } else if (!Objects.equals(faceGetter.apply(existing), face)) {
                update.add(target);
            }
        }
        return new Plan<>(false, List.of(), List.copyOf(update), List.copyOf(connect));
    }

    public static <T, C, D, F> Plan<T> planMultiFacePerTarget(
            Iterable<T> targets,
            D dimension,
            Iterable<C> connections,
            F face,
            Function<C, D> dimensionGetter,
            Function<C, T> posGetter,
            Function<C, F> faceGetter) {
        var targetList = copyTargets(targets);
        if (targetList.isEmpty()) {
            return emptyPlan();
        }

        boolean allSelected = true;
        for (var target : targetList) {
            if (!hasEndpoint(connections, dimension, target, face,
                    dimensionGetter, posGetter, faceGetter)) {
                allSelected = false;
                break;
            }
        }

        if (allSelected) {
            return new Plan<>(true, targetList, List.of(), List.of());
        }

        var connect = new ArrayList<T>();
        for (var target : targetList) {
            if (!hasEndpoint(connections, dimension, target, face,
                    dimensionGetter, posGetter, faceGetter)) {
                connect.add(target);
            }
        }
        return new Plan<>(false, List.of(), List.of(), List.copyOf(connect));
    }

    private static <T> List<T> copyTargets(Iterable<T> targets) {
        var result = new ArrayList<T>();
        for (var target : targets) {
            result.add(target);
        }
        return List.copyOf(result);
    }

    private static <T> Plan<T> emptyPlan() {
        return new Plan<>(false, List.of(), List.of(), List.of());
    }

    private static <T, C, D> C findTargetConnection(
            Iterable<C> connections,
            D dimension,
            T target,
            Function<C, D> dimensionGetter,
            Function<C, T> posGetter) {
        for (var connection : connections) {
            if (Objects.equals(dimensionGetter.apply(connection), dimension)
                    && Objects.equals(posGetter.apply(connection), target)) {
                return connection;
            }
        }
        return null;
    }

    private static <T, C, D, F> boolean hasEndpoint(
            Iterable<C> connections,
            D dimension,
            T target,
            F face,
            Function<C, D> dimensionGetter,
            Function<C, T> posGetter,
            Function<C, F> faceGetter) {
        for (var connection : connections) {
            if (Objects.equals(dimensionGetter.apply(connection), dimension)
                    && Objects.equals(posGetter.apply(connection), target)
                    && Objects.equals(faceGetter.apply(connection), face)) {
                return true;
            }
        }
        return false;
    }
}
