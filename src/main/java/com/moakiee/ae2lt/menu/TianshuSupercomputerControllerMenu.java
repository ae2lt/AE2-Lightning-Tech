package com.moakiee.ae2lt.menu;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.CpuMainCoreTier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.MenuTypeBuilder;
import com.moakiee.thunderbolt.core.crafting.algorithm.ForgeMenuTypeBuilderExtension;

public class TianshuSupercomputerControllerMenu extends AEBaseMenu {
    public static final MenuType<TianshuSupercomputerControllerMenu> TYPE =
            ForgeMenuTypeBuilderExtension.buildUnregistered(
                    MenuTypeBuilder
                            .create(TianshuSupercomputerControllerMenu::new,
                                    TianshuSupercomputerControllerBlockEntity.class)
                            .withMenuTitle(host -> host.getBlockState().getBlock().getName())
                            .withInitialData(
                                    TianshuSupercomputerControllerMenu::writeExtraData,
                                    (host, menu, buf) -> menu.readExtraData(buf)),
                    new ResourceLocation(AE2LightningTech.MODID, "tianshu_supercomputer_controller"));
    private final BlockPos blockPos;
    private final TianshuSupercomputerControllerBlockEntity host;
    private final DataSlot formed = DataSlot.standalone();
    private final DataSlot tier = DataSlot.standalone();
    private final DataSlot storageUnits = DataSlot.standalone();
    private final DataSlot parallelUnits = DataSlot.standalone();
    private final DataSlot amplifierUnits = DataSlot.standalone();
    private final DataSlot closedLoopPatternStorages = DataSlot.standalone();
    private final DataSlot closedLoopSeedStorages = DataSlot.standalone();
    private final DataSlot parallelism = DataSlot.standalone();
    private final DataSlot capped = DataSlot.standalone();
    private final DataSlot issue = DataSlot.standalone();
    private final DataSlot[] storage = {DataSlot.standalone(), DataSlot.standalone(),
            DataSlot.standalone(), DataSlot.standalone()};
    private final DataSlot[] maxCopiesPerTick = {DataSlot.standalone(), DataSlot.standalone(),
            DataSlot.standalone(), DataSlot.standalone()};

    public TianshuSupercomputerControllerMenu(int id, Inventory inventory,
                                               TianshuSupercomputerControllerBlockEntity host) {
        super(TYPE, id, inventory, host);
        this.blockPos = host.getBlockPos();
        this.host = host;
        syncFromHost();
        addSlots();
    }

    private void readExtraData(FriendlyByteBuf buf) {
        formed.set(buf.readBoolean() ? 1 : 0);
        tier.set(buf.readVarInt());
        storageUnits.set(buf.readVarInt());
        parallelUnits.set(buf.readVarInt());
        amplifierUnits.set(buf.readVarInt());
        closedLoopPatternStorages.set(buf.readVarInt());
        closedLoopSeedStorages.set(buf.readVarInt());
        setStorage(buf.readLong());
        parallelism.set(buf.readVarInt());
        setMaxCopiesPerTick(buf.readLong());
        capped.set(buf.readBoolean() ? 1 : 0);
        issue.set(buf.readVarInt());
    }

    public static void writeExtraData(
            TianshuSupercomputerControllerBlockEntity host, FriendlyByteBuf buf) {
        var profile = host.getCoreProfile();
        buf.writeBoolean(host.isFormed());
        buf.writeVarInt(profile.mainCore() == null ? -1 : profile.mainCore().ordinal());
        buf.writeVarInt(profile.storageUnitCount());
        buf.writeVarInt(profile.parallelUnitCount());
        buf.writeVarInt(profile.amplifierUnitCount());
        buf.writeVarInt(host.getFunctionProfile().closedLoopPatternStorageCount());
        buf.writeVarInt(host.getFunctionProfile().closedLoopSeedStorageCount());
        buf.writeLong(profile.storageBytes());
        buf.writeVarInt(profile.parallelism());
        buf.writeLong(profile.maxCopiesPerTick());
        buf.writeBoolean(profile.parallelCapped());
        buf.writeVarInt(host.getPrimaryIssueOrdinal());
    }

    @Override
    public void broadcastChanges() {
        syncFromHost();
        super.broadcastChanges();
    }

    private void syncFromHost() {
        var profile = host.getCoreProfile();
        formed.set(host.isFormed() ? 1 : 0);
        tier.set(profile.mainCore() == null ? -1 : profile.mainCore().ordinal());
        storageUnits.set(profile.storageUnitCount());
        parallelUnits.set(profile.parallelUnitCount());
        amplifierUnits.set(profile.amplifierUnitCount());
        closedLoopPatternStorages.set(host.getFunctionProfile().closedLoopPatternStorageCount());
        closedLoopSeedStorages.set(host.getFunctionProfile().closedLoopSeedStorageCount());
        parallelism.set(profile.parallelism());
        capped.set(profile.parallelCapped() ? 1 : 0);
        issue.set(host.getPrimaryIssueOrdinal());
        setStorage(profile.storageBytes());
        setMaxCopiesPerTick(profile.maxCopiesPerTick());
    }

    private void addSlots() {
        addDataSlot(formed); addDataSlot(tier); addDataSlot(storageUnits); addDataSlot(parallelUnits);
        addDataSlot(amplifierUnits);
        addDataSlot(closedLoopPatternStorages); addDataSlot(closedLoopSeedStorages);
        addDataSlot(parallelism); addDataSlot(capped); addDataSlot(issue);
        for (var slot : storage) addDataSlot(slot);
        for (var slot : maxCopiesPerTick) addDataSlot(slot);
    }

    private void setStorage(long value) {
        for (int i = 0; i < 4; i++) storage[i].set((int) (value >>> (i * 16)) & 0xFFFF);
    }

    private void setMaxCopiesPerTick(long value) {
        for (int i = 0; i < 4; i++) maxCopiesPerTick[i].set((int) (value >>> (i * 16)) & 0xFFFF);
    }

    public boolean isFormed() { return formed.get() != 0; }
    public BlockPos getBlockPos() { return blockPos; }
    public int token() { return containerId; }
    public CpuMainCoreTier getTier() {
        int value = tier.get();
        return value >= 0 && value < CpuMainCoreTier.values().length ? CpuMainCoreTier.values()[value] : null;
    }
    public int getStorageUnits() { return storageUnits.get(); }
    public int getParallelUnits() { return parallelUnits.get(); }
    public int getAmplifierUnits() { return amplifierUnits.get(); }
    public int getClosedLoopPatternStorages() { return closedLoopPatternStorages.get(); }
    public int getClosedLoopSeedStorages() { return closedLoopSeedStorages.get(); }
    public int getSuccessfulDispatchesPerTick() { return parallelism.get(); }
    public boolean isCapped() { return capped.get() != 0; }
    public int getIssue() { return issue.get(); }
    public long getStorageBytes() {
        long value = 0L;
        for (int i = 0; i < 4; i++) value |= (long) (storage[i].get() & 0xFFFF) << (i * 16);
        return value;
    }
    public long getMaxCopiesPerTick() {
        long value = 0L;
        for (int i = 0; i < 4; i++) value |= (long) (maxCopiesPerTick[i].get() & 0xFFFF) << (i * 16);
        return value;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + .5, blockPos.getY() + .5, blockPos.getZ() + .5) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
