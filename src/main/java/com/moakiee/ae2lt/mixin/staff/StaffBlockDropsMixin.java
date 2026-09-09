package com.moakiee.ae2lt.mixin.staff;

import java.util.List;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.item.staff.StaffDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommonHooks.class)
public abstract class StaffBlockDropsMixin {
    // This call is reached AFTER every BlockDropsEvent listener and the final cancellation check.
    // Collection inside an event listener could credit the inventory before a later cancellation.
    @WrapOperation(method = "handleBlockDrops", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean ae2lt$staffDrop(ServerLevel level, Entity entity, Operation<Boolean> original,
            ServerLevel sourceLevel, BlockPos pos, BlockState state, BlockEntity blockEntity,
            List<ItemEntity> drops, Entity breaker, ItemStack tool) {
        if (!(breaker instanceof Player player) || !(entity instanceof ItemEntity item) || !StaffDrops.active(tool)) {
            return original.call(level, entity);
        }
        var outputs = StaffDrops.process(level, player, tool, item.getItem());
        if (outputs.isEmpty()) return true;
        item.setItem(outputs.getFirst());
        boolean spawned = original.call(level, item);
        for (int i = 1; i < outputs.size(); i++) {
            var extra = new ItemEntity(level, item.getX(), item.getY(), item.getZ(), outputs.get(i));
            extra.setDeltaMovement(item.getDeltaMovement());
            extra.setDefaultPickUpDelay();
            spawned |= original.call(level, extra);
        }
        return spawned;
    }
}
