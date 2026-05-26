package com.moakiee.ae2lt.overload.armor;

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

/**
 * Applies staged mitigation and reflect tuning from active armor modules.
 *
 * <p>{@code passRate} is applied multiplicatively after vanilla armor in Pre.
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
        double passRate = collectPassRate(capabilities);
        if (passRate < 1.0D) {
            float incoming = event.getNewDamage();
            float afterMitigation = incoming * (float) passRate;
            event.setNewDamage(afterMitigation);
            applyMitigationLoad(capabilities, incoming - afterMitigation);
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

    private static double collectPassRate(java.util.List<ActiveCapability> capabilities) {
        double passRate = 1.0D;
        for (var active : capabilities) {
            if (active.capability() instanceof DeviceCapability.StagedMitigation mitigation) {
                passRate *= Math.clamp(mitigation.passRate(), 0.0D, 1.0D);
            }
        }
        return passRate;
    }

    private static void applyMitigationLoad(java.util.List<ActiveCapability> capabilities, float preventedDamage) {
        int totalLoad = ArmorDynamicLoadRules.pulseFromAmount(
                preventedDamage,
                AE2LTCommonConfig.overloadArmorMitigationLoadPerDamage());
        if (totalLoad <= 0) {
            return;
        }
        for (var active : capabilities) {
            if (active.capability() instanceof DeviceCapability.StagedMitigation mitigation) {
                OverloadArmorState.addPulseLoad(active.armor(), "resistance", totalLoad);
            }
        }
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
                        Math.max(0L, cost - ArmorEnergyBuffer.read(active.armor())));
                if (!ArmorEnergyBuffer.tryConsume(active.armor(), serverPlayer, cost)) {
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
                        for (var capability : m.capabilities(unit)) {
                            out.add(new ActiveCapability(armor, capability));
                        }
                    }
                }
            }
        }
        return out;
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

    private record ActiveCapability(ItemStack armor, DeviceCapability capability) {
    }
}
