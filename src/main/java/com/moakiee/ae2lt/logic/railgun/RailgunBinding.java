package com.moakiee.ae2lt.logic.railgun;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;

/**
 * Resolves the AE2 grid bound to a railgun ItemStack via AE2's standard
 * wireless-link target component. Mirrors the host-side resolution used by
 * AE2's WirelessTerminalItem, but never exposes the held-stack-on-player
 * mechanic for that; railgun is a distinct ItemMenuHost.
 */
public final class RailgunBinding {

    public enum FailReason { NOT_BOUND, DIM_NOT_LOADED, NO_AP, INACTIVE_AP, OUT_OF_RANGE, WRONG_DIMENSION }

    public record Result(@Nullable IGrid grid, @Nullable IWirelessAccessPoint ap, @Nullable FailReason failure) {
        public static Result ok(IGrid grid, IWirelessAccessPoint ap) {
            return new Result(grid, ap, null);
        }
        public static Result fail(FailReason r) {
            return new Result(null, null, r);
        }
        public boolean success() {
            return failure == null && grid != null;
        }
    }

    private RailgunBinding() {}

    public static @Nullable GlobalPos getBoundPos(ItemStack stack) {
        return stack.get(AEComponents.WIRELESS_LINK_TARGET);
    }

    public static Result resolve(ItemStack stack, ServerPlayer player) {
        GlobalPos pos = getBoundPos(stack);
        if (pos == null) {
            return Result.fail(FailReason.NOT_BOUND);
        }
        if (!player.level().dimension().equals(pos.dimension())) {
            return Result.fail(FailReason.WRONG_DIMENSION);
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return Result.fail(FailReason.DIM_NOT_LOADED);
        }
        ServerLevel target = server.getLevel(pos.dimension());
        if (target == null) {
            return Result.fail(FailReason.DIM_NOT_LOADED);
        }
        BlockPos bp = pos.pos();
        if (!target.isLoaded(bp)) {
            return Result.fail(FailReason.DIM_NOT_LOADED);
        }
        BlockEntity be = target.getBlockEntity(bp);
        if (!(be instanceof IWirelessAccessPoint ap)) {
            return Result.fail(FailReason.NO_AP);
        }
        if (!ap.isActive()) {
            return Result.fail(FailReason.INACTIVE_AP);
        }
        IGrid grid = ap.getGrid();
        if (grid == null) {
            return Result.fail(FailReason.NO_AP);
        }
        // Range check disabled - railgun works at any distance once bound
        // double range = ap.getRange();
        // double dist = distance(player, ap);
        // if (dist > range) {
        //     return Result.fail(FailReason.OUT_OF_RANGE);
        // }
        return Result.ok(grid, ap);
    }

    private static double distance(Entity player, IWirelessAccessPoint ap) {
        var loc = ap.getLocation();
        BlockPos bp = loc.getPos();
        double dx = (bp.getX() + 0.5) - player.getX();
        double dy = (bp.getY() + 0.5) - player.getY();
        double dz = (bp.getZ() + 0.5) - player.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static String failKey(FailReason r) {
        return switch (r) {
            case NOT_BOUND -> "ae2lt.railgun.fail.not_bound";
            case DIM_NOT_LOADED -> "ae2lt.railgun.fail.dim_not_loaded";
            case NO_AP -> "ae2lt.railgun.fail.no_ap";
            case INACTIVE_AP -> "ae2lt.railgun.fail.inactive_ap";
            case OUT_OF_RANGE -> "ae2lt.railgun.fail.out_of_range";
            case WRONG_DIMENSION -> "ae2lt.railgun.fail.wrong_dimension";
        };
    }
}
