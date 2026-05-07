package com.moakiee.ae2lt.block;

import com.moakiee.ae2lt.entity.OverloadTntEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

public class OverloadTntBlock extends TntBlock {
    public OverloadTntBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult) {
        if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }

        prime(level, pos, player);
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 11);

        if (!player.getAbilities().instabuild) {
            if (stack.is(Items.FLINT_AND_STEEL)) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            } else {
                stack.shrink(1);
            }
        }

        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(stack.getItem()));
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (oldState.is(state.getBlock())) {
            return;
        }

        if (level.hasNeighborSignal(pos)) {
            prime(level, pos, null);
            level.removeBlock(pos, false);
        }
    }

    @Override
    public void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            net.minecraft.world.level.block.Block block,
            BlockPos fromPos,
            boolean movedByPiston) {
        if (level.hasNeighborSignal(pos)) {
            prime(level, pos, null);
            level.removeBlock(pos, false);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative() && state.getValue(UNSTABLE)) {
            prime(level, pos, player);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable Direction face, @Nullable LivingEntity igniter) {
        prime(level, pos, igniter);
        level.removeBlock(pos, false);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (level.isClientSide) {
            return;
        }

        LivingEntity owner = explosion.getIndirectSourceEntity() instanceof LivingEntity livingEntity ? livingEntity : null;
        OverloadTntEntity tnt = new OverloadTntEntity(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, owner);
        int fuse = tnt.getFuse();
        // Keep vanilla-style shortened chain-explosion timing. The current fuse values are small,
        // so the historical short cast is harmless for now.
        tnt.setFuse((short) (level.random.nextInt(fuse / 4) + fuse / 8));
        level.addFreshEntity(tnt);
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    private static void prime(Level level, BlockPos pos, @Nullable LivingEntity igniter) {
        if (level.isClientSide) {
            return;
        }

        OverloadTntEntity tnt = new OverloadTntEntity(
                level,
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D,
                igniter);
        level.addFreshEntity(tnt);
        level.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(igniter, GameEvent.PRIME_FUSE, pos);
    }
}
