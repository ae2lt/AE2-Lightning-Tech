package com.moakiee.ae2lt.item.railgun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.registry.ModDataComponents;

public record RailgunSettings(boolean terrainDestruction, boolean pvp, boolean soundEnabled) {

    public static final RailgunSettings DEFAULT = new RailgunSettings(false, false, true);

    public static final Codec<RailgunSettings> CODEC = RecordCodecBuilder.create(b -> b.group(
            Codec.BOOL.fieldOf("terrain").forGetter(RailgunSettings::terrainDestruction),
            Codec.BOOL.fieldOf("pvp").forGetter(RailgunSettings::pvp),
            Codec.BOOL.optionalFieldOf("sound", true).forGetter(RailgunSettings::soundEnabled))
            .apply(b, RailgunSettings::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RailgunSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RailgunSettings::terrainDestruction,
            ByteBufCodecs.BOOL, RailgunSettings::pvp,
            ByteBufCodecs.BOOL, RailgunSettings::soundEnabled,
            RailgunSettings::new);

    public static boolean soundEnabled(ItemStack stack) {
        return stack == null
                || stack.isEmpty()
                || stack.getOrDefault(ModDataComponents.RAILGUN_SETTINGS.get(), DEFAULT).soundEnabled();
    }

    public RailgunSettings withTerrain(boolean v) {
        return new RailgunSettings(v, this.pvp, this.soundEnabled);
    }

    public RailgunSettings withPvp(boolean v) {
        return new RailgunSettings(this.terrainDestruction, v, this.soundEnabled);
    }

    public RailgunSettings withSound(boolean v) {
        return new RailgunSettings(this.terrainDestruction, this.pvp, v);
    }
}
