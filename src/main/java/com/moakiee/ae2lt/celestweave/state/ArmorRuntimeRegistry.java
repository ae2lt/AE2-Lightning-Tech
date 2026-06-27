package com.moakiee.ae2lt.celestweave.state;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side (and integrated-client) runtime "active" cache for armor submodules,
 * keyed by armor id. Only active entries are stored; absence means inactive, which
 * keeps the map from accumulating stale entries for unequipped/idle armor.
 *
 * <p>Client active state is no longer tracked here: clients derive it from the synced
 * stack (see {@code CelestweaveArmorState.isSubmoduleActiveClient}). Phase flight is the
 * sole exception and keeps a dedicated authoritative push.
 */
public final class ArmorRuntimeRegistry {
    private static final java.util.Map<String, Boolean> SUBMODULE_RUNTIME_ACTIVE =
            new ConcurrentHashMap<>();

    private ArmorRuntimeRegistry() {
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
        SUBMODULE_RUNTIME_ACTIVE.remove(cacheKey(armorId, submoduleId));
    }

    public static void clear(UUID armorId) {
        if (armorId == null) {
            return;
        }
        String prefix = armorId + "#";
        SUBMODULE_RUNTIME_ACTIVE.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String cacheKey(UUID armorId, String submoduleId) {
        return armorId + "#" + submoduleId;
    }
}
