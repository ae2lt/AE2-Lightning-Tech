package com.moakiee.ae2lt.entity;

import com.moakiee.ae2lt.registry.ModEntities;
import com.moakiee.ae2lt.network.NetworkHandler;
import com.moakiee.ae2lt.network.RitualItemBurstPacket;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The real ritual reward. It remains an ItemEntity, so normal pickup, saving and automation rules
 * apply after its five-second reveal ceremony.
 */
public final class RitualHyperdimensionalPigmeeEntity extends ItemEntity {
    private static final String TAG_CEREMONY_END = "CeremonyEndGameTime";
    private static final EntityDataAccessor<Long> DATA_CEREMONY_END =
            SynchedEntityData.defineId(RitualHyperdimensionalPigmeeEntity.class, EntityDataSerializers.LONG);
    public static final int CEREMONY_TICKS = 100;
    private static final int FIRST_BURST_REMAINING = 80;
    private static final int SECOND_BURST_REMAINING = 50;
    private static final int THIRD_BURST_REMAINING = 20;
    private static final float LARGE_SCALE = 3.0F;

    public RitualHyperdimensionalPigmeeEntity(
            EntityType<? extends RitualHyperdimensionalPigmeeEntity> type,
            Level level) {
        super(type, level);
    }

    public RitualHyperdimensionalPigmeeEntity(Level level, double x, double y, double z, ItemStack stack) {
        super(ModEntities.RITUAL_HYPERDIMENSIONAL_PIGMEE.get(), level);
        setPos(x, y, z);
        setItem(stack);
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        setPickUpDelay(CEREMONY_TICKS);
        entityData.set(DATA_CEREMONY_END, level.getGameTime() + CEREMONY_TICKS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CEREMONY_END, 0L);
    }

    @Override
    public void tick() {
        boolean ceremonyActive = getCeremonyTicksRemaining(0.0F) > 0.0F;
        setNoGravity(ceremonyActive);
        if (ceremonyActive) {
            setDeltaMovement(Vec3.ZERO);
        }

        super.tick();

        if (!level().isClientSide()) {
            long remaining = entityData.get(DATA_CEREMONY_END) - level().getGameTime();
            if (remaining == FIRST_BURST_REMAINING) {
                broadcastItemBurst(RitualItemBurstPacket.PIGMEE_CORE);
            } else if (remaining == SECOND_BURST_REMAINING) {
                broadcastItemBurst(RitualItemBurstPacket.UNDYING_MODULE);
            } else if (remaining == THIRD_BURST_REMAINING) {
                broadcastItemBurst(RitualItemBurstPacket.PHASE_LOCK_MODULE);
            } else if (entityData.get(DATA_CEREMONY_END) != 0L && remaining <= 0L) {
                entityData.set(DATA_CEREMONY_END, 0L);
                setNoGravity(false);
                setNoPickUpDelay();
            }
        }
    }

    public float getCeremonyScale(float partialTick) {
        float remaining = getCeremonyTicksRemaining(partialTick);
        if (remaining > THIRD_BURST_REMAINING) {
            return LARGE_SCALE;
        }
        if (remaining <= 0.0F) {
            return 1.0F;
        }
        return Mth.lerp(1.0F - remaining / THIRD_BURST_REMAINING, LARGE_SCALE, 1.0F);
    }

    @Override
    public void playerTouch(Player player) {
        if (getCeremonyTicksRemaining(0.0F) > 0.0F) {
            return;
        }
        super.playerTouch(player);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        long ceremonyEnd = entityData.get(DATA_CEREMONY_END);
        if (ceremonyEnd != 0L) {
            tag.putLong(TAG_CEREMONY_END, ceremonyEnd);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_CEREMONY_END, tag.getLong(TAG_CEREMONY_END));
    }

    private float getCeremonyTicksRemaining(float partialTick) {
        long ceremonyEnd = entityData.get(DATA_CEREMONY_END);
        return ceremonyEnd == 0L
                ? 0.0F
                : Math.max(0.0F, ceremonyEnd - (level().getGameTime() + partialTick));
    }

    private void broadcastItemBurst(byte stage) {
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            NetworkHandler.sendToTrackingChunk(
                    serverLevel,
                    chunkPosition(),
                    new RitualItemBurstPacket(getId(), stage));
        }
    }
}
