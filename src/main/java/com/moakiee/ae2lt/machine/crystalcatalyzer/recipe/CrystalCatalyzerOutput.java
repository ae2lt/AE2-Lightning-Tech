package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import java.util.Iterator;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 水晶催化器配方的产物声明。
 *
 * <p>支持两种 JSON 写法：</p>
 * <ul>
 *     <li>{@code {"id": "modid:item", "count": N}} —— 直接指定一个物品。</li>
 *     <li>{@code {"tag": "modid:path", "count": N}} —— 引用一个物品 tag，运行时解析为该 tag 内的第一个物品。
 *         若 tag 为空（即装包内没有任何物品被 tag 命中），配方等同于"输出为空"，会被机器和 JEI 跳过。</li>
 * </ul>
 *
 * <p>采用 tag 形式可以让多模组装包里"任何一个 amethyst 粉物品"都能被识别，无需为每个潜在模组写一份配方。</p>
 */
public sealed interface CrystalCatalyzerOutput
        permits CrystalCatalyzerOutput.OfItem, CrystalCatalyzerOutput.OfTag {

    Codec<CrystalCatalyzerOutput> CODEC = Codec.either(OfTag.CODEC, ItemStack.STRICT_CODEC)
            .xmap(
                    either -> either.map(tag -> (CrystalCatalyzerOutput) tag, OfItem::new),
                    output -> switch (output) {
                        case OfTag tag -> Either.left(tag);
                        case OfItem item -> Either.right(item.stack());
                    });

    StreamCodec<RegistryFriendlyByteBuf, CrystalCatalyzerOutput> STREAM_CODEC =
            StreamCodec.of(CrystalCatalyzerOutput::encode, CrystalCatalyzerOutput::decode);

    /**
     * Resolve to a concrete {@link ItemStack}. Returns {@link ItemStack#EMPTY} when this is a tag
     * output and the tag is empty in the current registry/datapack state.
     */
    ItemStack resolve();

    int count();

    static CrystalCatalyzerOutput ofItem(ItemStack stack) {
        return new OfItem(stack.copy());
    }

    static CrystalCatalyzerOutput ofTag(TagKey<Item> tag, int count) {
        return new OfTag(tag, count);
    }

    private static void encode(RegistryFriendlyByteBuf buf, CrystalCatalyzerOutput output) {
        switch (output) {
            case OfItem item -> {
                buf.writeBoolean(false);
                ItemStack.STREAM_CODEC.encode(buf, item.stack());
            }
            case OfTag tag -> {
                buf.writeBoolean(true);
                buf.writeResourceLocation(tag.tag().location());
                ByteBufCodecs.VAR_INT.encode(buf, tag.count());
            }
        }
    }

    private static CrystalCatalyzerOutput decode(RegistryFriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            ResourceLocation tagId = buf.readResourceLocation();
            int count = ByteBufCodecs.VAR_INT.decode(buf);
            return new OfTag(TagKey.create(BuiltInRegistries.ITEM.key(), tagId), count);
        }
        ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
        return new OfItem(stack);
    }

    record OfItem(ItemStack stack) implements CrystalCatalyzerOutput {
        public OfItem {
            stack = stack.copy();
            if (stack.isEmpty()) {
                throw new IllegalArgumentException("output stack cannot be empty");
            }
        }

        @Override
        public ItemStack resolve() {
            return stack.copy();
        }

        @Override
        public int count() {
            return stack.getCount();
        }
    }

    record OfTag(TagKey<Item> tag, int count) implements CrystalCatalyzerOutput {
        public static final Codec<OfTag> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        TagKey.codec(BuiltInRegistries.ITEM.key()).fieldOf("tag").forGetter(OfTag::tag),
                        Codec.INT.optionalFieldOf("count", 1).forGetter(OfTag::count))
                .apply(instance, OfTag::new));

        public OfTag {
            if (count <= 0) {
                throw new IllegalArgumentException("tag output count must be positive");
            }
        }

        @Override
        public ItemStack resolve() {
            HolderSet.Named<Item> holders = BuiltInRegistries.ITEM.getTag(tag).orElse(null);
            if (holders == null || holders.size() == 0) {
                return ItemStack.EMPTY;
            }
            Iterator<Holder<Item>> iterator = holders.iterator();
            if (!iterator.hasNext()) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(iterator.next().value(), count);
        }
    }
}
