package com.moakiee.ae2lt.me.key;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

public final class LightningKeyType extends AEKeyType {
    public static final LightningKeyType INSTANCE = new LightningKeyType();

    private LightningKeyType() {
        super(LightningKey.TYPE_ID, LightningKey.class, Component.translatable("key_type.ae2lt.lightning"));
    }

    @Override
    public int getAmountPerByte() {
        return 1;
    }

    @Override
    public int getAmountPerOperation() {
        return 1;
    }

    @Override
    public int getAmountPerUnit() {
        return 1;
    }

    @Override
    public @Nullable AEKey readFromPacket(FriendlyByteBuf input) {
        return LightningKey.fromOrdinal(input.readByte());
    }

    @Override
    public @Nullable AEKey loadKeyFromTag(CompoundTag tag) {
        return LightningKey.of(LightningKey.Tier.fromSerializedName(tag.getString("tier")));
    }
}
