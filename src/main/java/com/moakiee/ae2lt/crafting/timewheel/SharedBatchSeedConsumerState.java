package com.moakiee.ae2lt.crafting.timewheel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Job-local record of loop consumers that have successfully entered shared-input batch mode.
 *
 * <p>A shared-input batch needs one physical seed for the whole batch. Once a consumer has
 * successfully used that path, its protected loop seed is sufficient for later batches and final
 * outputs must no longer be retained in proportion to the number of undispatched copies.
 */
final class SharedBatchSeedConsumerState {
    private static final String TAG_CONSUMERS = "sharedBatchSeedConsumers";
    private static final String TAG_CONSUMER = "consumer";

    private final Set<UUID> consumers = new HashSet<>();

    void recordSuccessfulBatch(UUID consumerId) {
        if (consumerId != null) {
            consumers.add(consumerId);
        }
    }

    long pendingDemand(UUID consumerId, long perCopy, long remainingCopies) {
        if (perCopy <= 0 || remainingCopies <= 0 || consumers.contains(consumerId)) {
            return 0L;
        }
        if (perCopy > Long.MAX_VALUE / remainingCopies) {
            return Long.MAX_VALUE;
        }
        return perCopy * remainingCopies;
    }

    void readFromNBT(CompoundTag data) {
        consumers.clear();
        var tags = data.getList(TAG_CONSUMERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < tags.size(); i++) {
            var entry = tags.getCompound(i);
            if (entry.hasUUID(TAG_CONSUMER)) {
                consumers.add(entry.getUUID(TAG_CONSUMER));
            }
        }
    }

    void writeToNBT(CompoundTag data) {
        if (consumers.isEmpty()) {
            data.remove(TAG_CONSUMERS);
            return;
        }

        var tags = new ListTag();
        var orderedConsumers = new ArrayList<>(consumers);
        orderedConsumers.sort(UUID::compareTo);
        for (var consumerId : orderedConsumers) {
            var entry = new CompoundTag();
            entry.putUUID(TAG_CONSUMER, consumerId);
            tags.add(entry);
        }
        data.put(TAG_CONSUMERS, tags);
    }
}
