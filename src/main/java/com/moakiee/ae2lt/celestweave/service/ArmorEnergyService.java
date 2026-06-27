package com.moakiee.ae2lt.celestweave.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.ArmorNetworkRechargePolicy;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.service.ArmorLightningService.LightningCost;

public final class ArmorEnergyService {
    private static final ConcurrentHashMap<UUID, Long> NEXT_NETWORK_RETRY_TICK = new ConcurrentHashMap<>();
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET);

    private ArmorEnergyService() {
    }

    public static long refillFromBoundNetworkIfLow(Player player, ItemStack armor, HolderLookup.Provider registries) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return 0L;
        }
        long stored = ArmorEnergyBuffer.read(armor, registries);
        long capacity = ArmorEnergyBuffer.capacity(armor, registries);
        long request = ArmorNetworkRechargePolicy.passiveRechargeRequest(stored, capacity);
        return rechargeFromNetwork(serverPlayer, armor, request, false);
    }

    public static boolean consumePassiveDrain(Player player, ItemStack armor, HolderLookup.Provider registries) {
        return consumePassiveDrain(
                player,
                armor,
                CelestweaveArmorState.collectInstalledSubmoduleEntries(armor, registries),
                registries);
    }

    public static boolean consumePassiveDrain(
            Player player,
            ItemStack armor,
            List<CelestweaveArmorState.InstalledSubmodule> installedSubmodules,
            HolderLookup.Provider registries) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        var cost = computePassiveCost(serverPlayer, armor, installedSubmodules, registries);
        if (!ArmorLightningService.hasCost(serverPlayer, armor, cost.lightning())) {
            ArmorResourceFeedback.noLightning(serverPlayer, armor, cost.lightning());
            return false;
        }
        EnergyPayment payment = consumeBufferedCost(serverPlayer, armor, cost.fe());
        if (!payment.paid()) {
            ArmorResourceFeedback.noFe(serverPlayer);
            return false;
        }
        if (ArmorLightningService.consume(serverPlayer, armor, cost.lightning())) {
            return true;
        }
        ArmorResourceFeedback.noLightning(serverPlayer, armor, cost.lightning());
        payment.refund();
        return false;
    }

    public static boolean consumeActiveCost(Player player, ItemStack armor, long amount) {
        return consumeActiveCostPayment(player, armor, amount).paid();
    }

    public static EnergyPayment consumeActiveCostPayment(Player player, ItemStack armor, long amount) {
        return consumeCost(player, armor, amount, true);
    }

    private static EnergyPayment consumeBufferedCost(Player player, ItemStack armor, long amount) {
        return consumeCost(player, armor, amount, false);
    }

    private static EnergyPayment consumeCost(Player player, ItemStack armor, long amount, boolean activeRecharge) {
        if (amount <= 0L) {
            return EnergyPayment.paid(player, List.of());
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return EnergyPayment.unpaid(player);
        }
        List<ItemStack> candidates = collectEnergyCandidates(serverPlayer, armor);
        if (activeRecharge) {
            rechargeCandidatesForCost(serverPlayer, candidates, amount);
        }
        return consumePlannedCost(serverPlayer, candidates, amount);
    }

    private static long rechargeFromNetwork(ServerPlayer player, ItemStack armor, long request, boolean ignoreCooldown) {
        if (request <= 0L) {
            return 0L;
        }
        UUID armorId = CelestweaveArmorState.ensureArmorId(armor);
        long now = player.level().getGameTime();
        if (!ignoreCooldown) {
            long nextRetry = NEXT_NETWORK_RETRY_TICK.getOrDefault(armorId, 0L);
            if (ArmorNetworkRechargePolicy.isCoolingDown(nextRetry, now)) {
                return 0L;
            }
        }

        long received = ArmorEnergyBuffer.refillFromNetwork(armor, player, request);
        if (received >= request) {
            NEXT_NETWORK_RETRY_TICK.remove(armorId);
        } else {
            NEXT_NETWORK_RETRY_TICK.put(armorId, ArmorNetworkRechargePolicy.nextRetryTick(now));
        }
        return received;
    }

    public static void refundCost(ServerPlayer player, ItemStack armor, long amount) {
        if (amount <= 0L) {
            return;
        }
        ArmorEnergyBuffer.write(
                armor,
                player.registryAccess(),
                ArmorEnergyBuffer.read(armor, player.registryAccess()) + amount);
    }

    private static void rechargeCandidatesForCost(ServerPlayer player, List<ItemStack> candidates, long amount) {
        long remaining = amount;
        for (ItemStack candidate : candidates) {
            if (remaining <= 0L) {
                return;
            }
            long stored = ArmorEnergyBuffer.read(candidate, player.registryAccess());
            long capacity = ArmorEnergyBuffer.capacity(candidate, player.registryAccess());
            long request = ArmorNetworkRechargePolicy.activeRechargeRequest(stored, capacity, remaining);
            rechargeFromNetwork(player, candidate, request, true);
            remaining -= Math.min(remaining, ArmorEnergyBuffer.read(candidate, player.registryAccess()));
        }
    }

    private static EnergyPayment consumePlannedCost(ServerPlayer player, List<ItemStack> candidates, long amount) {
        var sources = new ArrayList<ArmorEnergySpendPlan.Source>();
        for (int i = 0; i < candidates.size(); i++) {
            sources.add(new ArmorEnergySpendPlan.Source(
                    i,
                    ArmorEnergyBuffer.read(candidates.get(i), player.registryAccess())));
        }
        ArmorEnergySpendPlan plan = ArmorEnergySpendPlan.create(amount, sources);
        if (!plan.canPay()) {
            return EnergyPayment.unpaid(player);
        }
        var debits = new ArrayList<EnergyDebit>();
        for (ArmorEnergySpendPlan.Debit debit : plan.debits()) {
            ItemStack stack = candidates.get(debit.sourceIndex());
            long current = ArmorEnergyBuffer.read(stack, player.registryAccess());
            ArmorEnergyBuffer.write(stack, player.registryAccess(), current - debit.amount());
            debits.add(new EnergyDebit(stack, debit.amount()));
        }
        return EnergyPayment.paid(player, debits);
    }

    private static List<ItemStack> collectEnergyCandidates(ServerPlayer player, ItemStack preferredArmor) {
        var candidates = new ArrayList<ItemStack>();
        if (isEnergyCandidate(preferredArmor)) {
            candidates.add(preferredArmor);
        }
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack equipped = player.getItemBySlot(slot);
            if (!isEnergyCandidate(equipped) || containsSameArmor(candidates, equipped)) {
                continue;
            }
            candidates.add(equipped);
        }
        return candidates;
    }

    private static boolean isEnergyCandidate(ItemStack armor) {
        return armor != null && !armor.isEmpty() && armor.getItem() instanceof BaseCelestweaveArmorItem;
    }

    private static boolean containsSameArmor(List<ItemStack> candidates, ItemStack armor) {
        UUID armorId = CelestweaveArmorState.getArmorId(armor);
        for (ItemStack candidate : candidates) {
            if (candidate == armor) {
                return true;
            }
            UUID candidateId = CelestweaveArmorState.getArmorId(candidate);
            if (candidateId != null && candidateId.equals(armorId)) {
                return true;
            }
        }
        return false;
    }

    private static PassiveCost computePassiveCost(
            ServerPlayer player,
            ItemStack armor,
            List<CelestweaveArmorState.InstalledSubmodule> installedSubmodules,
            HolderLookup.Provider registries) {
        if (!CelestweaveArmorState.hasCore(armor, registries)) {
            return new PassiveCost(0L, LightningCost.NONE);
        }
        long drain = 0L;
        double multiplier = 1.0D;
        LightningCost lightning = LightningCost.NONE;
        Set<ItemStack> chargedStacks = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : installedSubmodules) {
            if (!CelestweaveArmorState.isSubmoduleEnabled(armor, entry.submodule())) {
                continue;
            }
            ItemStack module = entry.stack();
            if (!(module.getItem() instanceof OverloadDeviceModuleItem provider)) {
                continue;
            }
            if (!chargedStacks.add(module)) {
                continue;
            }
            List<DeviceCapability> capabilities = provider.capabilities(module);
            boolean movingFlight = hasFlightMode(capabilities) && isMovingInFlight(player);
            int count = Math.max(1, entry.count());
            LightningCost moduleLightning = ArmorModuleLightningPolicy.passiveCost(
                            capabilities,
                            movingFlight,
                            AE2LTCommonConfig.overloadArmorPassiveHvPerTick(),
                            AE2LTCommonConfig.overloadArmorFlightHvPerTick(),
                            AE2LTCommonConfig.overloadArmorPhaseFlightHvPerTick())
                    .times(count);
            lightning = lightning.plus(moduleLightning);
            for (DeviceCapability capability : capabilities) {
                if (capability instanceof DeviceCapability.PassiveDrain passiveDrain) {
                    long fePerTick = Math.max(0L, passiveDrain.fePerTick());
                    if (movingFlight) {
                        fePerTick = Math.max(fePerTick, ArmorOverloadRules.FLIGHT_MOVING_DRAIN_FE);
                    }
                    drain += fePerTick * count;
                } else if (capability instanceof DeviceCapability.EnergyEfficiency efficiency) {
                    multiplier *= Math.max(0.0D, efficiency.drainMul());
                }
            }
        }
        return new PassiveCost((long) Math.ceil(drain * multiplier), lightning);
    }

    private static boolean hasFlightMode(List<DeviceCapability> capabilities) {
        for (DeviceCapability capability : capabilities) {
            if (capability instanceof DeviceCapability.FlightMode) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMovingInFlight(ServerPlayer player) {
        if (!player.getAbilities().flying) {
            return false;
        }
        Vec3 motion = player.getDeltaMovement();
        return motion.lengthSqr() > 1.0E-4D;
    }

    private record PassiveCost(long fe, LightningCost lightning) {
    }

    private record EnergyDebit(ItemStack armor, long amount) {
    }

    public static final class EnergyPayment {
        private final Player player;
        private final boolean paid;
        private final List<EnergyDebit> debits;

        private EnergyPayment(Player player, boolean paid, List<EnergyDebit> debits) {
            this.player = player;
            this.paid = paid;
            this.debits = List.copyOf(debits);
        }

        private static EnergyPayment paid(Player player, List<EnergyDebit> debits) {
            return new EnergyPayment(player, true, debits);
        }

        private static EnergyPayment unpaid(Player player) {
            return new EnergyPayment(player, false, List.of());
        }

        public boolean paid() {
            return paid;
        }

        public void refund() {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            for (int i = debits.size() - 1; i >= 0; i--) {
                EnergyDebit debit = debits.get(i);
                refundCost(serverPlayer, debit.armor(), debit.amount());
            }
        }
    }
}
