package com.moakiee.ae2lt.logic.research;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record ResearchNoteData(
        UUID ritualSeed,
        RitualGoal goal,
        List<ResourceLocation> recipeItems,
        List<String> descriptionKeys,
        boolean consumed) {

    public static final String TAG_RITUAL_SEED = "RitualSeed";
    public static final String TAG_GOAL = "Goal";
    public static final String TAG_RECIPE_ITEMS = "RecipeItems";
    public static final String TAG_DESCRIPTIONS = "Descriptions";
    public static final String TAG_CONSUMED = "Consumed";
    /** 仅存在于空白笔记(未生成前)上,铁砧调制后写入。玩家右键派生时被读取并强制覆盖随机 goal。 */
    public static final String TAG_FORCED_GOAL = "ForcedGoal";

    public static boolean isBlank(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return !tag.contains(TAG_GOAL, Tag.TAG_STRING);
    }

    public static @Nullable RitualGoal readForcedGoal(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_FORCED_GOAL, Tag.TAG_STRING)) {
            return null;
        }
        return RitualGoal.fromName(tag.getString(TAG_FORCED_GOAL));
    }

    public static void writeForcedGoal(ItemStack stack, RitualGoal goal) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG_FORCED_GOAL, goal.name()));
    }

    public static boolean isConsumed(ItemStack stack) {
        ResearchNoteData data = read(stack);
        return data != null && data.consumed();
    }

    public static @Nullable ResearchNoteData read(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_GOAL, Tag.TAG_STRING)) {
            return null;
        }

        RitualGoal goal = RitualGoal.fromName(tag.getString(TAG_GOAL));
        if (goal == null) {
            return null;
        }

        UUID ritualSeed;
        try {
            ritualSeed = UUID.fromString(tag.getString(TAG_RITUAL_SEED));
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        List<ResourceLocation> recipeItems = readResourceLocationList(tag, TAG_RECIPE_ITEMS);
        List<String> descriptionKeys = readStringList(tag, TAG_DESCRIPTIONS);
        if (recipeItems.size() != 9 || descriptionKeys.size() != recipeItems.size()) {
            return null;
        }

        return new ResearchNoteData(ritualSeed, goal, List.copyOf(recipeItems), List.copyOf(descriptionKeys),
                tag.getBoolean(TAG_CONSUMED));
    }

    public void writeTo(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(TAG_RITUAL_SEED, ritualSeed.toString());
            tag.putString(TAG_GOAL, goal.name());
            tag.put(TAG_RECIPE_ITEMS, writeResourceLocationList(recipeItems));
            tag.put(TAG_DESCRIPTIONS, writeStringList(descriptionKeys));
            tag.putBoolean(TAG_CONSUMED, consumed);
        });
    }

    public ResearchNoteData withConsumed(boolean consumed) {
        return new ResearchNoteData(ritualSeed, goal, recipeItems, descriptionKeys, consumed);
    }

    public String shortCode() {
        return ritualSeed.toString().replace("-", "").substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private static List<ResourceLocation> readResourceLocationList(CompoundTag tag, String key) {
        List<ResourceLocation> values = new ArrayList<>();
        ListTag listTag = tag.getList(key, Tag.TAG_STRING);
        for (Tag element : listTag) {
            if (!(element instanceof StringTag stringTag)) {
                continue;
            }

            ResourceLocation id = ResourceLocation.tryParse(stringTag.getAsString());
            if (id != null) {
                values.add(id);
            }
        }
        return values;
    }

    private static List<String> readStringList(CompoundTag tag, String key) {
        List<String> values = new ArrayList<>();
        ListTag listTag = tag.getList(key, Tag.TAG_STRING);
        for (Tag element : listTag) {
            if (element instanceof StringTag stringTag) {
                values.add(stringTag.getAsString());
            }
        }
        return values;
    }

    private static ListTag writeResourceLocationList(List<ResourceLocation> values) {
        ListTag listTag = new ListTag();
        for (ResourceLocation value : values) {
            listTag.add(StringTag.valueOf(value.toString()));
        }
        return listTag;
    }

    private static ListTag writeStringList(List<String> values) {
        ListTag listTag = new ListTag();
        for (String value : values) {
            listTag.add(StringTag.valueOf(value));
        }
        return listTag;
    }
}
