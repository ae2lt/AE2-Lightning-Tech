package com.moakiee.ae2lt.menu;

import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

public class MatrixControllerMenu extends AbstractContainerMenu {
    public static final MenuType<MatrixControllerMenu> TYPE =
            IMenuTypeExtension.create(MatrixControllerMenu::clientCreate);

    private final BlockPos blockPos;
    private final MatrixControllerBlockEntity host;
    private final DataSlot formedSlot = DataSlot.standalone();
    private final DataSlot memberCountSlot = DataSlot.standalone();
    private final DataSlot patternStorageCountSlot = DataSlot.standalone();
    private final DataSlot craftingUnitCountSlot = DataSlot.standalone();

    public MatrixControllerMenu(int containerId, Inventory playerInventory, MatrixControllerBlockEntity host) {
        super(TYPE, containerId);
        this.blockPos = host.getBlockPos();
        this.host = host;
        syncFromHost();
        addSyncSlots();
    }

    private MatrixControllerMenu(int containerId,
                                 BlockPos blockPos,
                                 boolean formed,
                                 int memberCount,
                                 int patternStorageCount,
                                 int craftingUnitCount) {
        super(TYPE, containerId);
        this.blockPos = blockPos;
        this.host = null;
        formedSlot.set(formed ? 1 : 0);
        memberCountSlot.set(memberCount);
        patternStorageCountSlot.set(patternStorageCount);
        craftingUnitCountSlot.set(craftingUnitCount);
        addSyncSlots();
    }

    private static MatrixControllerMenu clientCreate(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        return new MatrixControllerMenu(
                containerId,
                buf.readBlockPos(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt());
    }

    public static void writeExtraData(FriendlyByteBuf buf, MatrixControllerBlockEntity be) {
        buf.writeBlockPos(be.getBlockPos());
        buf.writeBoolean(be.isFormed());
        buf.writeVarInt(be.getMemberCount());
        buf.writeVarInt(be.getPatternStorageCount());
        buf.writeVarInt(be.getCraftingUnitCount());
    }

    @Override
    public void broadcastChanges() {
        if (host != null) {
            syncFromHost();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        if (host != null) {
            if (host.isRemoved() || host.getLevel() == null) {
                return false;
            }
            if (player.level() != host.getLevel()) {
                return false;
            }
            BlockEntity current = host.getLevel().getBlockEntity(blockPos);
            if (current != host) {
                return false;
            }
        }
        return player.distanceToSqr(
                blockPos.getX() + 0.5D,
                blockPos.getY() + 0.5D,
                blockPos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public int token() {
        return containerId;
    }

    public boolean isFormed() {
        return formedSlot.get() != 0;
    }

    public int getMemberCount() {
        return memberCountSlot.get();
    }

    public int getPatternStorageCount() {
        return patternStorageCountSlot.get();
    }

    public int getCraftingUnitCount() {
        return craftingUnitCountSlot.get();
    }

    private void syncFromHost() {
        formedSlot.set(host.isFormed() ? 1 : 0);
        memberCountSlot.set(host.getMemberCount());
        patternStorageCountSlot.set(host.getPatternStorageCount());
        craftingUnitCountSlot.set(host.getCraftingUnitCount());
    }

    private void addSyncSlots() {
        addDataSlot(formedSlot);
        addDataSlot(memberCountSlot);
        addDataSlot(patternStorageCountSlot);
        addDataSlot(craftingUnitCountSlot);
    }
}
