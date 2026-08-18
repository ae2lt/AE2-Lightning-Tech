package com.moakiee.ae2lt.blockentity;

import java.util.EnumSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencyBindingHost;
import com.moakiee.ae2lt.grid.OverloadedGridNodeOwner;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

/**
 * Wireless receiver block entity. The actual virtual grid link is implemented
 * by {@link FrequencyBindingHelper} so regular machines can reuse the same
 * receiver-side frequency binding.
 */
public class WirelessReceiverBlockEntity extends AENetworkBlockEntity
        implements OverloadedGridNodeOwner, FrequencyBindingHost, ServerTickingBlockEntity {

    private final FrequencyBindingHelper frequencyBinding = new FrequencyBindingHelper(this);

    public WirelessReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIRELESS_RECEIVER.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("wireless_receiver")
                .setVisualRepresentation(ModBlocks.WIRELESS_RECEIVER.get())
                .setIdlePowerUsage(5);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.DENSE_SMART;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public FrequencyBindingHelper getFrequencyBinding() {
        return frequencyBinding;
    }

    @Override
    public AENetworkBlockEntity getFrequencyBindingBlockEntity() {
        return this;
    }

    @Override
    public void saveFrequencyBindingChanges() {
        saveChanges();
    }

    @Override
    public void markFrequencyBindingForUpdate() {
        markForUpdate();
    }

    public boolean isConnected() {
        return frequencyBinding.isConnected();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        frequencyBinding.onMainNodeStateChanged(reason);
    }

    @Override
    public void serverTick() {
        frequencyBinding.serverTick();
    }

    @Override
    public void onReady() {
        super.onReady();
        frequencyBinding.onReady();
    }

    @Override
    public void setRemoved() {
        frequencyBinding.setRemoved();
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        frequencyBinding.clearRemoved();
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.WIRELESS_RECEIVER.get().asItem();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        frequencyBinding.save(tag);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        frequencyBinding.load(tag);
    }

    @Override
    public void exportSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.nbt.CompoundTag output,
                               @Nullable net.minecraft.world.entity.player.Player player) {
        super.exportSettings(mode, output, player);
        FrequencyBindingHelper.exportMemorySettings(mode, output, getFrequencyId());
    }

    @Override
    public void importSettings(appeng.util.SettingsFrom mode,
                               net.minecraft.nbt.CompoundTag input,
                               @Nullable net.minecraft.world.entity.player.Player player) {
        super.importSettings(mode, input, player);
        FrequencyBindingHelper.importMemorySettings(mode, input, this::setFrequency);
    }
}
