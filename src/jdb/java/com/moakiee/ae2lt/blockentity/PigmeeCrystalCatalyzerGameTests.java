package com.moakiee.ae2lt.blockentity;

import appeng.api.AECapabilities;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipeService;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real block/capability/AE grid ticks; no manually invoked machine ticks or free power sources. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class PigmeeCrystalCatalyzerGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final int CATALYST = CrystalCatalyzerInventory.SLOT_CATALYST;
    private static final int OUTPUT = CrystalCatalyzerInventory.SLOT_OUTPUT;

    private static CrystalCatalyzerBlockEntity machine(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.PIGMEE_CRYSTAL_CATALYZER.get());
        return helper.getBlockEntity(POS);
    }

    private static void supply(CrystalCatalyzerBlockEntity host, int catalysts, int water) {
        host.getInventory().setItemDirect(CATALYST, AEBlocks.QUARTZ_BLOCK.stack(catalysts));
        host.getTank().setFluid(new FluidStack(Fluids.WATER, water));
    }

    private static int output(CrystalCatalyzerBlockEntity host) {
        var stack = host.getInventory().getStackInSlot(OUTPUT);
        if (!stack.isEmpty()) require(stack.is(AEItems.CERTUS_QUARTZ_CRYSTAL.asItem()), "wrong output item");
        return stack.getCount();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    @GameTest(template = "pigmee_station_empty", timeoutTicks = 100)
    public static void pigmeeCapabilitiesAndVariantIsolation(GameTestHelper helper) {
        var host = machine(helper);
        helper.runAfterDelay(10, () -> {
            var level = helper.getLevel();
            var pos = helper.absolutePos(POS);
            for (var side : Direction.values()) {
                var items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
                require(items != null, "Pigmee item capability missing on " + side);
                var fluid = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
                require(fluid != null, "Pigmee fluid capability missing on " + side);
                require(level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side) == null,
                        "water-only Pigmee must not accept FE from pipes");
                require(items.insertItem(CATALYST, AEBlocks.QUARTZ_BLOCK.stack(65), true).getCount() == 1,
                        "catalyst insertion must stop at 64");
                require(items.extractItem(CATALYST, 64, false).isEmpty(), "pipes must not remove catalysts");
                require(!items.insertItem(1, new ItemStack(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()), true).isEmpty(),
                        "Pigmee must reject the collapse matrix");
                require(fluid.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.SIMULATE) == 1000,
                        "water pipe simulation must accept one bucket");
            }
            require(level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null) != null,
                    "AE grid node capability missing");
            require(host.getFluid().isEmpty(), "simulated water insertion mutated the tank");
            require(host.getInventory().getStackInSlot(CATALYST).isEmpty(), "simulated catalyst insertion mutated inventory");
            supply(host, 64, 1000);
            host.cycleMode();
            require(host.getMode() == Mode.CRYSTAL, "Pigmee must remain in crystal mode");
            var pigmee = host.findProcessableRecipe().orElseThrow().recipe().value();
            require(pigmee.pigmee() && pigmee.energyPerCycle() == 0 && pigmee.lightningCost() == 0,
                    "Pigmee selected a powered recipe");
            var normal = CrystalCatalyzerRecipeService.findRecipe(level, host.getInventory(), Mode.CRYSTAL, false)
                    .orElseThrow().recipe().value();
            require(!normal.pigmee() && normal.energyPerCycle() > 0, "normal recipe isolation failed");
            var loaded = level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get());
            require(loaded.stream().filter(r -> r.value().pigmee()).count() >= 4, "base Pigmee recipes missing");
            helper.succeed();
        });
    }

    @GameTest(template = "pigmee_station_empty", timeoutTicks = 700)
    public static void pigmeeRunsTwoWaterOnlyCyclesWithoutNetworkPower(GameTestHelper helper) {
        var host = machine(helper);
        long[] firstProgress = {-1};
        long[] firstCompletion = {-1};
        helper.runAfterDelay(20, () -> supply(host, 64, 2000));
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            int amount = output(host);
            if (host.getProcessingTicksSpent() > 0 && firstProgress[0] < 0) firstProgress[0] = tick;
            require(host.getMachineStoredEnergy() == 0 && host.getConsumedEnergy() == 0, "Pigmee used FE");
            if (firstProgress[0] < 0) return;
            require(host.getInventory().getStackInSlot(CATALYST).getCount() == 64, "catalyst was consumed");
            if (amount == 0) require(host.getFluid().getAmount() == 2000, "water spent before completion");
            if (amount == 16 && firstCompletion[0] < 0) {
                require(tick - firstProgress[0] == 299, "first cycle must take exactly 300 active ticks: "
                        + firstProgress[0] + " -> " + tick);
                require(host.getFluid().getAmount() == 1000, "first cycle must consume exactly 1000 mB");
                firstCompletion[0] = tick;
            }
            if (amount == 32) {
                require(tick - firstCompletion[0] == 300, "continuous cycle must take exactly 300 ticks");
                require(host.getFluid().isEmpty(), "second cycle water accounting failed");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "pigmee_station_empty", timeoutTicks = 800)
    public static void pigmeeWaitsForFullCatalystStackAndWater(GameTestHelper helper) {
        var host = machine(helper);
        helper.runAfterDelay(20, () -> supply(host, 63, 1000));
        helper.runAfterDelay(350, () -> {
            require(output(host) == 0 && host.getProcessingTicksSpent() == 0, "63 catalysts started processing");
            require(host.getFluid().getAmount() == 1000, "incomplete catalyst stack spent water");
            supply(host, 64, 999);
        });
        helper.runAfterDelay(400, () -> {
            require(output(host) == 0 && host.getProcessingTicksSpent() == 0, "999 mB started processing");
            host.getTank().fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE);
        });
        helper.onEachTick(() -> {
            if (output(host) == 16) {
                require(helper.getTick() >= 699, "waiting time was counted as active processing");
                require(host.getFluid().isEmpty(), "water recovery did not consume exactly one bucket");
                require(host.getInventory().getStackInSlot(CATALYST).getCount() == 64, "catalyst loss after recovery");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "pigmee_station_empty", timeoutTicks = 900)
    public static void pigmeeOutputBackpressurePausesAndResumes(GameTestHelper helper) {
        var host = machine(helper);
        int[] pausedAt = {-1};
        helper.runAfterDelay(20, () -> supply(host, 64, 1000));
        helper.runAfterDelay(120, () -> {
            require(host.getProcessingTicksSpent() > 0 && output(host) == 0, "fixture never started");
            pausedAt[0] = host.getProcessingTicksSpent();
            host.getInventory().setItemDirect(OUTPUT, AEItems.CERTUS_QUARTZ_CRYSTAL.stack(1016));
        });
        helper.runAfterDelay(450, () -> {
            require(host.getProcessingTicksSpent() == pausedAt[0], "full output failed to pause progress");
            require(host.getFluid().getAmount() == 1000 && output(host) == 1016, "blocked cycle spent resources");
            var items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(POS), Direction.UP);
            require(items != null && items.extractItem(OUTPUT, 1016, true).getCount() == 1016,
                    "output simulation did not expose retained products");
            require(output(host) == 1016, "simulated extraction changed ownership");
            require(items.extractItem(OUTPUT, 1016, false).getCount() == 1016, "output pipe extraction lost products");
        });
        helper.onEachTick(() -> {
            if (helper.getTick() > 450 && output(host) == 16) {
                require(helper.getTick() >= 450 + 300 - pausedAt[0] - 1, "paused time accelerated the recipe");
                require(host.getFluid().isEmpty() && host.getInventory().getStackInSlot(CATALYST).getCount() == 64,
                        "resumed cycle resource accounting failed");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "pigmee_station_empty", timeoutTicks = 500)
    public static void pigmeeSavedProgressAndLegacyEnergyResume(GameTestHelper helper) {
        var host = machine(helper);
        helper.runAfterDelay(20, () -> supply(host, 64, 1000));
        helper.runAfterDelay(150, () -> {
            require(host.getProcessingTicksSpent() > 0 && host.hasLockedRecipe(), "fixture never started");
            int progress = host.getProcessingTicksSpent();
            var tag = new CompoundTag();
            host.saveAdditional(tag, helper.getLevel().registryAccess());
            tag.getCompound("LockedRecipe").putInt("Energy", 400_000);
            tag.putLong("ConsumedEnergy", 123_456);
            host.clearContent();
            host.loadTag(tag, helper.getLevel().registryAccess());
            require(host.getProcessingTicksSpent() == progress, "NBT load lost progress");
            require(host.getLockedRecipe().orElseThrow().totalEnergy() == 0 && host.getConsumedEnergy() == 0,
                    "legacy powered Pigmee snapshot failed migration");
            require(host.getFluid().getAmount() == 1000 && host.getInventory().getStackInSlot(CATALYST).getCount() == 64,
                    "NBT load lost inventory/fluid");
        });
        helper.onEachTick(() -> {
            if (output(host) == 16) {
                require(helper.getTick() < 400, "load restarted the entire cycle");
                var drops = new ArrayList<ItemStack>();
                host.addAdditionalDrops(helper.getLevel(), helper.absolutePos(POS), drops);
                require(drops.stream().filter(s -> s.is(AEBlocks.QUARTZ_BLOCK.asItem())).mapToInt(ItemStack::getCount).sum() == 64,
                        "breaking the machine would lose catalysts");
                require(drops.stream().filter(s -> s.is(AEItems.CERTUS_QUARTZ_CRYSTAL.asItem())).mapToInt(ItemStack::getCount).sum() == 16,
                        "breaking the machine would lose output");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "pigmee_station_empty", timeoutTicks = 400)
    public static void normalCatalyzerDoesNotGainFreeProcessing(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.CRYSTAL_CATALYZER.get());
        CrystalCatalyzerBlockEntity host = helper.getBlockEntity(POS);
        helper.runAfterDelay(20, () -> supply(host, 64, 1000));
        helper.runAfterDelay(350, () -> {
            require(!host.isPigmeeVariant() && output(host) == 0, "normal machine produced without FE/lightning");
            require(host.getFluid().getAmount() == 1000 && host.getInventory().getStackInSlot(CATALYST).getCount() == 64,
                    "normal idle machine spent resources");
            require(helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(POS), Direction.UP) != null,
                    "normal FE capability disappeared");
            helper.succeed();
        });
    }
}
