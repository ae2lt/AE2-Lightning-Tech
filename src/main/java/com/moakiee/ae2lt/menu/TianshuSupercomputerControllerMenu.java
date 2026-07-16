package com.moakiee.ae2lt.menu;

import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.CpuMainCoreTier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

public class TianshuSupercomputerControllerMenu extends AbstractContainerMenu {
    public static final MenuType<TianshuSupercomputerControllerMenu> TYPE =
            IMenuTypeExtension.create(TianshuSupercomputerControllerMenu::clientCreate);
    private final BlockPos blockPos;
    private final TianshuSupercomputerControllerBlockEntity host;
    private final DataSlot formed = DataSlot.standalone();
    private final DataSlot tier = DataSlot.standalone();
    private final DataSlot capacityCores = DataSlot.standalone();
    private final DataSlot parallelCores = DataSlot.standalone();
    private final DataSlot parallelism = DataSlot.standalone();
    private final DataSlot capped = DataSlot.standalone();
    private final DataSlot fastPlanning = DataSlot.standalone();
    private final DataSlot issue = DataSlot.standalone();
    private final DataSlot[] storage = {DataSlot.standalone(), DataSlot.standalone(),
            DataSlot.standalone(), DataSlot.standalone()};

    public TianshuSupercomputerControllerMenu(int id, Inventory inventory,
                                               TianshuSupercomputerControllerBlockEntity host) {
        super(TYPE, id);
        this.blockPos = host.getBlockPos();
        this.host = host;
        syncFromHost();
        addSlots();
    }

    private TianshuSupercomputerControllerMenu(int id, BlockPos pos, boolean formed, int tier,
                                                int capacity, int parallel, long storage,
                                                int parallelism, boolean capped, boolean fastPlanning, int issue) {
        super(TYPE, id);
        this.blockPos = pos;
        this.host = null;
        this.formed.set(formed ? 1 : 0);
        this.tier.set(tier);
        capacityCores.set(capacity);
        parallelCores.set(parallel);
        this.parallelism.set(parallelism);
        this.capped.set(capped ? 1 : 0);
        this.fastPlanning.set(fastPlanning ? 1 : 0);
        this.issue.set(issue);
        setStorage(storage);
        addSlots();
    }

    private static TianshuSupercomputerControllerMenu clientCreate(int id, Inventory inventory, FriendlyByteBuf buf) {
        return new TianshuSupercomputerControllerMenu(id, buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readVarInt(), buf.readBoolean(),
                buf.readBoolean(), buf.readVarInt());
    }

    public static void writeExtraData(FriendlyByteBuf buf, TianshuSupercomputerControllerBlockEntity host) {
        var profile = host.getCoreProfile();
        buf.writeBlockPos(host.getBlockPos());
        buf.writeBoolean(host.isFormed());
        buf.writeVarInt(profile.mainCore() == null ? -1 : profile.mainCore().ordinal());
        buf.writeVarInt(profile.capacityCoreCount());
        buf.writeVarInt(profile.parallelCoreCount());
        buf.writeLong(profile.storageBytes());
        buf.writeVarInt(profile.parallelism());
        buf.writeBoolean(profile.parallelCapped());
        buf.writeBoolean(host.isFastPlanningEnabled());
        buf.writeVarInt(host.getPrimaryIssueOrdinal());
    }

    @Override
    public void broadcastChanges() {
        if (host != null) syncFromHost();
        super.broadcastChanges();
    }

    private void syncFromHost() {
        var profile = host.getCoreProfile();
        formed.set(host.isFormed() ? 1 : 0);
        tier.set(profile.mainCore() == null ? -1 : profile.mainCore().ordinal());
        capacityCores.set(profile.capacityCoreCount());
        parallelCores.set(profile.parallelCoreCount());
        parallelism.set(profile.parallelism());
        capped.set(profile.parallelCapped() ? 1 : 0);
        fastPlanning.set(host.isFastPlanningEnabled() ? 1 : 0);
        issue.set(host.getPrimaryIssueOrdinal());
        setStorage(profile.storageBytes());
    }

    private void addSlots() {
        addDataSlot(formed); addDataSlot(tier); addDataSlot(capacityCores); addDataSlot(parallelCores);
        addDataSlot(parallelism); addDataSlot(capped); addDataSlot(fastPlanning); addDataSlot(issue);
        for (var slot : storage) addDataSlot(slot);
    }

    private void setStorage(long value) {
        for (int i = 0; i < 4; i++) storage[i].set((int) (value >>> (i * 16)) & 0xFFFF);
    }

    public boolean isFormed() { return formed.get() != 0; }
    public BlockPos getBlockPos() { return blockPos; }
    public int token() { return containerId; }
    public CpuMainCoreTier getTier() {
        int value = tier.get();
        return value >= 0 && value < CpuMainCoreTier.values().length ? CpuMainCoreTier.values()[value] : null;
    }
    public int getCapacityCores() { return capacityCores.get(); }
    public int getParallelCores() { return parallelCores.get(); }
    public int getParallelism() { return parallelism.get(); }
    public boolean isCapped() { return capped.get() != 0; }
    public boolean isFastPlanningEnabled() { return fastPlanning.get() != 0; }
    public int getIssue() { return issue.get(); }
    public long getStorageBytes() {
        long value = 0L;
        for (int i = 0; i < 4; i++) value |= (long) (storage[i].get() & 0xFFFF) << (i * 16);
        return value;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + .5, blockPos.getY() + .5, blockPos.getZ() + .5) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
