package com.moakiee.ae2lt.celestweave.state;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArmorRuntimeRegistry {
    private static final java.util.Map<String, Boolean> SERVER_SUBMODULE_ACTIVE =
            new ConcurrentHashMap<>();
    private static final java.util.Map<String, Boolean> CLIENT_SUBMODULE_ACTIVE =
            new ConcurrentHashMap<>();
    private static final java.util.Map<String, Boolean> SUBMODULE_RUNTIME_ACTIVE =
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
        SUBMODULE_RUNTIME_ACTIVE.put(cacheKey(armorId, submoduleId), active);
    }

    public static boolean isSubmoduleRuntimeActive(UUID armorId, String submoduleId) {
        return armorId != null && SUBMODULE_RUNTIME_ACTIVE.getOrDefault(cacheKey(armorId, submoduleId), false);
    }

    public static Set<String> submoduleIds(UUID armorId) {
        Set<String> ids = new HashSet<>();
        if (armorId == null) {
            return ids;
        }
        String prefix = armorId + "#";
        for (String key : SUBMODULE_RUNTIME_ACTIVE.keySet()) {
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
        SUBMODULE_RUNTIME_ACTIVE.remove(key);
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
        SUBMODULE_RUNTIME_ACTIVE.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String cacheKey(UUID armorId, String submoduleId) {
        return armorId + "#" + submoduleId;
    }
}
