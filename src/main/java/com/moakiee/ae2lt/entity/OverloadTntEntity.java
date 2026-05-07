package com.moakiee.ae2lt.entity;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.logic.LightningBlastTask;
import com.moakiee.ae2lt.logic.LightningBlastTaskManager;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModEntities;
import com.moakiee.ae2lt.registry.ModItems;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class OverloadTntEntity extends PrimedTnt {
    private static final String TAG_OWNER = "Owner";
    private static final double EASTER_EGG_SCAN_RADIUS = 4.0D;
    private static final int EASTER_EGG_LIGHTNING_COUNT = 8;
    private static final double EASTER_EGG_LIGHTNING_SPREAD = 3.5D;
    private static final double EASTER_EGG_CELL_DROP_HEIGHT = 8.0D;

    @Nullable
    private LivingEntity owner;

    @Nullable
    private UUID ownerUuid;

    public OverloadTntEntity(EntityType<? extends OverloadTntEntity> entityType, Level level) {
        super(entityType, level);
        this.setBlockState(getDefaultBlockState());
    }

    public OverloadTntEntity(Level level, double x, double y, double z, @Nullable LivingEntity owner) {
        super(ModEntities.OVERLOAD_TNT.get(), level);
        this.setPos(x, y, z);
        this.setFuse(80);
        this.setBlockState(getDefaultBlockState());
        this.owner = owner;
        this.ownerUuid = owner != null ? owner.getUUID() : null;

        double angle = level.random.nextDouble() * (Math.PI * 2.0D);
        this.setDeltaMovement(-Math.sin(angle) * 0.02D, 0.2D, -Math.cos(angle) * 0.02D);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    @Override
    @Nullable
    public LivingEntity getOwner() {
        if (owner == null && ownerUuid != null && this.level() instanceof ServerLevel serverLevel) {
            var entity = serverLevel.getEntity(ownerUuid);
            if (entity instanceof LivingEntity livingEntity) {
                owner = livingEntity;
            }
        }
        return owner != null ? owner : super.getOwner();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) {
            tag.putUUID(TAG_OWNER, ownerUuid);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = null;
        ownerUuid = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }

    @Override
    protected void explode() {
        if (this.level() instanceof ServerLevel serverLevel) {
            if (tryTriggerEasterEgg(serverLevel)) {
                return;
            }
            if (AE2LTCommonConfig.overloadTntEnableTerrainDamage()) {
                LightningBlastTaskManager.schedule(new LightningBlastTask(serverLevel, this.blockPosition()));
            }
        }
    }

    private boolean tryTriggerEasterEgg(ServerLevel serverLevel) {
        AABB scanBox = AABB.ofSize(
                this.position(),
                EASTER_EGG_SCAN_RADIUS * 2.0D,
                EASTER_EGG_SCAN_RADIUS * 2.0D,
                EASTER_EGG_SCAN_RADIUS * 2.0D);
        List<ItemEntity> matrices = serverLevel.getEntitiesOfClass(
                ItemEntity.class,
                scanBox,
                e -> e.isAlive() && e.getItem().is(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()));
        if (matrices.isEmpty()) {
            return false;
        }

        ItemEntity matrixEntity = matrices.get(0);
        ItemStack matrixStack = matrixEntity.getItem();
        matrixStack.shrink(1);
        if (matrixStack.isEmpty()) {
            matrixEntity.discard();
        } else {
            matrixEntity.setItem(matrixStack);
        }

        for (int i = 0; i < EASTER_EGG_LIGHTNING_COUNT; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (bolt == null) {
                continue;
            }
            double angle = serverLevel.random.nextDouble() * Math.PI * 2.0D;
            double dist = serverLevel.random.nextDouble() * EASTER_EGG_LIGHTNING_SPREAD;
            bolt.moveTo(
                    this.getX() + Math.cos(angle) * dist,
                    this.getY(),
                    this.getZ() + Math.sin(angle) * dist);
            bolt.setVisualOnly(true);
            serverLevel.addFreshEntity(bolt);
        }

        ItemStack cellStack = new ItemStack(ModItems.MYSTERIOUS_CELL.get());
        FixedInfiniteCellItem.initializeOuterCell(cellStack);
        ItemEntity cellEntity = new ItemEntity(
                serverLevel,
                this.getX(),
                this.getY() + EASTER_EGG_CELL_DROP_HEIGHT,
                this.getZ(),
                cellStack);
        cellEntity.setDeltaMovement(0.0D, -0.1D, 0.0D);
        serverLevel.addFreshEntity(cellEntity);

        return true;
    }

    private static BlockState getDefaultBlockState() {
        return ModBlocks.OVERLOAD_TNT.get().defaultBlockState();
    }
}
