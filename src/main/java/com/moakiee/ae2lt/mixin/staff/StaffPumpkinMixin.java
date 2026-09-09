package com.moakiee.ae2lt.mixin.staff;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moakiee.ae2lt.item.staff.StaffDrops;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PumpkinBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PumpkinBlock.class)
public abstract class StaffPumpkinMixin {
    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean ae2lt$carvingDrops(Level level, Entity entity, Operation<Boolean> original,
            @Local(argsOnly = true) ItemStack tool, @Local(argsOnly = true) Player player) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof ItemEntity item) || !StaffDrops.active(tool)) {
            return original.call(level, entity);
        }
        var outputs = StaffDrops.process(server, player, tool, item.getItem());
        if (outputs.isEmpty()) return true;
        item.setItem(outputs.getFirst());
        boolean spawned = original.call(level, item);
        for (int i = 1; i < outputs.size(); i++) {
            var extra = new ItemEntity(level, item.getX(), item.getY(), item.getZ(), outputs.get(i));
            extra.setDeltaMovement(item.getDeltaMovement());
            spawned |= original.call(level, extra);
        }
        return spawned;
    }
}
