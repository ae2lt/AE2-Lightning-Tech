package com.moakiee.ae2lt.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IManagedGridNode;

import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

/**
 * Advanced Wireless Overloaded Controller: supports cross-dimension links and
 * unlimited per-receiver channel capacity (INF edge in max-flow).
 */
public class AdvancedWirelessOverloadedControllerBlockEntity extends WirelessOverloadedControllerBlockEntity {

    public AdvancedWirelessOverloadedControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get(), pos, blockState);
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("advanced_wireless_overloaded_controller")
                .setVisualRepresentation(ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get());
    }

    @Override
    protected net.minecraft.world.item.Item getItemFromBlockEntity() {
        return ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().asItem();
    }

    public static void advancedWirelessServerTick(Level level, BlockPos pos, BlockState state,
                                                   AdvancedWirelessOverloadedControllerBlockEntity be) {
        OverloadedControllerBlockEntity.serverTick(level, pos, state, be);
    }
}
