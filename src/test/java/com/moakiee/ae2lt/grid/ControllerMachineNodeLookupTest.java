package com.moakiee.ae2lt.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

class ControllerMachineNodeLookupTest {

    @Test
    void matchingControllerNodesRequireAtLeastOneNode() {
        var machines = new LinkedHashMap<Class<?>, Collection<String>>();
        machines.put(BaseController.class, List.of("vanilla"));
        machines.put(OverloadedA.class, List.of());

        assertFalse(ControllerMachineNodeLookup.hasMatchingControllerNodes(
                machines, OverloadedA.class::isAssignableFrom));

        machines.put(OverloadedB.class, List.of("overloaded"));

        assertTrue(ControllerMachineNodeLookup.hasMatchingControllerNodes(
                machines, OverloadedA.class::isAssignableFrom));
    }

    @Test
    void normalizedMachineClassesReplaceOverloadedFamilyWithBaseControllerClass() {
        var machines = new LinkedHashMap<Class<?>, Collection<String>>();
        machines.put(Unrelated.class, List.of("storage"));
        machines.put(OverloadedA.class, List.of("overloaded-a"));
        machines.put(OverloadedB.class, List.of("overloaded-b"));

        var classes = ControllerMachineNodeLookup.normalizedMachineClasses(
                machines,
                OverloadedA.class::isAssignableFrom,
                BaseController.class);

        assertEquals(List.of(Unrelated.class, BaseController.class), List.copyOf(classes));
    }

    @Test
    void controllerNodesMergeVanillaAndOverloadedNodesWithoutDuplicates() {
        var machines = new LinkedHashMap<Class<?>, Collection<String>>();
        machines.put(BaseController.class, List.of("vanilla", "shared"));
        machines.put(Unrelated.class, List.of("ignored"));
        machines.put(OverloadedA.class, List.of("overloaded-a", "shared"));
        machines.put(OverloadedB.class, List.of("overloaded-b"));

        var nodes = ControllerMachineNodeLookup.controllerNodes(
                machines,
                OverloadedA.class::isAssignableFrom,
                BaseController.class);

        assertEquals(List.of("vanilla", "shared", "overloaded-a", "overloaded-b"), nodes);
    }

    private static class BaseController {
    }

    private static class OverloadedA extends BaseController {
    }

    private static class OverloadedB extends OverloadedA {
    }

    private static class Unrelated {
    }
}
