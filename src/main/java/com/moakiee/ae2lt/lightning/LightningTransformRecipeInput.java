package com.moakiee.ae2lt.lightning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.phys.Vec3;

public final class LightningTransformRecipeInput implements RecipeInput {
    private final List<GroupedStack> groupedStacks;
    private final List<ItemStack> displayStacks;

    private LightningTransformRecipeInput(List<GroupedStack> groupedStacks) {
        this.groupedStacks = List.copyOf(groupedStacks);
        this.displayStacks = this.groupedStacks.stream()
                .map(GroupedStack::asDisplayStack)
                .toList();
    }

    public static LightningTransformRecipeInput fromEntities(Collection<ItemEntity> itemEntities) {
        Map<ItemStackKey, MutableGroup> grouped = new LinkedHashMap<>();
        for (ItemEntity itemEntity : itemEntities) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) {
                continue;
            }

            ItemStackKey key = new ItemStackKey(stack);
            MutableGroup group = grouped.computeIfAbsent(key, ignored -> new MutableGroup(stack.copyWithCount(1)));
            group.add(itemEntity, stack.getCount(), itemEntity.position());
        }

        return new LightningTransformRecipeInput(grouped.values().stream()
                .map(MutableGroup::freeze)
                .toList());
    }

    public List<GroupedStack> groupedStacks() {
        return groupedStacks;
    }

    @Override
    public ItemStack getItem(int index) {
        return displayStacks.get(index);
    }

    @Override
    public int size() {
        return displayStacks.size();
    }

    public static final class GroupedStack {
        private final ItemStack stack;
        private final int totalCount;
        private final List<ParticipantStack> participants;

        private GroupedStack(ItemStack stack, int totalCount, List<ParticipantStack> participants) {
            this.stack = stack.copyWithCount(1);
            this.totalCount = totalCount;
            this.participants = List.copyOf(participants);
        }

        public ItemStack stack() {
            return stack;
        }

        public int totalCount() {
            return totalCount;
        }

        public List<ParticipantStack> participants() {
            return participants;
        }

        public ItemStack asDisplayStack() {
            return stack.copyWithCount(totalCount);
        }
    }

    public static final class ParticipantStack {
        private final ItemEntity itemEntity;
        private final int count;
        private final Vec3 position;

        private ParticipantStack(ItemEntity itemEntity, int count, Vec3 position) {
            this.itemEntity = Objects.requireNonNull(itemEntity, "itemEntity");
            this.count = count;
            this.position = Objects.requireNonNull(position, "position");
        }

        public ItemEntity itemEntity() {
            return itemEntity;
        }

        public int count() {
            return count;
        }

        public Vec3 position() {
            return position;
        }
    }

    private static final class MutableGroup {
        private final ItemStack stack;
        private final List<ParticipantStack> participants = new ArrayList<>();
        private int totalCount;

        private MutableGroup(ItemStack stack) {
            this.stack = stack;
        }

        private void add(ItemEntity itemEntity, int count, Vec3 position) {
            participants.add(new ParticipantStack(itemEntity, count, position));
            totalCount += count;
        }

        private GroupedStack freeze() {
            return new GroupedStack(stack, totalCount, participants);
        }
    }

    private static final class ItemStackKey {
        private final ItemStack stack;
        private final int hash;

        private ItemStackKey(ItemStack stack) {
            this.stack = stack.copyWithCount(1);
            this.hash = ItemStack.hashItemAndComponents(this.stack);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof ItemStackKey itemStackKey)) {
                return false;
            }

            return ItemStack.isSameItemSameComponents(this.stack, itemStackKey.stack);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
