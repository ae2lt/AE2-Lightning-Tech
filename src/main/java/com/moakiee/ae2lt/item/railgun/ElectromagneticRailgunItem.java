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
import appeng.menu.locator.ItemMenuHostLocator;

import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.device.DeviceItem;
import com.moakiee.ae2lt.device.DeviceKind;
import com.moakiee.ae2lt.logic.railgun.RailgunEnergyBuffer;
import com.moakiee.ae2lt.logic.railgun.RailgunFireService;
import com.moakiee.ae2lt.menu.railgun.RailgunHost;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.util.DeviceHubTooltip;
import com.moakiee.ae2lt.util.EnergyText;

public class ElectromagneticRailgunItem extends Item implements IMenuItem, DeviceItem {

    /** Sentinel duration; we manage charging via {@link #onUseTick}. */
    private static final int USE_DURATION = 72_000;

    public ElectromagneticRailgunItem(Properties properties) {
        super(properties);
    }

    @Override
    public DeviceKind deviceKind() {
        return DeviceKind.RAILGUN;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!RailgunStructuralCore.hasCore(stack)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("ae2lt.railgun.structural_core_required"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!hasOverloadCoreModule(stack)) {
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
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return oldStack.getItem() != newStack.getItem() || slotChanged;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        boolean inHand = isSelected || player.getOffhandItem() == stack;
        if (inHand) {
            if (player instanceof ServerPlayer serverPlayer) {
                refillFromBoundNetwork(stack, serverPlayer);
            }
        }
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide) {
            return;
        }
        if (!(user instanceof ServerPlayer player)) {
            return;
        }
        if (!hasOverloadCoreModule(stack)) {
            stack.remove(ModDataComponents.RAILGUN_CHARGE_TICKS.get());
            player.stopUsingItem();
            player.displayClientMessage(Component.translatable("ae2lt.railgun.core_required"), true);
            return;
        }
        long current = stack.getOrDefault(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), 0L);
        RailgunChargeTier chargingTier = chargingCostTier(current);
        long chargeCost = RailgunEnergyRules.chargeCostPerTickFe(chargingTier);
        refillMissingFe(stack, player, chargeCost);
        if (!RailgunEnergyBuffer.tryConsume(stack, player, chargeCost)) {
            stack.remove(ModDataComponents.RAILGUN_CHARGE_TICKS.get());
            player.stopUsingItem();
            player.displayClientMessage(Component.translatable("ae2lt.railgun.fail.no_fe"), true);
            return;
        }
        // ACCEL module step-up: each module adds +1 charge-ticks per real tick.
        //   0 modules → +1 / tick (baseline)
        //   1 module  → +2 / tick (2× speed)
        //   2 modules → +3 / tick (3× speed)
        // Thresholds (RailgunDefaults.CHARGE_TICKS_TIER1/2/3) stay fixed in
        // "charge units"; only the rate of accumulation changes.
        RailgunModuleEntries mods = stack.getOrDefault(
                ModDataComponents.RAILGUN_MODULE_ENTRIES.get(), RailgunModuleEntries.EMPTY);
        long step = 1L + RailgunFireService.countAccelerationModules(mods);
        stack.set(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), current + step);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (level.isClientSide || !(user instanceof ServerPlayer player) || !(level instanceof ServerLevel sl)) {
            stack.remove(ModDataComponents.RAILGUN_CHARGE_TICKS.get());
            return;
        }
        long charged = stack.getOrDefault(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), 0L);
        stack.remove(ModDataComponents.RAILGUN_CHARGE_TICKS.get());
        RailgunModuleEntries mods = stack.getOrDefault(ModDataComponents.RAILGUN_MODULE_ENTRIES.get(), RailgunModuleEntries.EMPTY);
        RailgunChargeTier tier = RailgunFireService.tierForCharge(charged, mods);
        if (tier == RailgunChargeTier.HV) {
            return;
        }
        RailgunFireService.fireCharged(sl, player, stack, tier, charged);
    }

    @Override
    public @Nullable ItemMenuHost<?> getMenuHost(
            Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hitResult) {
        return new RailgunHost(this, player, locator);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltip, tooltipFlag);
        long current = RailgunEnergyBuffer.read(stack);
        long capacity = RailgunEnergyBuffer.capacity(stack);
        tooltip.add(EnergyText.storedFe(current, capacity));
        tooltip.add(DeviceHubTooltip.openConfigHint());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        long capacity = RailgunEnergyBuffer.capacity(stack);
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

    private static boolean hasOverloadCoreModule(ItemStack stack) {
        return stack.getOrDefault(
                ModDataComponents.RAILGUN_MODULE_ENTRIES.get(),
                RailgunModuleEntries.EMPTY).hasCore();
    }

    private static RailgunChargeTier chargingCostTier(long current) {
        if (current >= RailgunDefaults.CHARGE_TICKS_TIER3) {
            return RailgunChargeTier.EHV3;
        }
        if (current >= RailgunDefaults.CHARGE_TICKS_TIER2) {
            return RailgunChargeTier.EHV2;
        }
        return RailgunChargeTier.EHV1;
    }

    private static void refillFromBoundNetwork(ItemStack stack, ServerPlayer player) {
        RailgunEnergyBuffer.refillFromNetwork(stack, player, Long.MAX_VALUE);
    }

    private static void refillMissingFe(ItemStack stack, ServerPlayer player, long required) {
        if (required <= 0L) {
            return;
        }
        RailgunEnergyBuffer.refillFromNetwork(
                stack,
                player,
                Math.max(0L, required - RailgunEnergyBuffer.read(stack)));
    }
}
