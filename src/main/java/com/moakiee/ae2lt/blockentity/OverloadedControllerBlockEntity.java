package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.grid.OverloadedGridNodeOwner;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.IEnergyStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.helpers.ForgeEnergyAdapter;

/**
 * Minimal custom controller node owner.
 * Extends AE2's controller block entity so future controller-scoped channel
 * changes can target this subtype without changing vanilla controller behavior.
 * <p>
 * Important: later 128-channel logic is keyed off this concrete owner type,
 * so vanilla ControllerBlockEntity instances remain untouched.
 */
public class OverloadedControllerBlockEntity extends ControllerBlockEntity implements OverloadedGridNodeOwner {
    private static final double INTERNAL_MAX_POWER = 16_000_000.0;

    public OverloadedControllerBlockEntity(BlockPos pos, BlockState blockState) {
        this(ModBlockEntities.OVERLOADED_CONTROLLER.get(), pos, blockState);
    }

    protected OverloadedControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.setInternalMaxPower(INTERNAL_MAX_POWER);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OverloadedControllerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }

        be.injectAEPower(AE2LTCommonConfig.overloadedControllerPassiveAePerTick(), Actionable.MODULATE);
    }

    public IEnergyStorage getEnergyStorageCapability(Direction side) {
        return new ForgeEnergyAdapter(this);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        // The tag/visual representation are AE2LT-specific, but the underlying node
        // still follows vanilla AE2 controller behavior unless an owner-scoped mixin says otherwise.
        return super.createMainNode()
                .setTagName("overloaded_controller")
                .setVisualRepresentation(ModBlocks.OVERLOADED_CONTROLLER.get());
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.DENSE_SMART;
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModBlocks.OVERLOADED_CONTROLLER.get().asItem();
    }
}

