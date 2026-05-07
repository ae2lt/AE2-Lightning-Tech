package com.moakiee.ae2lt.util;

import java.util.function.BiFunction;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class SavedDataCodecs {
    private SavedDataCodecs() {
    }

    public static <T extends SavedData> SavedDataType.Factory<Codec<T>> codecFactory(
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            BiFunction<T, HolderLookup.Provider, CompoundTag> saver) {
        return level -> create(level != null ? level.registryAccess() : RegistryAccess.EMPTY, loader, saver);
    }

    public static <T extends SavedData> Codec<T> create(
            HolderLookup.Provider registries,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            BiFunction<T, HolderLookup.Provider, CompoundTag> saver) {
        return new Codec<>() {
            @Override
            public <O> DataResult<O> encode(T data, DynamicOps<O> ops, O prefix) {
                O encoded = NbtOps.INSTANCE.convertTo(ops, saver.apply(data, registries));
                return ops.getMap(encoded).flatMap(map -> ops.mergeToMap(prefix, map));
            }

            @Override
            public <O> DataResult<Pair<T, O>> decode(DynamicOps<O> ops, O input) {
                Tag tag = ops.convertTo(NbtOps.INSTANCE, input);
                if (tag instanceof CompoundTag compound) {
                    return DataResult.success(Pair.of(loader.apply(compound, registries), ops.empty()));
                }
                return DataResult.error(() -> "Expected compound tag, got " + tag.getType().getName());
            }
        };
    }
}
