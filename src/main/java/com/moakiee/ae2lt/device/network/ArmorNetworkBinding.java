package com.moakiee.ae2lt.device.network;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;

public final class ArmorNetworkBinding implements DeviceNetworkBinding {
    public static final ArmorNetworkBinding INSTANCE = new ArmorNetworkBinding();

    private ArmorNetworkBinding() {}

    @Override
    public @Nullable GlobalPos getBoundPos(ItemStack stack) {
        return stack.get(AEComponents.WIRELESS_LINK_TARGET);
    }

    @Override
    public void bind(ItemStack stack, GlobalPos pos) {
        stack.set(AEComponents.WIRELESS_LINK_TARGET, pos);
    }

    @Override
    public void unbind(ItemStack stack) {
        stack.remove(AEComponents.WIRELESS_LINK_TARGET);
    }

    @Override
    public BindingResolveResult resolve(ItemStack stack, ServerPlayer player) {
        GlobalPos pos = getBoundPos(stack);
        if (pos == null) {
            return BindingResolveResult.fail(BindingResolveResult.FailureReason.NOT_BOUND);
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return BindingResolveResult.fail(BindingResolveResult.FailureReason.DIM_NOT_LOADED);
        }
        ServerLevel target = server.getLevel(pos.dimension());
        if (target == null) {
            return BindingResolveResult.fail(BindingResolveResult.FailureReason.DIM_NOT_LOADED);
        }
        BlockPos blockPos = pos.pos();
        if (!target.isLoaded(blockPos)) {
            return BindingResolveResult.fail(BindingResolveResult.FailureReason.DIM_NOT_LOADED);
        }
        BlockEntity blockEntity = target.getBlockEntity(blockPos);
        if (!(blockEntity instanceof IWirelessAccessPoint accessPoint)) {
            return BindingResolveResult.fail(BindingResolveResult.FailureReason.NO_AP);
        }
        if (!accessPoint.isActive()) {
            return BindingResolveResult.fail(BindingResolveResult.FailureReason.INACTIVE_AP);
        }
        IGrid grid = accessPoint.getGrid();
        if (grid == null) {
            return BindingResolveResult.fail(BindingResolveResult.FailureReason.NO_AP);
        }
        return BindingResolveResult.ok(grid, accessPoint);
    }
}
