package com.moakiee.ae2lt.item.staff;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import com.moakiee.ae2lt.device.energy.LightningCompensationPolicy;
import com.moakiee.ae2lt.item.railgun.RailgunModuleType;
import com.moakiee.ae2lt.logic.railgun.RailgunBinding;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Lightning is reserved before a native hit, then refunded if no hit or execution settles. */
public final class StaffLightning {
    public record Cost(long hv, long ehv) {
        public boolean empty() { return hv == 0 && ehv == 0; }
    }
    private static final Cost FREE = new Cost(0, 0);
    private StaffLightning() {}

    public static Cost cost(ItemStack staff, boolean charged) {
        if (!(staff.getItem() instanceof MimicryStaffItem) || !StaffState.has(staff, StaffModule.DAMAGE)) return FREE;
        long ehv = switch (StaffCombat.effectiveDamageTier(staff)) {
            case 4 -> AE2LTCommonConfig.railgunEhvCostTier1();
            case 5 -> AE2LTCommonConfig.railgunEhvCostTier2();
            case 6 -> AE2LTCommonConfig.railgunEhvCostTier3();
            default -> 0;
        };
        if (charged && StaffCombat.hasExecution(staff) && StaffState.settings(staff).execution() != 0
                && AE2LTCommonConfig.overloadExecutionEnabled()) ehv = Math.max(ehv, AE2LTCommonConfig.railgunEhvCostTier3());
        return new Cost(charged && StaffCombat.mode(staff) != 0 ? 1 : 0, ehv);
    }

    private static int compensation(ItemStack staff) {
        return StaffState.has(staff, RailgunModuleType.CORE)
                ? LightningCompensationPolicy.DEFAULT_HIGH_VOLTAGE_PER_EXTREME_HIGH_VOLTAGE : 0;
    }

    public static boolean canPay(ServerPlayer player, ItemStack staff, boolean charged) {
        if (!StaffPhaseService.mayUse(player, staff)) return false;
        staff = StaffPhaseService.resolve(staff);
        if (staff.isEmpty()) return false;
        Cost cost = cost(staff, charged);
        if (cost.empty()) return true;
        var bound = RailgunBinding.resolve(staff, player);
        return bound.success() && plan(bound.grid().getStorageService().getInventory(), IActionSource.ofPlayer(player), cost,
                compensation(staff)).canPay();
    }

    /** Returns null when the complete bill cannot be reserved, without retaining partial payment. */
    public static Payment reserve(ServerPlayer player, ItemStack staff, boolean charged) {
        if (!StaffPhaseService.mayUse(player, staff)) return null;
        staff = StaffPhaseService.resolve(staff);
        if (staff.isEmpty()) return null;
        Cost cost = cost(staff, charged);
        if (cost.empty()) return Payment.FREE;
        var bound = RailgunBinding.resolve(staff, player);
        if (!bound.success()) {
            player.displayClientMessage(Component.translatable(RailgunBinding.failKey(bound.failure())), true);
            return null;
        }
        var payment = reserve(bound.grid().getStorageService().getInventory(), IActionSource.ofPlayer(player), cost, compensation(staff));
        if (payment == null) player.displayClientMessage(Component.translatable("ae2lt.staff.fail.no_lightning"), true);
        return payment;
    }

    private static LightningCompensationPolicy.Plan plan(MEStorage storage, IActionSource source, Cost cost, int compensation) {
        long availableEhv = cost.ehv == 0 ? 0 : storage.extract(LightningKey.EXTREME_HIGH_VOLTAGE, cost.ehv, Actionable.SIMULATE, source);
        long hvNeeded = cost.hv;
        if (compensation > 0 && availableEhv < cost.ehv) {
            long additional = LightningCompensationPolicy.highVoltageRequired(cost.ehv - availableEhv, compensation);
            hvNeeded = hvNeeded > Long.MAX_VALUE - additional ? Long.MAX_VALUE : hvNeeded + additional;
        }
        long availableHv = hvNeeded == 0 ? 0 : storage.extract(LightningKey.HIGH_VOLTAGE, hvNeeded, Actionable.SIMULATE, source);
        return LightningCompensationPolicy.plan(cost.hv, cost.ehv, availableHv, availableEhv, compensation);
    }

    static Payment reserve(MEStorage storage, IActionSource source, Cost cost, int compensation) {
        var plan = plan(storage, source, cost, compensation);
        if (!plan.canPay()) return null;
        long ehv = plan.extremeHighVoltageToConsume() == 0 ? 0
                : storage.extract(LightningKey.EXTREME_HIGH_VOLTAGE, plan.extremeHighVoltageToConsume(), Actionable.MODULATE, source);
        if (ehv < plan.extremeHighVoltageToConsume()) {
            new Payment(storage, source, 0, ehv).refund();
            return null;
        }
        long hv = plan.highVoltageToConsume() == 0 ? 0
                : storage.extract(LightningKey.HIGH_VOLTAGE, plan.highVoltageToConsume(), Actionable.MODULATE, source);
        if (hv < plan.highVoltageToConsume()) {
            new Payment(storage, source, hv, ehv).refund();
            return null;
        }
        return new Payment(storage, source, hv, ehv);
    }

    public static final class Payment {
        private static final Payment FREE = new Payment(null, null, 0, 0);
        private final MEStorage storage;
        private final IActionSource source;
        private final long hv, ehv;
        private boolean refunded;
        private Payment(MEStorage storage, IActionSource source, long hv, long ehv) {
            this.storage = storage; this.source = source; this.hv = hv; this.ehv = ehv;
        }
        public void refund() {
            if (storage == null || refunded) return;
            refunded = true;
            if (ehv > 0) storage.insert(LightningKey.EXTREME_HIGH_VOLTAGE, ehv, Actionable.MODULATE, source);
            if (hv > 0) storage.insert(LightningKey.HIGH_VOLTAGE, hv, Actionable.MODULATE, source);
        }
    }
}
