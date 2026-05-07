package com.moakiee.ae2lt.item;

import com.moakiee.ae2lt.event.NaturalLightningTransformationHandler;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Debug-only item: right-click a lightning rod to summon a "natural" lightning bolt at
 * the rod's position. Consumed on use unless the player is in creative mode.
 *
 * <p>The summoned lightning is tagged with
 * {@link NaturalLightningTransformationHandler#NATURAL_WEATHER_LIGHTNING_TAG}
 * so every mod feature that gates on "natural weather" lightning will treat it
 * as such (lightning rituals that require natural lightning, the collector
 * tiering, etc.).</p>
 */
public class DebugLightningRodItem extends Item {
    public DebugLightningRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.LIGHTNING_ROD)) {
            return InteractionResult.PASS;
        }

        if (level instanceof ServerLevel serverLevel) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (bolt == null) {
                return InteractionResult.FAIL;
            }
            // LightningBolt#getStrikePosition is (bolt.y - 1). Spawning the bolt one
            // block above the rod makes the strike position land exactly on the rod,
            // which is what LightningBolt#powerLightningRod checks for to power it.
            // This mirrors what vanilla ServerLevel.findLightningTargetAround does
            // (it returns rodPos.above(1) for natural rod-attracted lightning).
            Vec3 target = Vec3.atBottomCenterOf(pos.above());
            bolt.moveTo(target.x, target.y, target.z);
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                bolt.setCause(serverPlayer);
            }
            bolt.getPersistentData().putBoolean(
                    NaturalLightningTransformationHandler.NATURAL_WEATHER_LIGHTNING_TAG,
                    true);
            serverLevel.addFreshEntity(bolt);
        }

        Player player = context.getPlayer();
        if (player != null && !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("item.ae2lt.debug_lightning_rod.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, tooltipFlag);
    }
}
