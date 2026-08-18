package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;

import com.moakiee.ae2lt.celestweave.CelestweaveArmorUndyingHandler;

@Mixin(Entity.class)
public abstract class EntityUndyingMixin {
    /**
     * Inlined death routines may publish ENTITY_DIE without invoking LivingEntity#die or
     * Forge's LivingDeathEvent. Recover the protected player at the common entity game-event
     * boundary so vibration listeners never observe a death that the armor prevented.
     *
     * <p>1.20.1 signatures: {@code gameEvent(GameEvent, Entity)} — the Holder-based
     * overload used by the NeoForge port arrived in 1.20.2.</p>
     */
    @Inject(
            method = "gameEvent(Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void ae2lt$suppressProtectedCopiedDeathGameEvent(
            GameEvent gameEvent,
            Entity sourceEntity,
            CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (gameEvent == GameEvent.ENTITY_DIE
                && entity instanceof ServerPlayer player
                && CelestweaveArmorUndyingHandler.protectBeforeDeathSideEffect(player)) {
            ci.cancel();
        }
    }
}
