package com.moakiee.ae2lt.block;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;

import com.moakiee.ae2lt.blockentity.PigmeeMentalmathUnitBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class PigmeeMentalmathUnitBlock extends AEBaseEntityBlock<PigmeeMentalmathUnitBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public PigmeeMentalmathUnitBlock() {
        super(metalProps().forceSolidOn());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }
}
