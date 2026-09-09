package com.moakiee.ae2lt.mixin.staff;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.item.staff.StaffCombat;
import com.moakiee.ae2lt.item.staff.StaffEnergy;
import com.moakiee.ae2lt.item.staff.StaffLightning;
import com.moakiee.ae2lt.item.staff.MimicryStaffItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Every secondary target follows native attack events, enchantments and successful-hit FE payment. */
@Mixin(Player.class)
public abstract class StaffCombatMixin extends LivingEntity {
    @Unique private boolean ae2lt$staffAttackInProgress;
    @Unique private Entity ae2lt$staffPrimaryTarget;
    @Unique private boolean ae2lt$staffPrimaryHit;

    protected StaffCombatMixin(EntityType<? extends LivingEntity> type, Level level) { super(type, level); }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean ae2lt$observePrimaryHit(Entity target, DamageSource source, float damage, Operation<Boolean> original) {
        var player = (Player) (Object) this;
        var staff = player.getMainHandItem();
        StaffLightning.Payment payment = null;
        if (player instanceof ServerPlayer serverPlayer && staff.getItem() instanceof MimicryStaffItem) {
            payment = StaffLightning.reserve(serverPlayer, staff, player.getAttackStrengthScale(.5F) > .9F);
            if (payment == null) return false;
        }
        if (StaffCombat.infinite(staff) && StaffEnergy.canUse(staff)) damage = Float.MAX_VALUE;
        boolean hit = original.call(target, source, damage);
        // Like the railgun, execution may settle a target that rejected the ordinary hurt call.
        // Returning a settled hit retains native enchantment callbacks and charges the base FE exactly once.
        if (player instanceof ServerPlayer serverPlayer && target instanceof LivingEntity living
                && player.getMainHandItem() == staff) hit |= StaffCombat.execute(serverPlayer, staff, living, damage);
        if (!hit && payment != null) payment.refund();
        if (target == ae2lt$staffPrimaryTarget) ae2lt$staffPrimaryHit = hit;
        return hit;
    }

    @WrapMethod(method = "attack")
    private void ae2lt$staffAreaAttack(Entity target, Operation<Void> original) {
        var player = (Player) (Object) this;
        var staff = player.getMainHandItem();
        int mode = StaffCombat.mode(staff);
        if (ae2lt$staffAttackInProgress || !(level() instanceof ServerLevel)
                || !(target instanceof LivingEntity primary) || mode < 2
                || player.getAttackStrengthScale(0.5F) <= 0.9F || !StaffEnergy.canUse(staff)) {
            original.call(target);
            return;
        }

        int chargedTicks = attackStrengthTicker;
        var center = target.position();
        ae2lt$staffAttackInProgress = true;
        ae2lt$staffPrimaryTarget = target;
        ae2lt$staffPrimaryHit = false;
        try {
            original.call(target);
            ae2lt$staffPrimaryTarget = null;
            if (!ae2lt$staffPrimaryHit || player.getMainHandItem() != staff) return;
            int cooldownAfterPrimary = attackStrengthTicker;
            try {
                for (var secondary : StaffCombat.secondaryTargets(player, primary, center, mode)) {
                    if (!player.isAlive() || player.getMainHandItem() != staff || !StaffEnergy.canUse(staff)) break;
                    if (player instanceof ServerPlayer serverPlayer && !StaffLightning.canPay(serverPlayer, staff, true)) break;
                    // One swing uses one captured charge; native attack still resets the final cooldown.
                    attackStrengthTicker = chargedTicks;
                    original.call(secondary);
                }
            } finally {
                attackStrengthTicker = cooldownAfterPrimary;
            }
        } finally {
            ae2lt$staffPrimaryTarget = null;
            ae2lt$staffAttackInProgress = false;
        }
    }
}
