package com.moakiee.ae2lt.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.stacks.GenericStack;
import appeng.api.crafting.PatternDetailsHelper;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.logic.WirelessOverflowQueue.Bucket;

/** Stable NBT codec and deferred pattern decoding for wireless overflow. */
final class WirelessOverflowPersistence {
    private static final String TAG_W_SEND_LIST = "WirelessSendList";
    private static final String TAG_W_SEND_CONN = "WirelessSendConn";
    private static final String TAG_WIRELESS_OVERFLOW = "ae2lt:wireless_overflow";
    private static final String TAG_OVERFLOW_PATTERNS = "patterns";
    private static final String TAG_OVERFLOW_PATTERN_ID = "id";
    private static final String TAG_OVERFLOW_PATTERN = "pattern";
    private static final String TAG_OVERFLOW_BUCKETS = "buckets";
    private static final String TAG_OVERFLOW_CONN = "conn";
    private static final String TAG_OVERFLOW_PID = "pid";
    private static final String TAG_OVERFLOW_IDX = "idx";
    private static final String TAG_OVERFLOW_REMAINING = "remaining";
    private static final String TAG_OVERFLOW_FALLBACK = "fallback";
    private static final String TAG_OVERFLOW_FACE = "ae2lt:face";
    private static final String TAG_OVERFLOW_COMPACT = "compact";

    private final Map<Integer, ItemStack> pendingPatternDefinitions =
            new HashMap<>();
    private final List<PendingBucketLoad> pendingBuckets = new ArrayList<>();

    void write(
            CompoundTag tag,
            HolderLookup.Provider registries,
            WirelessOverflowQueue overflow) {
        if (overflow.isEmpty()) {
            return;
        }

        var overflowTag = new CompoundTag();
        var patternList = new ListTag();
        var remappedIds = new HashMap<Integer, Short>();
        short nextWriteId = 0;

        for (var bucket : overflow.buckets()) {
            if (!bucket.compactMode) {
                continue;
            }
            int runtimeId = Short.toUnsignedInt(bucket.patternId);
            if (remappedIds.containsKey(runtimeId)) {
                continue;
            }
            var pattern = overflow.pattern(runtimeId);
            if (pattern == null) {
                continue;
            }

            short writeId = nextWriteId++;
            remappedIds.put(runtimeId, writeId);
            var patternTag = new CompoundTag();
            patternTag.putShort(TAG_OVERFLOW_PATTERN_ID, writeId);
            patternTag.put(
                    TAG_OVERFLOW_PATTERN,
                    pattern.getDefinition().toStack().saveOptional(registries));
            patternList.add(patternTag);
        }
        overflowTag.put(TAG_OVERFLOW_PATTERNS, patternList);

        var bucketList = new ListTag();
        for (var connection : overflow.connections()) {
            var bucket = overflow.get(connection);
            if (bucket == null) {
                continue;
            }
            var bucketTag = new CompoundTag();
            bucketTag.put(TAG_OVERFLOW_CONN, connection.toTag());
            bucketTag.putBoolean(TAG_OVERFLOW_COMPACT, bucket.compactMode);
            if (bucket.compactMode) {
                var remapped = remappedIds.get(
                        Short.toUnsignedInt(bucket.patternId));
                if (remapped == null) {
                    continue;
                }
                bucketTag.putShort(TAG_OVERFLOW_PID, remapped);
                bucketTag.putShort(TAG_OVERFLOW_IDX, bucket.stuckIndex);
                bucketTag.putLong(TAG_OVERFLOW_REMAINING, bucket.remaining);
            } else {
                bucketTag.put(
                        TAG_OVERFLOW_FALLBACK,
                        writeRoutedOverflow(bucket.fallback, registries));
            }
            bucketList.add(bucketTag);
        }
        overflowTag.put(TAG_OVERFLOW_BUCKETS, bucketList);
        tag.put(TAG_WIRELESS_OVERFLOW, overflowTag);
    }

    void read(CompoundTag tag, HolderLookup.Provider registries) {
        clear();
        if (!tag.contains(TAG_WIRELESS_OVERFLOW, Tag.TAG_COMPOUND)) {
            readLegacy(tag, registries);
            return;
        }

        var overflowTag = tag.getCompound(TAG_WIRELESS_OVERFLOW);
        var patterns = overflowTag.getList(
                TAG_OVERFLOW_PATTERNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < patterns.size(); i++) {
            var patternTag = patterns.getCompound(i);
            int id = Short.toUnsignedInt(
                    patternTag.getShort(TAG_OVERFLOW_PATTERN_ID));
            var stack = ItemStack.parseOptional(
                    registries, patternTag.getCompound(TAG_OVERFLOW_PATTERN));
            if (!stack.isEmpty()) {
                pendingPatternDefinitions.put(id, stack);
            }
        }

        var buckets = overflowTag.getList(
                TAG_OVERFLOW_BUCKETS, Tag.TAG_COMPOUND);
        for (int i = 0; i < buckets.size(); i++) {
            var bucketTag = buckets.getCompound(i);
            if (!bucketTag.contains(TAG_OVERFLOW_CONN, Tag.TAG_COMPOUND)) {
                continue;
            }
            var connection = WirelessConnection.fromTag(
                    bucketTag.getCompound(TAG_OVERFLOW_CONN));
            if (bucketTag.getBoolean(TAG_OVERFLOW_COMPACT)) {
                pendingBuckets.add(new PendingBucketLoad(
                        connection,
                        bucketTag.getShort(TAG_OVERFLOW_PID),
                        bucketTag.getShort(TAG_OVERFLOW_IDX),
                        bucketTag.getLong(TAG_OVERFLOW_REMAINING),
                        List.of(),
                        true));
                continue;
            }

            var fallback = readRoutedOverflow(
                    registries,
                    bucketTag.getList(
                            TAG_OVERFLOW_FALLBACK, Tag.TAG_COMPOUND));
            if (!fallback.isEmpty()) {
                pendingBuckets.add(new PendingBucketLoad(
                        connection, (short) 0, (short) 0, 0L,
                        fallback, false));
            }
        }
    }

    boolean finishLoad(
            Level level,
            long gameTick,
            WirelessOverflowQueue overflow,
            Function<WirelessConnection, WirelessConnection> connectionResolver,
            Consumer<WirelessConnection> restoredConnection) {
        if (pendingBuckets.isEmpty()) {
            return false;
        }
        if (!pendingPatternDefinitions.isEmpty() && level == null) {
            return false;
        }

        for (var entry : pendingPatternDefinitions.entrySet()) {
            var details = PatternDetailsHelper.decodePattern(
                    entry.getValue(), level);
            if (details != null) {
                overflow.restorePattern(entry.getKey(), details);
            }
        }

        for (var pending : pendingBuckets) {
            Bucket bucket;
            if (pending.compactMode()) {
                var pattern = overflow.pattern(
                        Short.toUnsignedInt(pending.patternId()));
                if (pattern == null || pending.remaining() <= 0L) {
                    continue;
                }
                var inputs = pattern.getInputs();
                if (pending.stuckIndex() < 0
                        || pending.stuckIndex() >= inputs.length) {
                    continue;
                }
                bucket = Bucket.compact(
                        pending.patternId(),
                        pending.stuckIndex(),
                        pending.remaining());
            } else {
                if (pending.fallback().isEmpty()) {
                    continue;
                }
                bucket = Bucket.routedFallback(
                        pending.patternId(), pending.fallback());
            }
            var connection = connectionResolver.apply(pending.connection());
            overflow.restoreBucket(connection, bucket, gameTick);
            restoredConnection.accept(connection);
        }

        clear();
        return true;
    }

    void clear() {
        pendingPatternDefinitions.clear();
        pendingBuckets.clear();
    }

    static ListTag writeRoutedOverflow(
            RoutedPatternOverflow overflow,
            HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var entry : overflow.snapshot()) {
            var stackTag = GenericStack.writeTag(registries, entry.stack());
            if (entry.face() != null) {
                stackTag.putByte(
                        TAG_OVERFLOW_FACE,
                        (byte) entry.face().get3DDataValue());
            }
            list.add(stackTag);
        }
        return list;
    }

    static List<RoutedPatternOverflow.Entry> readRoutedOverflow(
            HolderLookup.Provider registries,
            ListTag list) {
        var entries = new ArrayList<RoutedPatternOverflow.Entry>(list.size());
        for (int i = 0; i < list.size(); i++) {
            var stackTag = list.getCompound(i);
            var stack = GenericStack.readTag(registries, stackTag);
            if (stack == null || stack.amount() <= 0L) {
                continue;
            }

            Direction face = null;
            if (stackTag.contains(TAG_OVERFLOW_FACE, Tag.TAG_BYTE)) {
                int faceId = stackTag.getByte(TAG_OVERFLOW_FACE);
                if (faceId >= 0 && faceId < Direction.values().length) {
                    face = Direction.from3DDataValue(faceId);
                }
            }
            entries.add(new RoutedPatternOverflow.Entry(face, stack));
        }
        return entries;
    }

    private void readLegacy(
            CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(TAG_W_SEND_LIST, Tag.TAG_LIST)
                || !tag.contains(TAG_W_SEND_CONN, Tag.TAG_COMPOUND)) {
            return;
        }
        var fallback = readGenericStackList(
                registries,
                tag.getList(TAG_W_SEND_LIST, Tag.TAG_COMPOUND));
        if (!fallback.isEmpty()) {
            pendingBuckets.add(new PendingBucketLoad(
                    WirelessConnection.fromTag(
                            tag.getCompound(TAG_W_SEND_CONN)),
                    (short) 0,
                    (short) 0,
                    0L,
                    toUnroutedOverflow(fallback),
                    false));
        }
    }

    private static List<RoutedPatternOverflow.Entry> toUnroutedOverflow(
            List<GenericStack> stacks) {
        var entries = new ArrayList<RoutedPatternOverflow.Entry>(stacks.size());
        for (var stack : stacks) {
            entries.add(new RoutedPatternOverflow.Entry(null, stack));
        }
        return entries;
    }

    private static List<GenericStack> readGenericStackList(
            HolderLookup.Provider registries, ListTag list) {
        var stacks = new ArrayList<GenericStack>(list.size());
        for (int i = 0; i < list.size(); i++) {
            var stack = GenericStack.readTag(registries, list.getCompound(i));
            if (stack != null && stack.amount() > 0L) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private record PendingBucketLoad(
            WirelessConnection connection,
            short patternId,
            short stuckIndex,
            long remaining,
            List<RoutedPatternOverflow.Entry> fallback,
            boolean compactMode) {
    }
}
