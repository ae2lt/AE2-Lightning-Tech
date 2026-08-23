package com.moakiee.ae2lt.mixin;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorUndyingHandler;
import com.moakiee.ae2lt.event.LightningItemTransformationHandler;
import com.moakiee.ae2lt.event.NaturalLightningTransformationHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @ModifyArg(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
                    // In Forge 1.20.1 tickChunk spawns the skeleton horse trap first and the
                    // actual weather lightning bolt second. We must mark the lightning call.
                    ordinal = 1))
    private Entity ae2lt$markNaturalWeatherLightning(Entity entity) {
        if (entity instanceof LightningBolt lightningBolt) {
            lightningBolt.getPersistentData().putBoolean(
                    NaturalLightningTransformationHandler.NATURAL_WEATHER_LIGHTNING_TAG,
                    true);
        }
        return entity;
    }

    /**
     * Dispatches lightning processing outside the bolt's virtual tick method. Modded lightning
     * entities such as Ars Nouveau's override {@link LightningBolt#tick()} without invoking the
     * vanilla implementation, so an injection into LightningBolt#tick cannot observe them.
     */
    @ModifyReceiver(
            method = "tickNonPassenger",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private Entity ae2lt$handleLightningTick(Entity entity) {
        if (entity instanceof LightningBolt lightningBolt) {
            NaturalLightningTransformationHandler.handleLightningTick(lightningBolt);
            LightningItemTransformationHandler.handleLightningTick(lightningBolt);
        }
        return entity;
    }

    /**
     * Complements the loot guard for mods that copy LivingEntity#die instead of invoking it.
     * Only the death event byte is suppressed; every unrelated entity event is untouched.
     */
    @Inject(method = "broadcastEntityEvent", at = @At("HEAD"), cancellable = true)
    private void ae2lt$suppressProtectedCopiedDeathAnimation(
            Entity entity,
            byte eventId,
            CallbackInfo ci) {
        if (eventId == EntityEvent.DEATH
                && entity instanceof ServerPlayer player
                && CelestweaveArmorUndyingHandler.protectBeforeDeathSideEffect(player)) {
            ci.cancel();
        }
    }
}
