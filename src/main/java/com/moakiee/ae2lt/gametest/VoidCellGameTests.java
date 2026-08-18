package com.moakiee.ae2lt.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import appeng.api.config.Actionable;
import appeng.api.config.CondenserOutput;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.core.definitions.AEItems;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.VoidStorageCellItem;
import com.moakiee.ae2lt.me.cell.VoidCellData;
import com.moakiee.ae2lt.me.cell.VoidCellInventory;
import com.moakiee.ae2lt.me.cell.VoidCellMode;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModItems;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class VoidCellGameTests {
    private VoidCellGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void acceptsEveryConfiguredAeKeyType(GameTestHelper helper) {
        assertAccepted(helper, AEItemKey.of(Items.STONE), 1);
        assertAccepted(helper, AEFluidKey.of(Fluids.WATER), 1_000);
        assertAccepted(helper, LightningKey.HIGH_VOLTAGE, 1);

        var unpartitionedStack = new ItemStack(ModItems.VOID_CELL.get());
        var unpartitioned = new VoidCellInventory(unpartitionedStack, null);
        helper.assertTrue(
                unpartitioned.insert(AEItemKey.of(Items.STONE), 1, Actionable.SIMULATE,
                        IActionSource.empty()) == 0,
                "An unpartitioned void cell must reject input like the 1.21 implementation");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void condensesInputIntoExtractableMatterBalls(GameTestHelper helper) {
        int requiredPower = CondenserOutput.MATTER_BALLS.requiredPower;
        helper.assertTrue(requiredPower > 0, "AE2 Matter Ball condenser power must be configured");

        ItemStack stack = configuredCell(AEItemKey.of(Items.STONE));
        VoidCellData.writeMode(stack, VoidCellMode.MATTER_BALLS);
        var cell = new VoidCellInventory(stack, null);
        long inserted = cell.insert(
                AEItemKey.of(Items.STONE), requiredPower, Actionable.MODULATE, IActionSource.empty());
        var matterBall = AEItemKey.of(AEItems.MATTER_BALL);

        helper.assertTrue(inserted == requiredPower, "The complete input amount must be accepted");
        helper.assertTrue(cell.getAvailableStacks().get(matterBall) == 1,
                "One condenser threshold of input must produce one Matter Ball");
        helper.assertTrue(cell.extract(matterBall, 1, Actionable.SIMULATE, IActionSource.empty()) == 1,
                "Produced Matter Balls must remain extractable from the cell");
        helper.succeed();
    }

    private static void assertAccepted(GameTestHelper helper, AEKey key, long amount) {
        var cell = new VoidCellInventory(configuredCell(key), null);
        long accepted = cell.insert(key, amount, Actionable.MODULATE, IActionSource.empty());
        helper.assertTrue(accepted == amount,
                "Configured AE key type was rejected: " + key.getType().getId());
    }

    private static ItemStack configuredCell(AEKey key) {
        var stack = new ItemStack(ModItems.VOID_CELL.get());
        var item = (VoidStorageCellItem) stack.getItem();
        item.getConfigInventory(stack).addFilter(key);
        return stack;
    }
}
