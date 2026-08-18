package com.moakiee.ae2lt.device.network;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;

public final class ArmorNetworkBinding implements DeviceNetworkBinding {
    public static final ArmorNetworkBinding INSTANCE = new ArmorNetworkBinding();
    // 1.20.1 has no AEComponents; the wireless link lives in the stack NBT under the same
    // "accessPoint" key AE2's WirelessTerminalItem uses (GlobalPos.CODEC via NbtOps).
    private static final String TAG_ACCESS_POINT_POS = "accessPoint";

    private ArmorNetworkBinding() {}

    @Override
    public @Nullable GlobalPos getBoundPos(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_ACCESS_POINT_POS, Tag.TAG_COMPOUND)) {
            return null;
        }
        return GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_ACCESS_POINT_POS))
                .resultOrPartial(error -> {})
                .orElse(null);
    }

    @Override
    public void bind(ItemStack stack, GlobalPos pos) {
        GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos)
                .result()
                .ifPresent(tag -> stack.getOrCreateTag().put(TAG_ACCESS_POINT_POS, tag));
    }

    @Override
    public void unbind(ItemStack stack) {
        stack.removeTagKey(TAG_ACCESS_POINT_POS);
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
