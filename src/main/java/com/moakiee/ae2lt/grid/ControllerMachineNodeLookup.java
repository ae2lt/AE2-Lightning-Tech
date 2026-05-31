package com.moakiee.ae2lt.grid;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import appeng.blockentity.networking.ControllerBlockEntity;

import com.moakiee.ae2lt.blockentity.OverloadedControllerBlockEntity;

public final class ControllerMachineNodeLookup {

    private ControllerMachineNodeLookup() {
    }

    public static boolean hasOverloadedControllerNodes(Map<Class<?>, ? extends Collection<?>> machines) {
        return hasMatchingControllerNodes(machines, ControllerMachineNodeLookup::isOverloadedControllerClass);
    }

    static boolean hasMatchingControllerNodes(Map<Class<?>, ? extends Collection<?>> machines,
                                              Predicate<Class<?>> controllerClassMatcher) {
        for (var entry : machines.entrySet()) {
            if (controllerClassMatcher.test(entry.getKey()) && !entry.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static Set<Class<?>> normalizedMachineClasses(Map<Class<?>, ? extends Collection<?>> machines) {
        return normalizedMachineClasses(
                machines,
                ControllerMachineNodeLookup::isOverloadedControllerClass,
                ControllerBlockEntity.class);
    }

    static Set<Class<?>> normalizedMachineClasses(Map<Class<?>, ? extends Collection<?>> machines,
                                                  Predicate<Class<?>> controllerClassMatcher,
                                                  Class<?> controllerBaseClass) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        boolean hasOverloadedControllers = false;

        for (var machineClass : machines.keySet()) {
            if (controllerClassMatcher.test(machineClass)) {
                hasOverloadedControllers = true;
            } else {
                classes.add(machineClass);
            }
        }

        if (hasOverloadedControllers) {
            classes.add(controllerBaseClass);
        }

        return classes;
    }

    public static <T> List<T> controllerNodes(Map<Class<?>, ? extends Collection<T>> machines) {
        return controllerNodes(
                machines,
                ControllerMachineNodeLookup::isOverloadedControllerClass,
                ControllerBlockEntity.class);
    }

    static <T> List<T> controllerNodes(Map<Class<?>, ? extends Collection<T>> machines,
                                       Predicate<Class<?>> controllerClassMatcher,
                                       Class<?> controllerBaseClass) {
        Set<T> nodes = new LinkedHashSet<>();

        var vanillaControllers = machines.get(controllerBaseClass);
        if (vanillaControllers != null) {
            nodes.addAll(vanillaControllers);
        }

        for (var entry : machines.entrySet()) {
            if (controllerClassMatcher.test(entry.getKey())) {
                nodes.addAll(entry.getValue());
            }
        }

        return List.copyOf(nodes);
    }

    private static boolean isOverloadedControllerClass(Class<?> machineClass) {
        return OverloadedControllerBlockEntity.class.isAssignableFrom(machineClass);
    }
}
