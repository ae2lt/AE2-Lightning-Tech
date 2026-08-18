package com.moakiee.ae2lt.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;

/**
 * Keeps overload patterns out of normal AE2 pattern providers.
 * <p>
 * OverloadedPatternProviderLogic rebuilds its pattern list separately and still
 * accepts these patterns.
 */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin {

    @WrapOperation(
            method = "updatePatterns",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/crafting/PatternDetailsHelper;decodePattern(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;"
            ),
            remap = false
    )
    private @Nullable IPatternDetails ae2lt$rejectOverloadPatternsInNormalProviders(
            ItemStack stack, Level level, Operation<IPatternDetails> original) {
        var details = original.call(stack, level);
        return details instanceof OverloadedProviderOnlyPatternDetails ? null : details;
    }

    @Inject(method = "onStackReturnedToNetwork", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2lt$handleOverloadUnlockMatching(GenericStack genericStack, CallbackInfo ci) {
        if ((Object) this instanceof OverloadedPatternProviderLogic overloadedLogic
                && overloadedLogic.handleOverloadUnlockOnReturnedStack(genericStack)) {
            ci.cancel();
        }
    }
}
