package com.moakiee.ae2lt.item.staff;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_staff")
@PrefixGameTestTemplate(false)
public final class StaffLightningPaymentGameTests {
    @GameTest(template = "staff_empty")
    public static void incompleteSimulationDoesNotTakeAnything(GameTestHelper h) {
        var inventory = new Inventory(16, 1);
        var payment = StaffLightning.reserve(inventory, IActionSource.empty(), new StaffLightning.Cost(1, 2), 16);
        h.assertTrue(payment == null && inventory.hv == 16 && inventory.ehv == 1 && inventory.extractions == 0,
                "A one-HV shortfall must not retain either resource");
        h.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void changedStorageRollsBackBothCurrencies(GameTestHelper h) {
        var inventory = new Inventory(20, 5);
        inventory.hvLimit = 3;
        var payment = StaffLightning.reserve(inventory, IActionSource.empty(), new StaffLightning.Cost(2, 6), 16);
        h.assertTrue(payment == null && inventory.hv == 20 && inventory.ehv == 5,
                "Storage returning less HV than simulated refunds the already-extracted EHV and HV");
        inventory = new Inventory(20, 8);
        inventory.ehvLimit = 2;
        payment = StaffLightning.reserve(inventory, IActionSource.empty(), new StaffLightning.Cost(2, 6), 16);
        h.assertTrue(payment == null && inventory.hv == 20 && inventory.ehv == 8,
                "Partial EHV extraction is also refunded");
        h.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void mixedPaymentAndRefundAreExact(GameTestHelper h) {
        var inventory = new Inventory(20, 5);
        var payment = StaffLightning.reserve(inventory, IActionSource.empty(), new StaffLightning.Cost(2, 6), 16);
        h.assertTrue(payment != null && inventory.hv == 2 && inventory.ehv == 0,
                "Five EHV plus sixteen compensation HV plus two direct HV");
        payment.refund(); payment.refund();
        h.assertTrue(inventory.hv == 20 && inventory.ehv == 5, "Refund is faithful and cannot duplicate resources");
        h.succeed();
    }

    private static final class Inventory implements MEStorage {
        long hv, ehv;
        long hvLimit = Long.MAX_VALUE, ehvLimit = Long.MAX_VALUE;
        int extractions;
        Inventory(long hv, long ehv) { this.hv = hv; this.ehv = ehv; }
        @Override public Component getDescription() { return Component.literal("Changing payment fixture"); }
        @Override public long extract(AEKey key, long amount, Actionable action, IActionSource source) {
            boolean high = key == LightningKey.HIGH_VOLTAGE;
            long available = Math.min(amount, high ? hv : ehv);
            if (action == Actionable.MODULATE) {
                extractions++;
                available = Math.min(available, high ? hvLimit : ehvLimit);
                if (high) hv -= available; else ehv -= available;
            }
            return available;
        }
        @Override public long insert(AEKey key, long amount, Actionable action, IActionSource source) {
            if (action == Actionable.MODULATE) {
                if (key == LightningKey.HIGH_VOLTAGE) hv += amount; else ehv += amount;
            }
            return amount;
        }
    }
}
