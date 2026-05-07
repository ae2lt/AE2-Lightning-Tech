package com.moakiee.ae2lt.lightning;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class LightningTransformPlan {
    private final List<Consumption> consumptions;
    private final Vec3 spawnPosition;
    private final int totalConsumedCount;

    private LightningTransformPlan(List<Consumption> consumptions, Vec3 spawnPosition, int totalConsumedCount) {
        this.consumptions = List.copyOf(consumptions);
        this.spawnPosition = Objects.requireNonNull(spawnPosition, "spawnPosition");
        this.totalConsumedCount = totalConsumedCount;
    }

    public static LightningTransformPlan fromGroupCounts(
            List<LightningTransformRecipeInput.GroupedStack> groupedStacks,
            int[] groupConsumptions) {
        List<Consumption> consumptions = new ArrayList<>();
        double totalX = 0.0D;
        double totalY = 0.0D;
        double totalZ = 0.0D;
        int totalConsumedCount = 0;

        for (int groupIndex = 0; groupIndex < groupedStacks.size(); groupIndex++) {
            int remaining = groupConsumptions[groupIndex];
            if (remaining <= 0) {
                continue;
            }

            for (LightningTransformRecipeInput.ParticipantStack participant : groupedStacks.get(groupIndex).participants()) {
                if (remaining <= 0) {
                    break;
                }

                int take = Math.min(remaining, participant.count());
                if (take <= 0) {
                    continue;
                }

                consumptions.add(new Consumption(participant.itemEntity(), take, participant.position()));
                totalX += participant.position().x * take;
                totalY += participant.position().y * take;
                totalZ += participant.position().z * take;
                totalConsumedCount += take;
                remaining -= take;
            }

            if (remaining > 0) {
                throw new IllegalStateException("Recipe allocation exceeded grouped stack snapshot");
            }
        }

        if (totalConsumedCount <= 0) {
            throw new IllegalStateException("Lightning transform plan must consume at least one item");
        }

        Vec3 spawnPosition = new Vec3(
                totalX / totalConsumedCount,
                totalY / totalConsumedCount + 0.15D,
                totalZ / totalConsumedCount);
        return new LightningTransformPlan(consumptions, spawnPosition, totalConsumedCount);
    }

    public Vec3 spawnPosition() {
        return spawnPosition;
    }

    public int totalConsumedCount() {
        return totalConsumedCount;
    }

    public boolean consumeInputs(long gameTime) {
        if (!isStillValid(gameTime)) {
            return false;
        }

        for (Consumption consumption : consumptions) {
            ItemEntity itemEntity = consumption.itemEntity();
            ItemStack stack = itemEntity.getItem();
            stack.shrink(consumption.count());
            if (stack.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(stack);
            }
        }

        return true;
    }

    public void applyTransformLocks(long gameTime) {
        for (Consumption consumption : consumptions) {
            ItemEntity itemEntity = consumption.itemEntity();
            if (itemEntity.isAlive() && !itemEntity.getItem().isEmpty()) {
                ProtectedItemEntityHelper.applyTransformLock(itemEntity, gameTime);
            }
        }
    }

    private boolean isStillValid(long gameTime) {
        for (Consumption consumption : consumptions) {
            ItemEntity itemEntity = consumption.itemEntity();
            if (!itemEntity.isAlive()) {
                return false;
            }

            if (!ProtectedItemEntityHelper.canParticipateInTransform(itemEntity, gameTime)) {
                return false;
            }

            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty() || stack.getCount() < consumption.count()) {
                return false;
            }
        }

        return true;
    }

    public record Consumption(ItemEntity itemEntity, int count, Vec3 originalPosition) {
        public Consumption {
            Objects.requireNonNull(itemEntity, "itemEntity");
            Objects.requireNonNull(originalPosition, "originalPosition");
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive");
            }
        }
    }
}
