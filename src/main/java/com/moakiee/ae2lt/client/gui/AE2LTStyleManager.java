package com.moakiee.ae2lt.client.gui;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import appeng.client.gui.style.ScreenStyle;

/** Loads AE2LT screen styles without placing them in AE2's resource namespace. */
public final class AE2LTStyleManager implements ResourceManagerReloadListener {
    public static final AE2LTStyleManager INSTANCE = new AE2LTStyleManager();

    private static final String AE2LT_NAMESPACE = "ae2lt";
    private static final String SCREEN_ROOT = "screens/";
    private static final Set<String> MERGED_OBJECTS = Set.of(
            "slots",
            "text",
            "palette",
            "images",
            "terminalStyle",
            "widgets");

    private final Map<ResourceLocation, ScreenStyle> styleCache = new HashMap<>();
    private ResourceManager resourceManager;

    private AE2LTStyleManager() {
    }

    public static void initialize(ResourceManager resourceManager) {
        synchronized (INSTANCE) {
            INSTANCE.setResourceManager(resourceManager);
        }
    }

    public static ScreenStyle loadStyleDoc(String path) {
        ResourceLocation styleId = parseRootPath(path);
        synchronized (INSTANCE) {
            ScreenStyle cached = INSTANCE.styleCache.get(styleId);
            if (cached != null) {
                return cached;
            }

            try {
                ScreenStyle style = INSTANCE.loadStyleDoc(styleId);
                INSTANCE.styleCache.put(styleId, style);
                return style;
            } catch (Exception exception) {
                throw new RuntimeException("Failed to load style document " + styleId, exception);
            }
        }
    }

    public static boolean handles(String path) {
        return path.startsWith(AE2LT_NAMESPACE + ":");
    }

    @Override
    public synchronized void onResourceManagerReload(ResourceManager resourceManager) {
        setResourceManager(resourceManager);
    }

    private void setResourceManager(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
        styleCache.clear();
    }

    private ScreenStyle loadStyleDoc(ResourceLocation styleId) throws IOException {
        JsonObject json = loadMergedJsonTree(styleId, new HashSet<>());
        ScreenStyle style = ScreenStyle.GSON.fromJson(json, ScreenStyle.class);
        if (style == null) {
            throw new JsonParseException("Style document is empty: " + styleId);
        }
        style.validate();
        return style;
    }

    private JsonObject loadMergedJsonTree(ResourceLocation styleId, Set<ResourceLocation> loadedStyles)
            throws IOException {
        ResourceLocation normalizedId = normalize(styleId);
        if (!loadedStyles.add(normalizedId)) {
            throw new IllegalStateException("Style include cycle or duplicate include at " + normalizedId);
        }

        ResourceManager manager = resourceManager;
        if (manager == null) {
            manager = Minecraft.getInstance().getResourceManager();
            setResourceManager(manager);
        }
        if (manager.getResource(normalizedId).isEmpty()) {
            throw new FileNotFoundException(normalizedId.toString());
        }

        JsonObject document;
        try (Reader reader = manager.openAsReader(normalizedId)) {
            document = ScreenStyle.GSON.fromJson(reader, JsonObject.class);
        }
        if (document == null) {
            throw new JsonParseException("Style document is empty: " + normalizedId);
        }

        List<JsonObject> layers = new ArrayList<>();
        if (document.has("includes")) {
            String[] includes = ScreenStyle.GSON.fromJson(document.get("includes"), String[].class);
            for (String include : includes) {
                layers.add(loadMergedJsonTree(resolveInclude(normalizedId, include), loadedStyles));
            }
        }
        layers.add(document);
        return combineLayers(layers);
    }

    private static JsonObject combineLayers(List<JsonObject> layers) {
        JsonObject result = new JsonObject();
        for (JsonObject layer : layers) {
            layer.entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue()));
        }

        for (String property : MERGED_OBJECTS) {
            JsonObject merged = new JsonObject();
            boolean present = false;
            for (JsonObject layer : layers) {
                if (!layer.has(property)) {
                    continue;
                }
                if (!layer.get(property).isJsonObject()) {
                    throw new JsonParseException("Style property '" + property + "' must be an object");
                }
                present = true;
                layer.getAsJsonObject(property).entrySet()
                        .forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
            }
            if (present) {
                result.add(property, merged);
            }
        }
        return result;
    }

    private static ResourceLocation parseRootPath(String path) {
        if (!handles(path)) {
            throw new IllegalArgumentException("AE2LT style paths must use the ae2lt namespace: " + path);
        }
        ResourceLocation id = ResourceLocation.tryParse(path);
        if (id == null) {
            throw new IllegalArgumentException("Invalid style resource location: " + path);
        }
        return normalize(id);
    }

    private static ResourceLocation resolveInclude(ResourceLocation source, String include) {
        ResourceLocation explicit = include.indexOf(':') >= 0 ? ResourceLocation.tryParse(include) : null;
        if (include.indexOf(':') >= 0 && explicit == null) {
            throw new IllegalArgumentException("Invalid style include '" + include + "' in " + source);
        }
        if (explicit != null) {
            return normalize(explicit);
        }

        String sourcePath = source.getPath();
        int separator = sourcePath.lastIndexOf('/');
        String parent = separator >= 0 ? sourcePath.substring(0, separator + 1) : "";
        return normalize(new ResourceLocation(source.getNamespace(), parent + include));
    }

    private static ResourceLocation normalize(ResourceLocation id) {
        String normalizedPath = URI.create('/' + id.getPath()).normalize().getPath().substring(1);
        if (!normalizedPath.startsWith(SCREEN_ROOT)) {
            throw new IllegalArgumentException("Style resource must stay under screens/: " + id);
        }
        return new ResourceLocation(id.getNamespace(), normalizedPath);
    }
}
