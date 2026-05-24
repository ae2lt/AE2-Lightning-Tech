package com.moakiee.ae2lt.mixin;

import com.google.common.collect.SetMultimap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGridNode;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.Grid;

import com.moakiee.ae2lt.grid.ControllerMachineNodeLookup;

@Mixin(Grid.class)
public abstract class GridGetMachineNodesMixin {

    @Shadow
    @Final
    private SetMultimap<Class<?>, IGridNode> machines;

    @Inject(method = "getMachineClasses", at = @At("HEAD"), cancellable = true)
    private void ae2lt$normalizeControllerMachineClasses(CallbackInfoReturnable<Iterable<Class<?>>> cir) {
        var machineMap = this.machines.asMap();
        if (!ControllerMachineNodeLookup.hasOverloadedControllerNodes(machineMap)) {
            return;
        }

        cir.setReturnValue(ControllerMachineNodeLookup.normalizedMachineClasses(machineMap));
    }

    @Inject(method = "getMachineNodes", at = @At("HEAD"), cancellable = true)
    private void ae2lt$includeOverloadedControllersForControllerQueries(Class<?> machineClass,
            CallbackInfoReturnable<Iterable<IGridNode>> cir) {
        if (machineClass != ControllerBlockEntity.class) {
            return;
        }

        var machineMap = this.machines.asMap();
        if (!ControllerMachineNodeLookup.hasOverloadedControllerNodes(machineMap)) {
            return;
        }

        // Compatibility shim only:
        // AE2 stores nodes by exact owner class, so a query for
        // ControllerBlockEntity.class would miss our subclasses otherwise.
        // This only affects controller-class queries and appends AE2LT's
        // overloaded controller family, leaving vanilla lookups intact.
        cir.setReturnValue(ControllerMachineNodeLookup.controllerNodes(machineMap));
    }
}
