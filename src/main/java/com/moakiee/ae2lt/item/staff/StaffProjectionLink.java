package com.moakiee.ae2lt.item.staff;

import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

/** Opaque identity only. No tool, module, enchantment or container payload. */
public record StaffProjectionLink(UUID owner, UUID tool, UUID generation, int slot) {
    public static final Codec<StaffProjectionLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(StaffProjectionLink::owner),
            UUIDUtil.CODEC.fieldOf("tool").forGetter(StaffProjectionLink::tool),
            UUIDUtil.CODEC.fieldOf("generation").forGetter(StaffProjectionLink::generation),
            Codec.intRange(0, 40).fieldOf("slot").forGetter(StaffProjectionLink::slot)).apply(i, StaffProjectionLink::new));
}
