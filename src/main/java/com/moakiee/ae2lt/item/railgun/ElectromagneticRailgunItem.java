package com.moakiee.ae2lt.item.railgun;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.core.localization.Tooltips;

import com.moakiee.ae2lt.logic.railgun.RailgunEnergyBuffer;
import com.moakiee.ae2lt.logic.railgun.RailgunFireService;
import com.moakiee.ae2lt.menu.railgun.RailgunHost;
import com.moakiee.ae2lt.registry.ModDataComponents;
import appeng.menu.locator.ItemMenuHostLocator;

public class ElectromagneticRailgunItem extends Item implements IMenuItem {

    /** Sentinel duration; we manage charging via {@link #onUseTick}. */
    private static final int USE_DURATION = 72_000;

    public ElectromagneticRailgunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        RailgunModules mods = stack.getOrDefault(ModDataComponents.RAILGUN_MODULES.get(), RailgunModules.EMPTY);
        if (!mods.hasCore()) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.railgun.core_required"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            stack.set(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), 0L);
        }
        return new InteractionResultHolder<>(InteractionResult.CONSUME, stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return USE_DURATION;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide) {
            return;
        }
        long current = stack.getOrDefault(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), 0L);
        stack.set(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), current + 1L);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (level.isClientSide || !(user instanceof ServerPlayer player) || !(level instanceof ServerLevel sl)) {
            stack.remove(ModDataComponents.RAILGUN_CHARGE_TICKS.get());
            return;
        }
        long charged = stack.getOrDefault(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), 0L);
        stack.remove(ModDataComponents.RAILGUN_CHARGE_TICKS.get());
        RailgunModules mods = stack.getOrDefault(ModDataComponents.RAILGUN_MODULES.get(), RailgunModules.EMPTY);
        RailgunChargeTier tier = RailgunFireService.tierForCharge(charged, mods);
        if (tier == RailgunChargeTier.HV) {
            return;
        }
        RailgunFireService.fireCharged(sl, player, stack, tier);
    }

    @Override
    public @Nullable ItemMenuHost<?> getMenuHost(
            Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        return new RailgunHost(this, player, locator);
    }

    /**
     * Passive AE buffer top-up while the player holds the railgun. Only runs
     * server-side and only when the stack is in the main hand or off-hand —
     * inventory-resident railguns intentionally do not drain the network.
     * The actual throttling and grid lookup happens inside
     * {@link RailgunEnergyBuffer#refillFromNetwork}.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        boolean inHand = isSelected || player.getOffhandItem() == stack;
        if (!inHand) return;
        RailgunEnergyBuffer.refillFromNetwork(stack, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltip, tooltipFlag);
        long current = RailgunEnergyBuffer.read(stack);
        long capacity = RailgunEnergyBuffer.capacity();
        tooltip.add(Tooltips.energyStorageComponent((double) current, (double) capacity));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        long capacity = RailgunEnergyBuffer.capacity();
        if (capacity <= 0L) {
            return 0;
        }
        double filled = (double) RailgunEnergyBuffer.read(stack) / (double) capacity;
        return Mth.clamp((int) Math.round(filled * 13), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // Standard green of full durability bars, matching AE2 wireless terminals
        return Mth.hsvToRgb(1 / 3.0F, 1.0F, 1.0F);
    }
}
