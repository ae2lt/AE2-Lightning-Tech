package com.moakiee.ae2lt.item.staff;

import java.util.Comparator;
import java.util.List;
import com.moakiee.ae2lt.item.railgun.RailgunExecutionMode;
import com.moakiee.ae2lt.item.railgun.RailgunModuleType;
import com.moakiee.ae2lt.logic.railgun.OverloadExecutionService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class StaffCombat {
    private StaffCombat() {}

    public static int mode(ItemStack stack) {
        return stack.getItem() instanceof MimicryStaffItem && StaffState.has(stack, StaffModule.DAMAGE)
                ? StaffState.settings(stack).combat() : 0;
    }

    public static boolean damageUnlocked(ItemStack stack, int tier) {
        return tier >= 0 && tier <= 6 && (tier < 5
                || tier == 5 && StaffState.has(stack, RailgunModuleType.CORE)
                || tier == 6 && StaffState.has(stack, RailgunModuleType.MULTIDIMENSIONAL_EXECUTION));
    }

    public static int effectiveDamageTier(ItemStack stack) {
        int tier = StaffState.settings(stack).damage();
        while (!damageUnlocked(stack, tier)) tier--;
        return tier;
    }

    public static boolean infinite(ItemStack stack) {
        return stack.getItem() instanceof MimicryStaffItem && StaffState.has(stack, StaffModule.DAMAGE)
                && effectiveDamageTier(stack) == 6;
    }

    public static int baseDamage(ItemStack stack) {
        if (!StaffState.has(stack, StaffModule.DAMAGE)) return 7;
        // Infinity is represented only at the damage call; attributes must stay finite and in range.
        return switch (effectiveDamageTier(stack)) { case 0 -> 1; case 1 -> 5; case 2 -> 10;
            case 3 -> 20; case 4 -> 100; default -> 500; };
    }

    public static boolean hasExecution(ItemStack stack) {
        return StaffState.has(stack, RailgunModuleType.OVERLOAD_EXECUTION)
                || StaffState.has(stack, RailgunModuleType.MULTIDIMENSIONAL_EXECUTION);
    }

    public static boolean execute(ServerPlayer player, ItemStack stack, LivingEntity target, float damage) {
        if (!(stack.getItem() instanceof MimicryStaffItem) || !StaffState.has(stack, StaffModule.DAMAGE)
                || !hasExecution(stack) || player.getAttackStrengthScale(0.5F) <= .9F || !StaffEnergy.canUse(stack)) return false;
        var mode = switch (StaffState.settings(stack).execution()) {
            case 0 -> RailgunExecutionMode.OFF; case 2 -> RailgunExecutionMode.FORCED; default -> RailgunExecutionMode.NORMAL;
        };
        return OverloadExecutionService.onStaffHit(player.serverLevel(), player, StaffPhaseService.resolve(stack), target, damage,
                StaffState.has(stack, RailgunModuleType.MULTIDIMENSIONAL_EXECUTION), mode);
    }

    /** Area modes are horizontal squares, with a three-block vertical window. */
    public static List<LivingEntity> secondaryTargets(Player player, LivingEntity primary, Vec3 center, int mode) {
        if (mode < 2 || mode > 4) return List.of();
        double halfWidth = (mode * 2 - 1) / 2.0;
        var bounds = new AABB(center.x - halfWidth, center.y - 1, center.z - halfWidth,
                center.x + halfWidth, center.y + 2, center.z + halfWidth);
        var targets = player.level().getEntitiesOfClass(LivingEntity.class, bounds, other ->
                other != primary && other != player && other.isAlive() && other.isAttackable()
                && !(other instanceof ArmorStand) && !other.isSpectator() && !player.isAlliedTo(other)
                && !(other instanceof OwnableEntity pet && player.getUUID().equals(pet.getOwnerUUID()))
                && player.hasLineOfSight(other));
        targets.sort(Comparator.comparingDouble((LivingEntity entity) -> entity.distanceToSqr(center))
                .thenComparingInt(LivingEntity::getId));
        return targets;
    }
}
