package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import com.google.gson.JsonObject;
import java.util.Iterator;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.util.RecipeSerializationHelper;

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

    static CrystalCatalyzerOutput fromJson(JsonObject json) {
        if (json.has("tag")) {
            return new OfTag(
                    TagKey.create(Registries.ITEM, new ResourceLocation(GsonHelper.getAsString(json, "tag"))),
                    GsonHelper.getAsInt(json, "count", 1));
        }
        return new OfItem(RecipeSerializationHelper.itemStackFromJson(json));
    }

    static void encode(FriendlyByteBuf buf, CrystalCatalyzerOutput output) {
        if (output instanceof OfItem item) {
            buf.writeBoolean(false);
            buf.writeItem(item.stack());
            return;
        }
        if (output instanceof OfTag tag) {
            buf.writeBoolean(true);
            buf.writeResourceLocation(tag.tag().location());
            buf.writeInt(tag.count());
            return;
        }
        throw new IllegalStateException("Unknown crystal catalyzer output type: " + output);
    }

    static CrystalCatalyzerOutput decode(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            ResourceLocation tagId = buf.readResourceLocation();
            int count = buf.readInt();
            return new OfTag(TagKey.create(Registries.ITEM, tagId), count);
        }
        ItemStack stack = buf.readItem();
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
