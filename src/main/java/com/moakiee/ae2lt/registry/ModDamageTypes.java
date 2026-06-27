package com.moakiee.ae2lt.registry;

import java.util.concurrent.ConcurrentHashMap;

import com.moakiee.ae2lt.AE2LightningTech;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;

/**
 * Pure resource-key holder for our custom damage types. The actual JSON for
 * {@code data/ae2lt/damage_type/electromagnetic.json} carries the message id
 * and exhaustion settings. We do per-hit armor bypass manually (see
 * {@code RailgunDamageCalculator}), so the JSON does NOT carry bypasses_armor.
 */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> ELECTROMAGNETIC = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "electromagnetic"));

    /**
     * Per-{@link ServerLevel} cache of the resolved holder. Damage-type registries
     * are frozen at server start, so the holder is stable for the level's life.
     */
    private static final ConcurrentHashMap<ServerLevel, Holder<DamageType>> ELECTROMAGNETIC_CACHE =
            new ConcurrentHashMap<>();

    public static Holder<DamageType> electromagneticHolder(ServerLevel level) {
        return ELECTROMAGNETIC_CACHE.computeIfAbsent(level, l ->
                l.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ELECTROMAGNETIC));
    }

    /** Cleared on server stop so an integrated server re-launch starts fresh. */
    public static void clearCache() {
        ELECTROMAGNETIC_CACHE.clear();
    }

    private ModDamageTypes() {}
}
