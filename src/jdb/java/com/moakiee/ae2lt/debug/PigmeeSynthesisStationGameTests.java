package com.moakiee.ae2lt.debug;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.core.definitions.AEBlocks;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.CraftingTermSlot;
import appeng.helpers.InventoryAction;
import appeng.parts.reporting.CraftingTerminalPart;
import com.moakiee.ae2lt.blockentity.PigmeeSynthesisStationBlockEntity;
import com.moakiee.ae2lt.menu.PigmeeSynthesisStationMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-world regression tests; jdb sources are excluded from the published jar. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class PigmeeSynthesisStationGameTests {
    private static final BlockPos STATION = new BlockPos(2, 2, 2);
    private static final BlockPos SOURCE = STATION.below();

    private static PigmeeSynthesisStationBlockEntity station(GameTestHelper helper) {
        helper.setBlock(STATION, ModBlocks.PIGMEE_SYNTHESIS_STATION.get());
        return helper.getBlockEntity(STATION);
    }

    @GameTest(template = "pigmee_station_empty")
    public static void liveStorageAndPower(GameTestHelper helper) {
        var host = station(helper);
        var inventory = host.getInventory(); // Open before attaching storage.
        var key = AEItemKey.of(Items.IRON_INGOT);
        var action = IActionSource.empty();
        helper.assertTrue(!host.getLinkStatus().connected(), "Empty station must be disconnected");
        helper.setBlock(SOURCE, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(SOURCE);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
        helper.assertTrue(host.getInventory() == inventory, "Menu storage identity must remain stable");
        helper.assertTrue(inventory.getAvailableStacks().get(key) == 16, "Attached chest must appear without reopening");
        helper.assertTrue(StorageHelper.poweredExtraction(host, inventory, key, 3, action) == 3,
                "Standalone extraction must need no AE network");
        helper.assertTrue(chest.getItem(0).getCount() == 13, "Extraction must debit the real chest");
        helper.assertTrue(inventory.insert(key, 2, Actionable.SIMULATE, action) == 2, "Simulated insertion");
        helper.assertTrue(chest.getItem(0).getCount() == 13, "Simulation must not mutate chest");
        helper.setBlock(SOURCE, Blocks.AIR);
        helper.assertTrue(inventory.extract(key, 64, Actionable.MODULATE, action) == 0,
                "Removed chest must not remain accessible through an open menu");
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.assertTrue(inventory.getAvailableStacks().get(key) == 0, "Replacement chest must be empty");
        helper.assertTrue(StorageHelper.poweredInsert(host, inventory, key, 4, action) == 4,
                "Standalone insertion must work");
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void rejectsMeInterface(GameTestHelper helper) {
        var host = station(helper);
        helper.setBlock(SOURCE, AEBlocks.INTERFACE.block());
        helper.runAfterDelay(5, () -> {
            var capability = helper.getLevel().getCapability(AECapabilities.ME_STORAGE,
                    helper.absolutePos(SOURCE), Direction.UP);
            helper.assertTrue(capability != null, "Fixture must actually expose ME_STORAGE");
            helper.assertTrue(!host.getLinkStatus().connected(), "ME interface must be rejected");
            helper.assertTrue(host.getInventory().insert(AEItemKey.of(Items.IRON_INGOT), 1,
                    Actionable.MODULATE, IActionSource.empty()) == 0, "ME interface must not accept station writes");
            helper.setBlock(STATION.east(), Blocks.CHEST);
            helper.assertTrue(host.getLinkStatus().connected(), "Rejected side must not hide another valid source");
            helper.succeed();
        });
    }

    @GameTest(template = "pigmee_station_empty")
    public static void menuCraftingAndPersistence(GameTestHelper helper) {
        var host = station(helper);
        var matrix = host.getSubInventory(CraftingTerminalPart.INV_CRAFTING);
        helper.assertTrue(matrix.size() == 9, "AE2 crafting inventory ID must resolve all nine slots");
        matrix.setItemDirect(0, new ItemStack(Items.OAK_LOG, 2));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = new PigmeeSynthesisStationMenu(1, player.getInventory(), host);
        helper.assertTrue(menu.getCraftingMatrix() == matrix, "Menu must use the persistent matrix");
        var output = menu.getSlots(SlotSemantics.CRAFTING_RESULT).getFirst();
        helper.assertTrue(output.getItem().is(Items.OAK_PLANKS) && output.getItem().getCount() == 4,
                "Crafting terminal must resolve the real vanilla recipe");
        player.containerMenu = menu;
        ((CraftingTermSlot) output).doClick(InventoryAction.CRAFT_ITEM, player);
        helper.assertTrue(menu.getCarried().is(Items.OAK_PLANKS) && menu.getCarried().getCount() == 4,
                "Taking the result must craft four planks");
        helper.assertTrue(matrix.getStackInSlot(0).getCount() == 1, "Crafting must consume one log");
        matrix.setItemDirect(0, new ItemStack(Items.OAK_LOG, 2));
        var registries = helper.getLevel().registryAccess();
        var tag = new CompoundTag();
        host.saveAdditional(tag, registries);
        host.clearContent();
        helper.assertTrue(matrix.getStackInSlot(0).isEmpty(), "Clear content must clear the grid");
        host.loadTag(tag, registries);
        helper.assertTrue(matrix.getStackInSlot(0).getCount() == 2, "NBT round trip must retain grid items");
        var drops = new ArrayList<ItemStack>();
        host.addAdditionalDrops(helper.getLevel(), helper.absolutePos(STATION), drops);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getCount() == 2, "Grid contents must drop on removal");
        helper.succeed();
    }
}
