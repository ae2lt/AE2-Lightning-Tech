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

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;

import com.moakiee.ae2lt.device.capability.DeviceCapability;
import com.moakiee.ae2lt.device.module.OverloadDeviceModuleItem;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.celestweave.ArmorEnergyBuffer;
import com.moakiee.ae2lt.celestweave.ArmorNetworkRechargePolicy;
import com.moakiee.ae2lt.celestweave.ArmorOverloadRules;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.PhaseFlightPlayerState;
import com.moakiee.ae2lt.celestweave.module.PhaseFlightSubmodule;
import com.moakiee.ae2lt.celestweave.service.ArmorLightningService.LightningCost;
import com.moakiee.ae2lt.celestweave.phase.CelestweaveEquipmentAccess;
import com.moakiee.ae2lt.device.network.ArmorNetworkBinding;
import com.moakiee.ae2lt.logic.energy.AppFluxBridge;

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

    /**
     * Pays an active ability cost from equipped armor buffers and then directly from their bound
     * networks. Active abilities must not fail merely because one armor buffer cannot hold their
     * complete atomic cost.
     */
    public static EnergyPayment consumeActiveCostPayment(Player player, ItemStack armor, long amount) {
        return consumeCost(player, armor, amount, true, true);
    }

    private static EnergyPayment consumeBufferedCost(Player player, ItemStack armor, long amount) {
        return consumeCost(player, armor, amount, false, false);
    }

    private static EnergyPayment consumeCost(
            Player player,
            ItemStack armor,
            long amount,
            boolean activeRecharge,
            boolean includeBoundNetworks) {
        if (amount <= 0L) {
            return EnergyPayment.paid(player, List.of(), List.of());
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return EnergyPayment.unpaid(player);
        }
        List<ItemStack> candidates = collectEnergyCandidates(serverPlayer, armor);
        if (activeRecharge) {
            rechargeCandidatesForCost(serverPlayer, candidates, amount);
        }
        return consumePlannedCost(serverPlayer, candidates, amount, includeBoundNetworks);
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
        long storedAfter = ArmorEnergyBuffer.read(armor, player.level().registryAccess());
        long capacity = ArmorEnergyBuffer.capacity(armor, player.level().registryAccess());
        if (storedAfter >= capacity) {
            NEXT_NETWORK_RETRY_TICK.remove(armorId);
        } else if (ArmorNetworkRechargePolicy.shouldThrottlePassiveRetry(storedAfter, capacity, received)) {
            NEXT_NETWORK_RETRY_TICK.put(armorId, ArmorNetworkRechargePolicy.nextRetryTick(now));
        } else {
            NEXT_NETWORK_RETRY_TICK.remove(armorId);
        }
        return received;
    }

    public static void refundCost(ServerPlayer player, ItemStack armor, long amount) {
        if (amount <= 0L) {
            return;
        }
        ArmorEnergyBuffer.write(
                armor,
                player.level().registryAccess(),
                ArmorEnergyBuffer.read(armor, player.level().registryAccess()) + amount);
    }

    private static void rechargeCandidatesForCost(ServerPlayer player, List<ItemStack> candidates, long amount) {
        long remaining = amount;
        for (ItemStack candidate : candidates) {
            if (remaining <= 0L) {
                return;
            }
            long stored = ArmorEnergyBuffer.read(candidate, player.level().registryAccess());
            long capacity = ArmorEnergyBuffer.capacity(candidate, player.level().registryAccess());
            long request = ArmorNetworkRechargePolicy.activeRechargeRequest(stored, capacity, remaining);
            rechargeFromNetwork(player, candidate, request, true);
            remaining -= Math.min(remaining, ArmorEnergyBuffer.read(candidate, player.level().registryAccess()));
        }
    }

    private static EnergyPayment consumePlannedCost(
            ServerPlayer player,
            List<ItemStack> candidates,
            long amount,
            boolean includeBoundNetworks) {
        var sources = new ArrayList<ArmorEnergySpendPlan.Source>();
        for (int i = 0; i < candidates.size(); i++) {
            sources.add(new ArmorEnergySpendPlan.Source(
                    i,
                    ArmorEnergyBuffer.read(candidates.get(i), player.level().registryAccess())));
        }
        List<NetworkEnergySource> networkSources = includeBoundNetworks
                ? collectNetworkEnergySources(player, candidates, amount)
                : List.of();
        int networkSourceOffset = sources.size();
        for (int i = 0; i < networkSources.size(); i++) {
            sources.add(new ArmorEnergySpendPlan.Source(
                    networkSourceOffset + i,
                    networkSources.get(i).available()));
        }
        ArmorEnergySpendPlan plan = ArmorEnergySpendPlan.create(amount, sources);
        if (!plan.canPay()) {
            return EnergyPayment.unpaid(player);
        }
        var debits = new ArrayList<EnergyDebit>();
        var networkDebits = new ArrayList<NetworkEnergyDebit>();
        for (ArmorEnergySpendPlan.Debit debit : plan.debits()) {
            if (debit.sourceIndex() < networkSourceOffset) {
                ItemStack stack = candidates.get(debit.sourceIndex());
                long current = ArmorEnergyBuffer.read(stack, player.level().registryAccess());
                ArmorEnergyBuffer.write(stack, player.level().registryAccess(), current - debit.amount());
                debits.add(new EnergyDebit(stack, debit.amount()));
                continue;
            }
            NetworkEnergySource source = networkSources.get(debit.sourceIndex() - networkSourceOffset);
            long extracted = source.storage().extract(
                    AppFluxBridge.FE_KEY,
                    debit.amount(),
                    Actionable.MODULATE,
                    source.actionSource());
            if (extracted > 0L) {
                networkDebits.add(new NetworkEnergyDebit(source.storage(), source.actionSource(), extracted));
            }
            if (extracted < debit.amount()) {
                refundNetworkDebits(networkDebits);
                refundEnergyDebits(player, debits);
                return EnergyPayment.unpaid(player);
            }
        }
        return EnergyPayment.paid(player, debits, networkDebits);
    }

    private static List<NetworkEnergySource> collectNetworkEnergySources(
            ServerPlayer player,
            List<ItemStack> candidates,
            long maximumRequest) {
        if (maximumRequest <= 0L || !AppFluxBridge.isAvailable() || AppFluxBridge.FE_KEY == null) {
            return List.of();
        }
        Set<IGrid> seenGrids = Collections.newSetFromMap(new IdentityHashMap<>());
        IActionSource actionSource = IActionSource.ofPlayer(player);
        var sources = new ArrayList<NetworkEnergySource>();
        for (ItemStack candidate : candidates) {
            var bound = ArmorNetworkBinding.INSTANCE.resolve(candidate, player);
            IGrid grid = bound.success() ? bound.grid() : null;
            if (grid == null || !seenGrids.add(grid)) {
                continue;
            }
            MEStorage storage = grid.getStorageService().getInventory();
            long available = storage.extract(
                    AppFluxBridge.FE_KEY,
                    maximumRequest,
                    Actionable.SIMULATE,
                    actionSource);
            if (available > 0L) {
                sources.add(new NetworkEnergySource(storage, actionSource, available));
            }
        }
        return List.copyOf(sources);
    }

    private static void refundEnergyDebits(ServerPlayer player, List<EnergyDebit> debits) {
        for (int i = debits.size() - 1; i >= 0; i--) {
            EnergyDebit debit = debits.get(i);
            refundCost(player, debit.armor(), debit.amount());
        }
    }

    private static void refundNetworkDebits(List<NetworkEnergyDebit> debits) {
        if (AppFluxBridge.FE_KEY == null) {
            return;
        }
        for (int i = debits.size() - 1; i >= 0; i--) {
            NetworkEnergyDebit debit = debits.get(i);
            debit.storage().insert(
                    AppFluxBridge.FE_KEY,
                    debit.amount(),
                    Actionable.MODULATE,
                    debit.actionSource());
        }
    }

    /** Fills the preferred piece first, then the remaining equipped/private Celestweave pieces. */
    public static long receiveExternalEnergy(
            ServerPlayer player,
            ItemStack preferredArmor,
            long amount) {
        if (player == null || amount <= 0L) {
            return 0L;
        }
        long remaining = amount;
        long received = 0L;
        for (ItemStack candidate : collectEnergyCandidates(player, preferredArmor)) {
            long accepted = ArmorEnergyBuffer.receiveFe(
                    candidate,
                    player.level().registryAccess(),
                    remaining,
                    false);
            received += accepted;
            remaining -= accepted;
            if (remaining <= 0L) {
                break;
            }
        }
        return received;
    }

    private static List<ItemStack> collectEnergyCandidates(ServerPlayer player, ItemStack preferredArmor) {
        var candidates = new ArrayList<ItemStack>();
        if (isEnergyCandidate(preferredArmor)) {
            candidates.add(preferredArmor);
        }
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack equipped = CelestweaveEquipmentAccess.findArmor(player, slot);
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
            boolean phaseTraversalActive = hasPhaseTraversal(capabilities)
                    && PhaseFlightSubmodule.shouldUsePhaseTraversal(player, armor);
            int count = Math.max(1, entry.count());
            LightningCost moduleLightning = ArmorModuleLightningPolicy.passiveCost(
                            capabilities,
                            movingFlight,
                            phaseTraversalActive,
                            AE2LTCommonConfig.overloadArmorPassiveHvPerTick(),
                            AE2LTCommonConfig.overloadArmorFlightHvPerTick(),
                            AE2LTCommonConfig.overloadArmorPhaseFlightHvPerTick())
                    .times(count);
            lightning = lightning.plus(moduleLightning);
            long moduleDrain = 0L;
            for (DeviceCapability capability : capabilities) {
                if (capability instanceof DeviceCapability.PassiveDrain passiveDrain) {
                    long fePerTick = Math.max(0L, passiveDrain.fePerTick());
                    if (movingFlight) {
                        fePerTick = Math.max(fePerTick, ArmorOverloadRules.FLIGHT_MOVING_DRAIN_FE);
                    }
                    moduleDrain += fePerTick;
                } else if (capability instanceof DeviceCapability.PhaseTraversal traversal
                        && phaseTraversalActive) {
                    moduleDrain = Math.max(moduleDrain, traversal.activeFePerTick());
                } else if (capability instanceof DeviceCapability.EnergyEfficiency efficiency) {
                    multiplier *= Math.max(0.0D, efficiency.drainMul());
                }
            }
            drain += moduleDrain * count;
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

    private static boolean hasPhaseTraversal(List<DeviceCapability> capabilities) {
        for (DeviceCapability capability : capabilities) {
            if (capability instanceof DeviceCapability.PhaseTraversal) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMovingInFlight(ServerPlayer player) {
        if (!player.getAbilities().flying && !PhaseFlightPlayerState.isFlying(player)) {
            return false;
        }
        Vec3 motion = player.getDeltaMovement();
        return motion.lengthSqr() > 1.0E-4D;
    }

    private record PassiveCost(long fe, LightningCost lightning) {
    }

    private record EnergyDebit(ItemStack armor, long amount) {
    }

    private record NetworkEnergySource(MEStorage storage, IActionSource actionSource, long available) {
    }

    private record NetworkEnergyDebit(MEStorage storage, IActionSource actionSource, long amount) {
    }

    public static final class EnergyPayment {
        private final Player player;
        private final boolean paid;
        private final List<EnergyDebit> debits;
        private final List<NetworkEnergyDebit> networkDebits;

        private EnergyPayment(
                Player player,
                boolean paid,
                List<EnergyDebit> debits,
                List<NetworkEnergyDebit> networkDebits) {
            this.player = player;
            this.paid = paid;
            this.debits = List.copyOf(debits);
            this.networkDebits = List.copyOf(networkDebits);
        }

        private static EnergyPayment paid(
                Player player,
                List<EnergyDebit> debits,
                List<NetworkEnergyDebit> networkDebits) {
            return new EnergyPayment(player, true, debits, networkDebits);
        }

        private static EnergyPayment unpaid(Player player) {
            return new EnergyPayment(player, false, List.of(), List.of());
        }

        public boolean paid() {
            return paid;
        }

        public void refund() {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            refundNetworkDebits(networkDebits);
            refundEnergyDebits(serverPlayer, debits);
        }
    }
}
