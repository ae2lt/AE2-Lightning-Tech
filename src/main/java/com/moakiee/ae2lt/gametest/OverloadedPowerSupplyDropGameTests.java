package com.moakiee.ae2lt.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.registry.ModBlocks;

@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class OverloadedPowerSupplyDropGameTests {
    private OverloadedPowerSupplyDropGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void dropsBlockAndInstalledCell(GameTestHelper helper) {
        if (!ModBlocks.hasOverloadedPowerSupply()) {
            var blockId = ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "overloaded_power_supply");
            helper.assertFalse(ForgeRegistries.BLOCKS.containsKey(blockId),
                    "The AppFlux-only power supply must not be registered without AppFlux");
            helper.succeed();
            return;
        }

        var relativePos = BlockPos.ZERO;
        var block = ModBlocks.OVERLOADED_POWER_SUPPLY.get();
        helper.setBlock(relativePos, block);

        var blockEntity = helper.getBlockEntity(relativePos);
        helper.assertTrue(blockEntity instanceof OverloadedPowerSupplyBlockEntity,
                "The placed power supply must create its block entity");

        var fluxCell = ForgeRegistries.ITEMS.getValue(
                ResourceLocation.fromNamespaceAndPath("appflux", "fe_1k_cell"));
        helper.assertTrue(fluxCell != null && fluxCell != Items.AIR,
                "The AppFlux test profile must provide the 1k FE cell");
        var cellStack = new ItemStack(fluxCell);
        var powerSupply = (OverloadedPowerSupplyBlockEntity) blockEntity;
        helper.assertTrue(powerSupply.getCellInventory().isItemValid(0, cellStack),
                "The real AppFlux FE cell must be accepted by the power supply");
        powerSupply.getCellInventory().setItemDirect(0, cellStack);

        var absolutePos = helper.absolutePos(relativePos);
        helper.assertTrue(helper.getLevel().destroyBlock(absolutePos, true),
                "The placed power supply must be destroyable");

        var itemEntities = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, new AABB(absolutePos).inflate(2.0D));
        int blockItemCount = itemEntities.stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(block.asItem()))
                .mapToInt(ItemStack::getCount)
                .sum();
        int cellItemCount = itemEntities.stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(fluxCell))
                .mapToInt(ItemStack::getCount)
                .sum();

        helper.assertTrue(blockItemCount == 1,
                "Expected exactly one overloaded power supply from its actual loot table, got " + itemEntities);
        helper.assertTrue(cellItemCount == 1,
                "Expected exactly one installed FE cell from AE2's additional-drop hook, got " + itemEntities);
        helper.succeed();
    }
}
