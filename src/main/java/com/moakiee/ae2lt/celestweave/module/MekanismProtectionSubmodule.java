package com.moakiee.ae2lt.celestweave.module;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;

import com.moakiee.ae2lt.integration.mekanism.MekanismArmorIntegration;

/**
 * Optional Mekanism-specific protection installed in the Celestweave chestplate.
 *
 * <p>The actual Mekanism item capabilities are registered by the optional integration layer.
 * The damage type is also exposed as a regular device capability so the armor damage pipeline
 * can provide a final fallback if another mod invokes the damage source directly.
 */
public final class MekanismProtectionSubmodule extends AbstractCelestweaveArmorSubmodule {
    public static final MekanismProtectionSubmodule RADIATION = new MekanismProtectionSubmodule(
            "radiation_protection",
            "radiation",
            "ae2lt.celestweave.feature.radiation_protection.name",
            "ae2lt.celestweave.feature.radiation_protection.desc");
    public static final MekanismProtectionSubmodule LASER = new MekanismProtectionSubmodule(
            "laser_protection",
            "laser",
            "ae2lt.celestweave.feature.laser_protection.name",
            "ae2lt.celestweave.feature.laser_protection.desc");

    private final String id;
    private final ResourceKey<DamageType> damageType;
    private final String nameKey;
    private final String descriptionKey;

    private MekanismProtectionSubmodule(
            String id,
            String mekanismDamageType,
            String nameKey,
            String descriptionKey) {
        this.id = id;
        this.damageType = ResourceKey.create(
                Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath("mekanism", mekanismDamageType));
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
    }

    @Override
    public String id() {
        return id;
    }

    public ResourceKey<DamageType> damageType() {
        return damageType;
    }

    @Override
    public String nameKey() {
        return nameKey;
    }

    @Override
    public String descriptionKey() {
        return descriptionKey;
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int getMaxInstallAmount() {
        return 1;
    }

    @Override
    public int tickActive(@Nullable Player player, Dist dist, ItemStack armor) {
        if (this == RADIATION
                && dist == Dist.DEDICATED_SERVER
                && player instanceof ServerPlayer serverPlayer
                && ModList.get().isLoaded("mekanism")) {
            MekanismArmorIntegration.tickRadiationRegeneration(serverPlayer);
        }
        return 0;
    }
}
