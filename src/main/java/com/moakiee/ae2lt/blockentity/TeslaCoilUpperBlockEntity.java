package com.moakiee.ae2lt.blockentity;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.block.TeslaCoilBlock;
import com.moakiee.ae2lt.block.TeslaCoilHalfHelper;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

import appeng.blockentity.AEBaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

public class TeslaCoilUpperBlockEntity extends AEBaseBlockEntity {
    public TeslaCoilUpperBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TESLA_COIL_UPPER.get(), pos, state);
    }

    public static void ensurePresent(Level level, BlockPos lowerPos) {
        BlockPos upperPos = lowerPos.above();
        BlockState upperState = level.getBlockState(upperPos);
        if (!isUpperTeslaCoilState(upperState)) {
            return;
        }

        if (!(level.getBlockEntity(upperPos) instanceof TeslaCoilUpperBlockEntity)) {
            level.setBlockEntity(new TeslaCoilUpperBlockEntity(upperPos, upperState));
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability != ForgeCapabilities.ITEM_HANDLER && capability != ForgeCapabilities.ENERGY) {
            return super.getCapability(capability, side);
        }

        TeslaCoilBlockEntity host = getHost();
        return host != null ? host.getCapability(capability, side) : LazyOptional.empty();
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModBlocks.TESLA_COIL.get().asItem();
    }

    @Nullable
    private TeslaCoilBlockEntity getHost() {
        if (level == null) {
            return null;
        }

        BlockPos hostPos = TeslaCoilHalfHelper.getCapabilityHostPos(DoubleBlockHalf.UPPER, worldPosition);
        BlockState hostState = level.getBlockState(hostPos);
        if (!isLowerTeslaCoilState(hostState)) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(hostPos);
        return blockEntity instanceof TeslaCoilBlockEntity teslaCoil ? teslaCoil : null;
    }

    private static boolean isUpperTeslaCoilState(BlockState state) {
        return state.getBlock() instanceof TeslaCoilBlock
                && state.hasProperty(TeslaCoilBlock.HALF)
                && state.getValue(TeslaCoilBlock.HALF) == DoubleBlockHalf.UPPER;
    }

    private static boolean isLowerTeslaCoilState(BlockState state) {
        return state.getBlock() instanceof TeslaCoilBlock
                && state.hasProperty(TeslaCoilBlock.HALF)
                && state.getValue(TeslaCoilBlock.HALF) == DoubleBlockHalf.LOWER;
    }
}
