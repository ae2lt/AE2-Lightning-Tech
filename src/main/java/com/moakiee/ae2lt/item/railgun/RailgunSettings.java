package com.moakiee.ae2lt.item.railgun;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.registry.ModDataComponents;

public record RailgunSettings(
        boolean terrainDestruction,
        boolean pvp,
        boolean soundEnabled,
        RailgunExecutionMode executionMode,
        boolean chargedSplash,
        boolean chainDamage) {

    public static final RailgunSettings DEFAULT = new RailgunSettings(
            false, false, true, RailgunExecutionMode.NORMAL, true, true);

    private static final Codec<RailgunSettings> CURRENT_CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.BOOL.fieldOf("terrain").forGetter(RailgunSettings::terrainDestruction),
            Codec.BOOL.fieldOf("pvp").forGetter(RailgunSettings::pvp),
            Codec.BOOL.optionalFieldOf("sound", true).forGetter(RailgunSettings::soundEnabled),
            RailgunExecutionMode.CODEC.fieldOf("execution_mode")
                    .forGetter(RailgunSettings::executionMode),
            Codec.BOOL.optionalFieldOf("charged_splash", true)
                    .forGetter(RailgunSettings::chargedSplash),
            Codec.BOOL.optionalFieldOf("chain_damage", true).forGetter(RailgunSettings::chainDamage))
            .apply(b, RailgunSettings::new));

    private static final Codec<LegacySettings> LEGACY_CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.BOOL.fieldOf("terrain").forGetter(LegacySettings::terrainDestruction),
            Codec.BOOL.fieldOf("pvp").forGetter(LegacySettings::pvp),
            Codec.BOOL.optionalFieldOf("sound", true).forGetter(LegacySettings::soundEnabled),
            Codec.BOOL.optionalFieldOf("force_overload_removal", false)
                    .forGetter(LegacySettings::forceOverloadRemoval),
            Codec.BOOL.optionalFieldOf("charged_splash", true)
                    .forGetter(LegacySettings::chargedSplash),
            Codec.BOOL.optionalFieldOf("chain_damage", true).forGetter(LegacySettings::chainDamage))
            .apply(b, LegacySettings::new));

    /**
     * The alternative codec keeps railguns saved before the three-state setting usable:
     * legacy false becomes NORMAL and legacy true remains FORCED.
     */
    public static final Codec<RailgunSettings> CODEC = Codec.either(CURRENT_CODEC, LEGACY_CODEC)
            .xmap(value -> value.map(settings -> settings, LegacySettings::upgrade), Either::left);

    // 1.20.1 has no StreamCodec; the component shim persists this codec in stack NBT.

    /** Source-compatible constructor for callers using the former boolean setting. */
    public RailgunSettings(
            boolean terrainDestruction,
            boolean pvp,
            boolean soundEnabled,
            boolean forceOverloadRemoval,
            boolean chargedSplash,
            boolean chainDamage) {
        this(
                terrainDestruction,
                pvp,
                soundEnabled,
                forceOverloadRemoval ? RailgunExecutionMode.FORCED : RailgunExecutionMode.NORMAL,
                chargedSplash,
                chainDamage);
    }

    public static boolean soundEnabled(ItemStack stack) {
        return stack == null
                || stack.isEmpty()
                || ModDataComponents.RAILGUN_SETTINGS.getOrDefault(stack, DEFAULT).soundEnabled();
    }

    public RailgunSettings withTerrain(boolean v) {
        return new RailgunSettings(
                v, this.pvp, this.soundEnabled, this.executionMode, this.chargedSplash,
                this.chainDamage);
    }

    public RailgunSettings withPvp(boolean v) {
        return new RailgunSettings(
                this.terrainDestruction, v, this.soundEnabled, this.executionMode,
                this.chargedSplash, this.chainDamage);
    }

    public RailgunSettings withSound(boolean v) {
        return new RailgunSettings(
                this.terrainDestruction, this.pvp, v, this.executionMode, this.chargedSplash,
                this.chainDamage);
    }

    /** Legacy adapter retained for source compatibility. */
    public RailgunSettings withForceOverloadRemoval(boolean v) {
        return withExecutionMode(v ? RailgunExecutionMode.FORCED : RailgunExecutionMode.NORMAL);
    }

    /** Legacy adapter retained for source compatibility. */
    public boolean forceOverloadRemoval() {
        return this.executionMode.forcesRemoval();
    }

    public RailgunSettings withExecutionMode(RailgunExecutionMode mode) {
        return new RailgunSettings(
                this.terrainDestruction, this.pvp, this.soundEnabled, mode, this.chargedSplash,
                this.chainDamage);
    }

    public RailgunSettings withChargedSplash(boolean v) {
        return new RailgunSettings(
                this.terrainDestruction, this.pvp, this.soundEnabled, this.executionMode, v,
                this.chainDamage);
    }

    public RailgunSettings withChainDamage(boolean v) {
        return new RailgunSettings(
                this.terrainDestruction, this.pvp, this.soundEnabled, this.executionMode,
                this.chargedSplash, v);
    }

    /** Player targeting requires both this railgun's opt-in and server permission. */
    public boolean allowsPlayerTargets(boolean serverAllowsPvp) {
        return this.pvp && serverAllowsPvp;
    }

    private record LegacySettings(
            boolean terrainDestruction,
            boolean pvp,
            boolean soundEnabled,
            boolean forceOverloadRemoval,
            boolean chargedSplash,
            boolean chainDamage) {

        private RailgunSettings upgrade() {
            return new RailgunSettings(
                    terrainDestruction,
                    pvp,
                    soundEnabled,
                    forceOverloadRemoval ? RailgunExecutionMode.FORCED : RailgunExecutionMode.NORMAL,
                    chargedSplash,
                    chainDamage);
        }
    }
}
