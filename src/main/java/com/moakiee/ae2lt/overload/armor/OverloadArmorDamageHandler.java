package com.moakiee.ae2lt.overload.armor;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.overload.armor.module.OverloadArmorSubmoduleItem;
import com.moakiee.ae2lt.registry.ModDamageTypes;

/**
 * Applies staged mitigation and reflect tuning from active armor modules.
 *
 * <p>The strongest mitigation stage is applied after vanilla armor in Pre.
 * {@code reflectPct} bounces post-resist damage back to LivingEntity attackers in Post.
 * Environmental damage (fire/fall/drown) is never reflected.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class OverloadArmorDamageHandler {

    private OverloadArmorDamageHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        var capabilities = collectActiveCapabilities(player);
        ActiveCapability mitigation = collectMitigation(capabilities);
        if (mitigation != null
                && mitigation.capability() instanceof DeviceCapability.StagedMitigation staged) {
            float incoming = event.getNewDamage();
            float afterMitigation = ArmorMitigationRules.apply(
                    staged.stage(),
                    classifyDamage(event.getSource()),
                    incoming);
            event.setNewDamage(afterMitigation);
            applyMitigationLoad(mitigation, incoming - afterMitigation);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        float reflected = collectReflectedDamage(player, event.getNewDamage());
        if (reflected > 0.0F) {
            attacker.hurt(event.getSource(), reflected);
        }
    }

    private static ActiveCapability collectMitigation(java.util.List<ActiveCapability> capabilities) {
        ActiveCapability best = null;
        int bestRank = 0;
        for (var active : capabilities) {
            if (active.capability() instanceof DeviceCapability.StagedMitigation mitigation) {
                int rank = mitigationRank(mitigation.stage());
                if (rank > bestRank) {
                    bestRank = rank;
                    best = active;
                }
            }
        }
        return best;
    }

    private static int mitigationRank(String stage) {
        return switch (stage) {
            case "phase_shield" -> 2;
            case "matrix_shield" -> 1;
            default -> 0;
        };
    }

    private static ArmorMitigationRules.DamageClass classifyDamage(DamageSource source) {
        if (isHardDamage(source)) {
            return ArmorMitigationRules.DamageClass.HARD;
        }
        if (isEnvironmentDamage(source)) {
            return ArmorMitigationRules.DamageClass.ENVIRONMENT;
        }
        return ArmorMitigationRules.DamageClass.ORDINARY;
    }

    private static boolean isHardDamage(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_ARMOR)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypeTags.BYPASSES_EFFECTS)
                || source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                || source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(DamageTypes.GENERIC_KILL)
                || source.is(DamageTypes.STARVE)
                || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.WITHER)
                || source.is(DamageTypes.WITHER_SKULL)
                || source.is(ModDamageTypes.ELECTROMAGNETIC);
    }

    private static boolean isEnvironmentDamage(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypes.IN_FIRE)
                || source.is(DamageTypes.ON_FIRE)
                || source.is(DamageTypes.LAVA)
                || source.is(DamageTypes.HOT_FLOOR)
                || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.CRAMMING)
                || source.is(DamageTypes.CACTUS)
                || source.is(DamageTypes.DRY_OUT)
                || source.is(DamageTypes.SWEET_BERRY_BUSH)
                || source.is(DamageTypes.FREEZE)
                || source.is(DamageTypes.STALAGMITE);
    }

    private static void applyMitigationLoad(ActiveCapability mitigation, float preventedDamage) {
        int totalLoad = ArmorDynamicLoadRules.pulseFromAmount(
                preventedDamage,
                AE2LTCommonConfig.overloadArmorMitigationLoadPerDamage());
        if (totalLoad <= 0) {
            return;
        }
        OverloadArmorState.addPulseLoad(
                mitigation.armor(),
                mitigation.submoduleId(),
                totalLoad);
    }

    private static float collectReflectedDamage(Player player, float damage) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return 0.0F;
        }
        if (damage <= 0.0F) {
            return 0.0F;
        }
        float reflected = 0.0F;
        for (var active : collectActiveCapabilities(player)) {
            if (!(active.capability() instanceof DeviceCapability.ReflectTuning reflect)
                    || reflect.reflectPct() <= 0.0D) {
                continue;
            }
            float remaining = Math.max(0.0F, damage - reflected);
            float amount = Math.min(remaining, damage * (float) reflect.reflectPct());
            if (amount <= 0.0F) {
                continue;
            }
            long cost = (long) Math.ceil(amount * Math.max(0L, reflect.fePerDamage()));
            if (cost > 0L) {
                ArmorEnergyBuffer.refillFromNetwork(
                        active.armor(),
                        serverPlayer,
                        Math.max(0L, cost - ArmorEnergyBuffer.read(active.armor(), serverPlayer.registryAccess())));
                if (!ArmorEnergyBuffer.tryConsume(active.armor(), serverPlayer, cost)) {
                    OverloadArmorState.markEnergyUnpaid(active.armor(), "energy");
                    continue;
                }
            }
            reflected += amount;
            int load = ArmorDynamicLoadRules.pulseFromAmount(
                    amount,
                    Math.max(reflect.loadPerDamage(), AE2LTCommonConfig.overloadArmorReflectLoadPerDamage()));
            OverloadArmorState.addPulseLoad(active.armor(), "reflect", load);
            if (reflected >= damage) {
                break;
            }
        }
        return reflected;
    }

    private static java.util.List<ActiveCapability> collectActiveCapabilities(Player player) {
        var out = new java.util.ArrayList<ActiveCapability>();
        for (EquipmentSlot slot : java.util.List.of(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET)) {
            ItemStack armor = player.getItemBySlot(slot);
            if (armor.isEmpty() || !(armor.getItem() instanceof BaseOverloadArmorItem)) {
                continue;
            }
            var snapshot = OverloadArmorState.snapshot(player, armor, player.level().registryAccess(), true);
            if (!snapshot.hasCore() || snapshot.locked()) {
                continue;
            }
            var stacks = OverloadArmorState.loadModuleStacks(armor, player.level().registryAccess());
            for (ItemStack s : stacks) {
                if (!s.isEmpty() && s.getItem() instanceof OverloadDeviceModuleItem m && moduleRuntimeActive(armor, s)) {
                    int count = Math.max(1, s.getCount());
                    for (int i = 0; i < count; i++) {
                        ItemStack unit = s.copyWithCount(1);
                        collectActiveCapabilitiesForUnit(armor, unit, m, out);
                    }
                }
            }
        }
        return out;
    }

    private static void collectActiveCapabilitiesForUnit(
            ItemStack armor,
            ItemStack unit,
            OverloadDeviceModuleItem module,
            java.util.List<ActiveCapability> out) {
        if (!(unit.getItem() instanceof OverloadArmorSubmoduleItem provider)) {
            return;
        }
        provider.collectSubmodules(unit, submodule -> {
            if (submodule == null || !OverloadArmorState.isSubmoduleRuntimeActive(armor, submodule.id())) {
                return;
            }
            for (var capability : module.capabilities(unit)) {
                out.add(new ActiveCapability(armor, submodule.id(), capability));
            }
        });
    }

    private static boolean moduleRuntimeActive(ItemStack armor, ItemStack module) {
        if (!(module.getItem() instanceof OverloadArmorSubmoduleItem provider)) {
            return false;
        }
        boolean[] active = {false};
        provider.collectSubmodules(module, submodule -> {
            if (submodule != null && OverloadArmorState.isSubmoduleRuntimeActive(armor, submodule.id())) {
                active[0] = true;
            }
        });
        return active[0];
    }

    private record ActiveCapability(ItemStack armor, String submoduleId, DeviceCapability capability) {
    }

}
