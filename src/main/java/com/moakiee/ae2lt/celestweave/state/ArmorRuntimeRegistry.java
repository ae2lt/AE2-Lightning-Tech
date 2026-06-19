package com.moakiee.ae2lt.celestweave.state;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArmorRuntimeRegistry {
    // Only active==true entries are stored; absence means inactive. This keeps the maps from
    // accumulating stale false entries for unequipped/idle armor (no per-stack removal hook).
    private static final java.util.Map<String, Boolean> CLIENT_SUBMODULE_ACTIVE =
            new ConcurrentHashMap<>();
    private static final java.util.Map<String, Boolean> SUBMODULE_RUNTIME_ACTIVE =
            new ConcurrentHashMap<>();

    private ArmorRuntimeRegistry() {
    }

    // Returns the previous value (null when absent, i.e. was inactive).
    public static Boolean setClientSubmoduleActive(UUID armorId, String submoduleId, boolean active) {
        if (armorId == null) {
            return null;
        }
        String key = cacheKey(armorId, submoduleId);
        return active ? CLIENT_SUBMODULE_ACTIVE.put(key, Boolean.TRUE) : CLIENT_SUBMODULE_ACTIVE.remove(key);
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

    public static void replaceClientSubmoduleActiveStates(UUID armorId, Map<String, Boolean> activeStates) {
        if (armorId == null) {
            return;
        }
        String prefix = armorId + "#";
        CLIENT_SUBMODULE_ACTIVE.keySet().removeIf(key -> key.startsWith(prefix));
        if (activeStates == null || activeStates.isEmpty()) {
            return;
        }
        activeStates.forEach((submoduleId, active) -> {
            if (submoduleId != null && !submoduleId.isBlank() && Boolean.TRUE.equals(active)) {
                CLIENT_SUBMODULE_ACTIVE.put(cacheKey(armorId, submoduleId), Boolean.TRUE);
            }
        });
    }

    public static void clearClientActiveCache() {
        CLIENT_SUBMODULE_ACTIVE.clear();
    }

    // Returns the previous value (null when absent, i.e. was inactive).
    public static Boolean setSubmoduleRuntimeActive(UUID armorId, String submoduleId, boolean active) {
        if (armorId == null) {
            return null;
        }
        String key = cacheKey(armorId, submoduleId);
        return active ? SUBMODULE_RUNTIME_ACTIVE.put(key, Boolean.TRUE) : SUBMODULE_RUNTIME_ACTIVE.remove(key);
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
        CLIENT_SUBMODULE_ACTIVE.remove(key);
    }

    public static void clear(UUID armorId) {
        if (armorId == null) {
            return;
        }
        String prefix = armorId + "#";
        CLIENT_SUBMODULE_ACTIVE.keySet().removeIf(key -> key.startsWith(prefix));
        SUBMODULE_RUNTIME_ACTIVE.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String cacheKey(UUID armorId, String submoduleId) {
        return armorId + "#" + submoduleId;
    }
}
