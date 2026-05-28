package com.moakiee.ae2lt.overload.armor.state;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArmorRuntimeRegistry {
    private static final java.util.Map<String, Boolean> SERVER_SUBMODULE_ACTIVE =
            new ConcurrentHashMap<>();
    private static final java.util.Map<String, Boolean> CLIENT_SUBMODULE_ACTIVE =
            new ConcurrentHashMap<>();
    private static final java.util.Map<String, SubmoduleRuntimeState> SUBMODULE_RUNTIME =
            new ConcurrentHashMap<>();

    private ArmorRuntimeRegistry() {
    }

    public static Boolean setServerSubmoduleActive(UUID armorId, String submoduleId, boolean active) {
        return armorId == null ? null : SERVER_SUBMODULE_ACTIVE.put(cacheKey(armorId, submoduleId), active);
    }

    public static boolean isServerSubmoduleActive(UUID armorId, String submoduleId) {
        return armorId != null && SERVER_SUBMODULE_ACTIVE.getOrDefault(cacheKey(armorId, submoduleId), false);
    }

    public static Boolean setClientSubmoduleActive(UUID armorId, String submoduleId, boolean active) {
        return armorId == null ? null : CLIENT_SUBMODULE_ACTIVE.put(cacheKey(armorId, submoduleId), active);
    }

    public static boolean isClientSubmoduleActive(UUID armorId, String submoduleId) {
        return armorId != null && CLIENT_SUBMODULE_ACTIVE.getOrDefault(cacheKey(armorId, submoduleId), false);
    }

    public static boolean isAnyClientSubmoduleActive(String submoduleId) {
        if (submoduleId == null || submoduleId.isBlank()) {
            return false;
        }
        String suffix = "#" + submoduleId;
        for (var entry : CLIENT_SUBMODULE_ACTIVE.entrySet()) {
            if (entry.getKey().endsWith(suffix) && entry.getValue()) {
                return true;
            }
        }
        return false;
    }

    public static void clearClientActiveCache() {
        CLIENT_SUBMODULE_ACTIVE.clear();
    }

    public static void setSubmoduleRuntimeActive(UUID armorId, String submoduleId, boolean active) {
        if (armorId == null) {
            return;
        }
        String key = cacheKey(armorId, submoduleId);
        SubmoduleRuntimeState previous = SUBMODULE_RUNTIME.get(key);
        int load = active && previous != null ? previous.dynamicLoad() : 0;
        SUBMODULE_RUNTIME.put(key, new SubmoduleRuntimeState(active, load));
    }

    public static void setSubmoduleRuntimeDynamicLoad(UUID armorId, String submoduleId, int dynamicLoad) {
        if (armorId == null) {
            return;
        }
        String key = cacheKey(armorId, submoduleId);
        SubmoduleRuntimeState previous = SUBMODULE_RUNTIME.get(key);
        boolean active = previous != null && previous.active();
        SUBMODULE_RUNTIME.put(key, new SubmoduleRuntimeState(active, Math.max(0, dynamicLoad)));
    }

    public static SubmoduleRuntimeState getSubmoduleRuntime(UUID armorId, String submoduleId) {
        if (armorId == null) {
            return new SubmoduleRuntimeState(false, 0);
        }
        return SUBMODULE_RUNTIME.getOrDefault(cacheKey(armorId, submoduleId), new SubmoduleRuntimeState(false, 0));
    }

    public static Set<String> submoduleIds(UUID armorId) {
        Set<String> ids = new HashSet<>();
        if (armorId == null) {
            return ids;
        }
        String prefix = armorId + "#";
        for (String key : SUBMODULE_RUNTIME.keySet()) {
            if (key.startsWith(prefix)) {
                ids.add(key.substring(prefix.length()));
            }
        }
        return ids;
    }

    public static void removeSubmodule(UUID armorId, String submoduleId) {
        if (armorId == null) {
            return;
        }
        String key = cacheKey(armorId, submoduleId);
        SUBMODULE_RUNTIME.remove(key);
        SERVER_SUBMODULE_ACTIVE.remove(key);
        CLIENT_SUBMODULE_ACTIVE.remove(key);
    }

    public static void clear(UUID armorId) {
        if (armorId == null) {
            return;
        }
        String prefix = armorId + "#";
        SERVER_SUBMODULE_ACTIVE.keySet().removeIf(key -> key.startsWith(prefix));
        CLIENT_SUBMODULE_ACTIVE.keySet().removeIf(key -> key.startsWith(prefix));
        SUBMODULE_RUNTIME.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String cacheKey(UUID armorId, String submoduleId) {
        return armorId + "#" + submoduleId;
    }

    public record SubmoduleRuntimeState(boolean active, int dynamicLoad) {
    }
}
