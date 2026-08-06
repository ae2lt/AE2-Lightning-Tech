package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.lightning.ProtectedItemEntityHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void ae2lt$ignoreProtectedItemDamage(DamageSource damageSource, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (ProtectedItemEntityHelper.shouldIgnoreDamage(itemEntity, damageSource)) {
            cir.setReturnValue(false);
        }
    }
}
