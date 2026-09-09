package com.moakiee.ae2lt.mixin.staff;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moakiee.ae2lt.item.staff.StaffDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BeehiveBlock.class)
public abstract class StaffBeehiveMixin {
    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/BeehiveBlock;dropHoneycomb(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void ae2lt$honeycombDrops(Level level, BlockPos pos, Operation<Void> original,
            @Local(argsOnly = true) ItemStack tool, @Local(argsOnly = true) Player player) {
        if (!(level instanceof ServerLevel server) || !StaffDrops.active(tool)) {
            original.call(level, pos);
            return;
        }
        // The native helper emits exactly three honeycombs via Block.popResource. Preserve its
        // suppression rules while leaving honey reset, bee anger, stats and damage in the caller.
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS) || level.restoringBlockSnapshots) return;
        for (var output : StaffDrops.process(server, player, tool, new ItemStack(Items.HONEYCOMB, 3))) {
            Block.popResource(level, pos, output);
        }
    }
}
