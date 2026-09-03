package com.moakiee.ae2lt.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import appeng.client.gui.style.ScreenStyle;

import org.junit.jupiter.api.Test;

class AE2LTStyleManagerTest {
    private static final List<String> MERGED_OBJECTS = List.of(
            "slots",
            "text",
            "palette",
            "images",
            "terminalStyle",
            "widgets");

    @Test
    void mergedObjectsCombineByKeyAndRootValuesWin() throws Exception {
        JsonObject included = new JsonObject();
        JsonObject root = new JsonObject();
        for (String property : MERGED_OBJECTS) {
            JsonObject includedValues = new JsonObject();
            includedValues.addProperty("included", property);
            includedValues.addProperty("shared", "included");
            included.add(property, includedValues);

            JsonObject rootValues = new JsonObject();
            rootValues.addProperty("root", property);
            rootValues.addProperty("shared", "root");
            root.add(property, rootValues);
        }
        included.addProperty("helpTopic", "included");
        root.addProperty("helpTopic", "root");

        JsonObject result = invokeStatic("combineLayers", new Class<?>[] { List.class }, List.of(included, root));

        assertEquals("root", result.get("helpTopic").getAsString());
        for (String property : MERGED_OBJECTS) {
            JsonObject values = result.getAsJsonObject(property);
            assertEquals(property, values.get("included").getAsString(), property);
            assertEquals(property, values.get("root").getAsString(), property);
            assertEquals("root", values.get("shared").getAsString(), property);
        }
    }

    @Test
    void includesInheritTheirNamespaceUnlessExplicitlyQualified() throws Exception {
        ResourceLocation source = new ResourceLocation("ae2lt", "screens/terminals/root.json");

        ResourceLocation relative = invokeStatic(
                "resolveInclude",
                new Class<?>[] { ResourceLocation.class, String.class },
                source,
                "../common/base.json");
        ResourceLocation explicit = invokeStatic(
                "resolveInclude",
                new Class<?>[] { ResourceLocation.class, String.class },
                source,
                "ae2:screens/common/common.json");

        assertEquals(new ResourceLocation("ae2lt", "screens/common/base.json"), relative);
        assertEquals(new ResourceLocation("ae2", "screens/common/common.json"), explicit);
    }

    @Test
    void pathsAreNormalizedAndCannotEscapeTheScreensRoot() throws Exception {
        ResourceLocation normalized = invokeStatic(
                "parseRootPath",
                new Class<?>[] { String.class },
                "ae2lt:screens/terminals/../root.json");
        assertEquals(new ResourceLocation("ae2lt", "screens/root.json"), normalized);

        assertInvocationCause(
                IllegalArgumentException.class,
                () -> invokeStatic(
                        "parseRootPath",
                        new Class<?>[] { String.class },
                        "ae2:screens/root.json"));
        assertInvocationCause(
                IllegalArgumentException.class,
                () -> invokeStatic(
                        "resolveInclude",
                        new Class<?>[] { ResourceLocation.class, String.class },
                        new ResourceLocation("ae2lt", "screens/terminals/root.json"),
                        "../../outside.json"));
    }

    @Test
    void recursiveAndDuplicateIncludesAreRejected() throws Exception {
        ResourceLocation root = new ResourceLocation("ae2lt", "screens/root.json");
        ResourceLocation child = new ResourceLocation("ae2lt", "screens/child.json");

        ResourceManager cycleManager = new MapResourceManager(Map.of(
                root, "{\"includes\":[\"child.json\"]}",
                child, "{\"includes\":[\"root.json\"]}"));
        AE2LTStyleManager.initialize(cycleManager);
        assertInvocationCause(IllegalStateException.class, () -> loadMergedTree(root));

        ResourceManager duplicateManager = new MapResourceManager(Map.of(
                root, "{\"includes\":[\"child.json\",\"child.json\"]}",
                child, "{}"));
        AE2LTStyleManager.initialize(duplicateManager);
        assertInvocationCause(IllegalStateException.class, () -> loadMergedTree(root));
    }

    @Test
    void resourceReloadReplacesTheManagerAndClearsTheCache() throws Exception {
        Field cacheField = AE2LTStyleManager.class.getDeclaredField("styleCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<ResourceLocation, ScreenStyle> cache =
                (Map<ResourceLocation, ScreenStyle>) cacheField.get(AE2LTStyleManager.INSTANCE);
        cache.put(new ResourceLocation("ae2lt", "screens/cached.json"), new ScreenStyle());

        ResourceManager replacement = new MapResourceManager(Map.of());
        AE2LTStyleManager.INSTANCE.onResourceManagerReload(replacement);

        Field managerField = AE2LTStyleManager.class.getDeclaredField("resourceManager");
        managerField.setAccessible(true);
        assertSame(replacement, managerField.get(AE2LTStyleManager.INSTANCE));
        assertTrue(cache.isEmpty());
    }

    private static JsonObject loadMergedTree(ResourceLocation id) throws Exception {
        Method method = AE2LTStyleManager.class.getDeclaredMethod(
                "loadMergedJsonTree", ResourceLocation.class, Set.class);
        method.setAccessible(true);
        return (JsonObject) method.invoke(AE2LTStyleManager.INSTANCE, id, new HashSet<>());
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeStatic(String name, Class<?>[] parameterTypes, Object... arguments)
            throws Exception {
        Method method = AE2LTStyleManager.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return (T) method.invoke(null, arguments);
    }

    private static void assertInvocationCause(
            Class<? extends Throwable> expected,
            ThrowingInvocation invocation) {
        InvocationTargetException exception = assertThrows(InvocationTargetException.class, invocation::run);
        assertTrue(expected.isInstance(exception.getCause()), String.valueOf(exception.getCause()));
    }

    @FunctionalInterface
    private interface ThrowingInvocation {
        void run() throws Exception;
    }

    private static final class MapResourceManager implements ResourceManager {
        private final Map<ResourceLocation, String> resources;

        private MapResourceManager(Map<ResourceLocation, String> resources) {
            this.resources = resources;
        }

        @Override
        public Optional<Resource> getResource(ResourceLocation id) {
            String content = resources.get(id);
            if (content == null) {
                return Optional.empty();
            }
            return Optional.of(new Resource(
                    null,
                    () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
        }

        @Override
        public Set<String> getNamespaces() {
            return Set.of("ae2", "ae2lt");
        }

        @Override
        public List<Resource> getResourceStack(ResourceLocation id) {
            return getResource(id).map(List::of).orElseGet(List::of);
        }

        @Override
        public Map<ResourceLocation, Resource> listResources(
                String path,
                java.util.function.Predicate<ResourceLocation> filter) {
            return Map.of();
        }

        @Override
        public Map<ResourceLocation, List<Resource>> listResourceStacks(
                String path,
                java.util.function.Predicate<ResourceLocation> filter) {
            return Map.of();
        }

        @Override
        public Stream<PackResources> listPacks() {
            return Stream.empty();
        }
    }
}
