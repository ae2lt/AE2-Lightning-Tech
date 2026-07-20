package com.moakiee.ae2lt.menu;

import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.logic.craft.MatrixCoreMode;

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
    private final DataSlot patternSlotCountSlot = DataSlot.standalone();
    private final DataSlot craftingUnitCountSlot = DataSlot.standalone();
    private final DataSlot modeSlot = DataSlot.standalone();
    private final DataSlot issueSlot = DataSlot.standalone();
    private final DataSlot dispatchUnitCountSlot = DataSlot.standalone();
    private final DataSlot amplifierUnitCountSlot = DataSlot.standalone();
    private final DataSlot coolingUnitCountSlot = DataSlot.standalone();
    private final DataSlot coolingPowerSlot = DataSlot.standalone();
    private final DataSlot heatSlot = DataSlot.standalone();
    private final DataSlot efficiencySlot = DataSlot.standalone();
    private final DataSlot providerCallsRemainingSlot = DataSlot.standalone();
    private final DataSlot operationsPerTickHighSlot = DataSlot.standalone();
    private final DataSlot operationsPerTickLowSlot = DataSlot.standalone();

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
                                 int patternSlotCount,
                                 int craftingUnitCount,
                                 int mode,
                                 int issue,
                                 int dispatchUnitCount,
                                 int amplifierUnitCount,
                                 int coolingUnitCount,
                                 int coolingPower,
                                 int heat,
                                 int efficiency,
                                 int providerCallsRemaining,
                                 int operationsPerTick) {
        super(TYPE, containerId);
        this.blockPos = blockPos;
        this.host = null;
        formedSlot.set(formed ? 1 : 0);
        memberCountSlot.set(memberCount);
        patternStorageCountSlot.set(patternStorageCount);
        patternSlotCountSlot.set(patternSlotCount);
        craftingUnitCountSlot.set(craftingUnitCount);
        modeSlot.set(mode);
        issueSlot.set(issue);
        dispatchUnitCountSlot.set(dispatchUnitCount);
        amplifierUnitCountSlot.set(amplifierUnitCount);
        coolingUnitCountSlot.set(coolingUnitCount);
        coolingPowerSlot.set(coolingPower);
        heatSlot.set(heat);
        efficiencySlot.set(efficiency);
        providerCallsRemainingSlot.set(providerCallsRemaining);
        setOperationsPerTick(operationsPerTick);
        addSyncSlots();
    }

    private static MatrixControllerMenu clientCreate(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        return new MatrixControllerMenu(
                containerId,
                buf.readBlockPos(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt());
    }

    public static void writeExtraData(FriendlyByteBuf buf, MatrixControllerBlockEntity be) {
        var profile = be.getCraftingProfile();
        var snapshot = be.getLimiterSnapshot();
        buf.writeBlockPos(be.getBlockPos());
        buf.writeBoolean(be.isFormed());
        buf.writeVarInt(be.getMemberCount());
        buf.writeVarInt(be.getPatternStorageCount());
        buf.writeVarInt(be.getPatternSlotCount());
        buf.writeVarInt(be.getCraftingUnitCount());
        buf.writeVarInt(profile.mode().ordinal());
        buf.writeVarInt(be.getPrimaryIssueOrdinal());
        buf.writeVarInt(profile.dispatchUnitCount());
        buf.writeVarInt(profile.multiplierCount());
        buf.writeVarInt(profile.coolingUnitCount());
        buf.writeVarInt(scaleValue(profile.coolPower()));
        buf.writeVarInt(scaleHeat(snapshot.normalizedHeat()));
        buf.writeVarInt(scaleValue(snapshot.efficiencyFactor()));
        buf.writeVarInt(be.getAvailableProviderCalls());
        buf.writeVarInt(saturate(snapshot.operationsPerTick()));
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

    public int getPatternSlotCount() {
        return patternSlotCountSlot.get();
    }

    public int getCraftingUnitCount() {
        return craftingUnitCountSlot.get();
    }

    public MatrixCoreMode getMode() {
        int ordinal = modeSlot.get();
        var values = MatrixCoreMode.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MatrixCoreMode.NONE;
    }

    public int getIssue() {
        return issueSlot.get();
    }

    public int getDispatchUnitCount() {
        return dispatchUnitCountSlot.get();
    }

    public int getAmplifierUnitCount() {
        return amplifierUnitCountSlot.get();
    }

    public int getCoolingUnitCount() {
        return coolingUnitCountSlot.get();
    }

    public double getCoolingPower() {
        return unscaleValue(coolingPowerSlot.get());
    }

    public double getNormalizedHeat() {
        return unscaleHeat(heatSlot.get());
    }

    public double getEfficiencyFactor() {
        return unscaleValue(efficiencySlot.get());
    }

    public int getProviderCallsRemaining() {
        return providerCallsRemainingSlot.get();
    }

    public int getOperationsPerTick() {
        return ((operationsPerTickHighSlot.get() & 0xFFFF) << 16)
                | (operationsPerTickLowSlot.get() & 0xFFFF);
    }

    private void syncFromHost() {
        var profile = host.getCraftingProfile();
        var snapshot = host.getLimiterSnapshot();
        formedSlot.set(host.isFormed() ? 1 : 0);
        memberCountSlot.set(host.getMemberCount());
        patternStorageCountSlot.set(host.getPatternStorageCount());
        patternSlotCountSlot.set(host.getPatternSlotCount());
        craftingUnitCountSlot.set(host.getCraftingUnitCount());
        modeSlot.set(profile.mode().ordinal());
        issueSlot.set(host.getPrimaryIssueOrdinal());
        dispatchUnitCountSlot.set(profile.dispatchUnitCount());
        amplifierUnitCountSlot.set(profile.multiplierCount());
        coolingUnitCountSlot.set(profile.coolingUnitCount());
        coolingPowerSlot.set(scaleValue(profile.coolPower()));
        heatSlot.set(scaleHeat(snapshot.normalizedHeat()));
        efficiencySlot.set(scaleValue(snapshot.efficiencyFactor()));
        providerCallsRemainingSlot.set(host.getAvailableProviderCalls());
        setOperationsPerTick(saturate(snapshot.operationsPerTick()));
    }

    private void addSyncSlots() {
        addDataSlot(formedSlot);
        addDataSlot(memberCountSlot);
        addDataSlot(patternStorageCountSlot);
        addDataSlot(patternSlotCountSlot);
        addDataSlot(craftingUnitCountSlot);
        addDataSlot(modeSlot);
        addDataSlot(issueSlot);
        addDataSlot(dispatchUnitCountSlot);
        addDataSlot(amplifierUnitCountSlot);
        addDataSlot(coolingUnitCountSlot);
        addDataSlot(coolingPowerSlot);
        addDataSlot(heatSlot);
        addDataSlot(efficiencySlot);
        addDataSlot(providerCallsRemainingSlot);
        addDataSlot(operationsPerTickHighSlot);
        addDataSlot(operationsPerTickLowSlot);
    }

    private void setOperationsPerTick(int operationsPerTick) {
        operationsPerTickHighSlot.set((operationsPerTick >>> 16) & 0xFFFF);
        operationsPerTickLowSlot.set(operationsPerTick & 0xFFFF);
    }

    private static int scaleValue(double value) {
        if (Double.isNaN(value) || value <= 0.0D) {
            return 0;
        }
        double scaled = value * 10.0D;
        return scaled >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.round(scaled);
    }

    private static double unscaleValue(int value) {
        return value / 10.0D;
    }

    private static int scaleHeat(double value) {
        if (Double.isNaN(value) || value <= 0.0D) {
            return 0;
        }
        double scaled = value * 1000.0D;
        return scaled >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.round(scaled);
    }

    private static double unscaleHeat(int value) {
        return value / 1000.0D;
    }

    private static int saturate(long value) {
        if (value <= 0L) {
            return 0;
        }
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
