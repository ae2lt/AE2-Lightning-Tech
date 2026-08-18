package com.moakiee.ae2lt.celestweave.phase;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/** Identifies the authoritative armor mirrored by one phase-lock projection. */
public record PhaseLockProjectionLink(UUID armorId, long update) {
    public static final Codec<PhaseLockProjectionLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("armor_id").forGetter(PhaseLockProjectionLink::armorId),
            Codec.LONG.fieldOf("update").forGetter(PhaseLockProjectionLink::update))
            .apply(instance, PhaseLockProjectionLink::new));
}
