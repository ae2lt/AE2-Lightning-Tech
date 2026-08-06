package com.moakiee.ae2lt.api.lightning;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Public, addon-facing tier of lightning energy stored on an AE2 Lightning Tech grid.
 *
 * <p>The two constants and their {@linkplain #getSerializedName() serialized names}
 * (<code>"high_voltage"</code> / <code>"extreme_high_voltage"</code>) are part of the
 * frozen API contract: they are persisted in NBT, recipes, and packets and MUST NOT
 * change. Adding new constants is a breaking change.
 *
 * <p>Conversion between this enum and the internal
 * <code>com.moakiee.ae2lt.me.key.LightningKey.Tier</code> lives on
 * {@code LightningKey} (see {@code LightningKey.toApiTier(...)} /
 * {@code LightningKey.fromApiTier(...)}). The API package itself does not depend on
 * any internal implementation type.
 */
public enum LightningTier implements StringRepresentable {
    HIGH_VOLTAGE("high_voltage"),
    EXTREME_HIGH_VOLTAGE("extreme_high_voltage");

    public static final Codec<LightningTier> CODEC = StringRepresentable.fromEnum(LightningTier::values);

    public static final StreamCodec<RegistryFriendlyByteBuf, LightningTier> STREAM_CODEC =
            StreamCodec.of(
                    (buf, tier) -> buf.writeByte(tier.ordinal()),
                    buf -> fromOrdinal(buf.readByte()));

    private final String serializedName;

    LightningTier(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    /**
     * Decode a tier from its serialized name. Unknown names degrade safely to
     * {@link #HIGH_VOLTAGE} so future-facing or malformed data does not hard-fail.
     */
    public static LightningTier fromSerializedName(String serializedName) {
        for (LightningTier value : values()) {
            if (value.serializedName.equals(serializedName)) {
                return value;
            }
        }
        return HIGH_VOLTAGE;
    }

    /**
     * Decode a tier from a network ordinal byte. Mirrors
     * {@code LightningKey.Tier.fromOrdinal} so addons can use a single ordinal wire
     * format compatible with this mod's existing packets.
     *
     * <p>Unknown ordinal values intentionally degrade to {@link #HIGH_VOLTAGE}.
     */
    public static LightningTier fromOrdinal(int ordinal) {
        return ordinal == EXTREME_HIGH_VOLTAGE.ordinal() ? EXTREME_HIGH_VOLTAGE : HIGH_VOLTAGE;
    }
}
