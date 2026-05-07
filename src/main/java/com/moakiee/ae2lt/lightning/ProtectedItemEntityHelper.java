package com.moakiee.ae2lt.lightning;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;

import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;

public final class ProtectedItemEntityHelper {
    private static final String PROTECT_UNTIL_TAG = "ae2lt.lightning_transform.protect_until";
    private static final String NO_TRANSFORM_UNTIL_TAG = "ae2lt.lightning_transform.no_transform_until";
    private static final String TRANSFORM_LOCK_UNTIL_TAG = "ae2lt.lightning_transform.lock_until";

    private static volatile Set<Item> fireproofItems;

    private static Set<Item> getFireproofItems() {
        if (fireproofItems == null) {
            fireproofItems = Set.of(
                    ModItems.OVERLOAD_ALLOY_BLANK.get(),
                    ModItems.OVERLOAD_CRYSTAL_DUST.get(),
                    ModItems.UNOVERLOADED_CIRCUIT_BOARD.get(),
                    ModItems.OVERLOAD_PROCESSOR.get(),
                    ModItems.OVERLOAD_CRYSTAL.get(),
                    ModItems.OVERLOAD_ALLOY.get(),
                    ModItems.OVERLOAD_CIRCUIT_BOARD.get(),
                    ModItems.RESEARCH_NOTE.get(),
                    ModItems.CHARRED_RITUAL_FRAGMENT.get(),
                    ModBlocks.OVERLOAD_CRYSTAL_BLOCK.get().asItem(),
                    ModItems.OVERLOAD_SINGULARITY.get(),
                    ModItems.LIGHTNING_COLLAPSE_MATRIX.get());
        }
        return fireproofItems;
    }

    public static boolean isFireproofItem(ItemEntity itemEntity) {
        return getFireproofItems().contains(itemEntity.getItem().getItem());
    }

    private ProtectedItemEntityHelper() {
    }

    public static void applyOutputProtection(ItemEntity itemEntity, long gameTime) {
        CompoundTag data = itemEntity.getPersistentData();
        long protectUntil = gameTime + LightningTransformRules.OUTPUT_PROTECTION_TICKS;
        putMax(data, PROTECT_UNTIL_TAG, protectUntil);
        putMax(data, NO_TRANSFORM_UNTIL_TAG, protectUntil);
        data.remove(TRANSFORM_LOCK_UNTIL_TAG);
        itemEntity.clearFire();
        itemEntity.setRemainingFireTicks(0);
    }

    public static void applyTransformLock(ItemEntity itemEntity, long gameTime) {
        CompoundTag data = itemEntity.getPersistentData();
        putMax(data, TRANSFORM_LOCK_UNTIL_TAG, gameTime + LightningTransformRules.PARTICIPANT_LOCK_TICKS);
        putMax(data, PROTECT_UNTIL_TAG, gameTime + LightningTransformRules.OUTPUT_PROTECTION_TICKS);
        itemEntity.clearFire();
        itemEntity.setRemainingFireTicks(0);
    }

    public static boolean isProtectedItem(ItemEntity itemEntity) {
        return isProtectedItem(itemEntity, itemEntity.level().getGameTime());
    }

    public static boolean isProtectedItem(ItemEntity itemEntity, long gameTime) {
        return itemEntity.getPersistentData().getLong(PROTECT_UNTIL_TAG) > gameTime;
    }

    public static boolean canParticipateInTransform(ItemEntity itemEntity, long gameTime) {
        if (!itemEntity.isAlive() || itemEntity.getItem().isEmpty()) {
            return false;
        }

        CompoundTag data = itemEntity.getPersistentData();
        return data.getLong(PROTECT_UNTIL_TAG) <= gameTime
                && data.getLong(NO_TRANSFORM_UNTIL_TAG) <= gameTime
                && data.getLong(TRANSFORM_LOCK_UNTIL_TAG) <= gameTime;
    }

    public static boolean shouldIgnoreDamage(ItemEntity itemEntity, DamageSource damageSource) {
        if (!isLightningOrFireDamage(damageSource)) {
            return false;
        }

        if (isProtectedItem(itemEntity) || isFireproofItem(itemEntity)) {
            itemEntity.clearFire();
            itemEntity.setRemainingFireTicks(0);
            return true;
        }

        return false;
    }

    private static boolean isLightningOrFireDamage(DamageSource damageSource) {
        return damageSource.is(DamageTypes.LIGHTNING_BOLT)
                || damageSource.is(DamageTypes.IN_FIRE)
                || damageSource.is(DamageTypes.ON_FIRE)
                || damageSource.is(DamageTypes.LAVA)
                || damageSource.is(DamageTypeTags.IS_FIRE);
    }

    public static void tick(ItemEntity itemEntity) {
        if (itemEntity.level().isClientSide()) {
            return;
        }

        CompoundTag data = itemEntity.getPersistentData();
        if (!hasTimedState(data)) {
            return;
        }

        long gameTime = itemEntity.level().getGameTime();
        if (isProtectedItem(itemEntity, gameTime)) {
            itemEntity.clearFire();
            itemEntity.setRemainingFireTicks(0);
        }

        clearIfExpired(data, PROTECT_UNTIL_TAG, gameTime);
        clearIfExpired(data, NO_TRANSFORM_UNTIL_TAG, gameTime);
        clearIfExpired(data, TRANSFORM_LOCK_UNTIL_TAG, gameTime);
    }

    private static boolean hasTimedState(CompoundTag data) {
        return data.contains(PROTECT_UNTIL_TAG)
                || data.contains(NO_TRANSFORM_UNTIL_TAG)
                || data.contains(TRANSFORM_LOCK_UNTIL_TAG);
    }

    private static void clearIfExpired(CompoundTag data, String key, long gameTime) {
        if (data.getLong(key) <= gameTime) {
            data.remove(key);
        }
    }

    private static void putMax(CompoundTag data, String key, long until) {
        if (data.getLong(key) < until) {
            data.putLong(key, until);
        }
    }
}
