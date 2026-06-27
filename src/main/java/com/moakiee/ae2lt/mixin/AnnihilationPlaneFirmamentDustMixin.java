package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.StorageHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.me.helpers.MachineSource;
import appeng.parts.automation.AnnihilationPlanePart;

import com.moakiee.ae2lt.logic.FirmamentDustGenerationRules;
import com.moakiee.ae2lt.registry.ModItems;

@Mixin(AnnihilationPlanePart.class)
public abstract class AnnihilationPlaneFirmamentDustMixin {
    @Unique
    private int ae2lt$firmamentGenerationTicks;

    @Inject(method = "tickingRequest", at = @At("HEAD"), cancellable = true)
    private void ae2lt$generateFirmamentDustInTheEnd(
            IGridNode node,
            int ticksSinceLastCall,
            CallbackInfoReturnable<TickRateModulation> cir) {
        var self = (AnnihilationPlanePart) (Object) this;
        var host = self.getBlockEntity();
        if (host == null || !(host.getLevel() instanceof ServerLevel level)) {
            ae2lt$firmamentGenerationTicks = 0;
            return;
        }

        var hostPos = host.getBlockPos();
        boolean shouldGenerate = FirmamentDustGenerationRules.shouldGenerate(
                level.dimension().location().toString(),
                self.getSide().getSerializedName(),
                hostPos.getY(),
                level.getMaxBuildHeight());

        if (!shouldGenerate) {
            ae2lt$firmamentGenerationTicks = 0;
            return;
        }

        if (!self.isActive()) {
            ae2lt$firmamentGenerationTicks = 0;
            cir.setReturnValue(TickRateModulation.SLEEP);
            return;
        }

        ae2lt$firmamentGenerationTicks += ticksSinceLastCall;
        if (ae2lt$firmamentGenerationTicks >= FirmamentDustGenerationRules.INTERVAL_TICKS) {
            long amount = ae2lt$firmamentGenerationTicks / FirmamentDustGenerationRules.INTERVAL_TICKS;
            AEItemKey key = AEItemKey.of(ModItems.FIRMAMENT_DUST.get());
            if (key != null) {
                ae2lt$insertIntoGrid(self, key, amount);
            }
            ae2lt$firmamentGenerationTicks -= amount * FirmamentDustGenerationRules.INTERVAL_TICKS;
        }

        cir.setReturnValue(TickRateModulation.IDLE);
    }

    @Unique
    private long ae2lt$insertIntoGrid(AnnihilationPlanePart self, AEKey what, long amount) {
        var grid = self.getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        return StorageHelper.poweredInsert(
                grid.getEnergyService(),
                grid.getStorageService().getInventory(),
                what,
                amount,
                new MachineSource(self));
    }
}
