package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.recipe.PigmeeBuildingRecipe;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native recipe loading, network serialization, consumption and returns; never packaged. */
@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public final class PigmeeBuildingGameTests {
    private static final BlockPos TABLE = new BlockPos(2, 2, 2);

    private static ServerPlayer player(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "BuildingQA"));
        player.getInventory().clearContent();
        return player;
    }

    private static ItemStack pigmee(int count) {
        var stack = new ItemStack(ModFumos.PIGMEE_FUMO_ITEM.get(), count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Pigmee catalyst"));
        return stack;
    }

    private static CraftingMenu table(GameTestHelper helper, ServerPlayer player, int stone, int pigs) {
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        var menu = new CraftingMenu(13, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(TABLE)));
        player.containerMenu = menu;
        for (int slot = 1; slot <= 9; slot++) {
            menu.getSlot(slot).set(slot == 5 ? pigmee(pigs) : new ItemStack(Items.COBBLESTONE, stone));
        }
        return menu;
    }

    private static int materials(ServerPlayer player) {
        return player.getInventory().items.stream().filter(s -> s.is(ModBlocks.PIGMEE_BUILDING_BLOCK.asItem()))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static CraftingInput input() {
        var items = new ArrayList<ItemStack>();
        for (int i = 0; i < 9; i++) items.add(i == 4 ? pigmee(1) : new ItemStack(Items.COBBLESTONE));
        return CraftingInput.of(3, 3, items);
    }

    @GameTest(template = "pigmee_station_empty")
    public static void recipeLoadsIsVisibleAndSurvivesNetworkSync(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = (PigmeeBuildingRecipe) level.getRecipeManager()
                .byKey(ResourceLocation.parse("ae2lt:pigmee_building_block")).orElseThrow().value();
        helper.assertTrue(recipe instanceof ShapedRecipe && !recipe.isSpecial(), "visible shaped recipe for recipe viewers");
        helper.assertTrue(recipe.getWidth() == 3 && recipe.getHeight() == 3, "3 by 3 layout");
        helper.assertTrue(recipe.matches(input(), level), "loaded recipe matches the cobblestone ring");
        helper.assertTrue(!recipe.canCraftInDimensions(2, 2), "player 2 by 2 grid cannot craft this recipe");
        var encoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            var codec = ModRecipeTypes.PIGMEE_BUILDING_SERIALIZER.get().streamCodec();
            codec.encode(encoded, recipe);
            var copy = codec.decode(encoded);
            var result = copy.assemble(input(), level.registryAccess());
            helper.assertTrue(copy.matches(input(), level), "network recipe retains ingredients and shape");
            helper.assertTrue(result.is(ModBlocks.PIGMEE_BUILDING_BLOCK.asItem()) && result.getCount() == 64,
                    "8 cobblestone gives 64 building blocks");
            helper.assertTrue(ItemStack.matches(copy.getRemainingItems(input()).get(4), pigmee(1)),
                    "network recipe still returns the named catalyst");
        } finally {
            encoded.release();
        }
        var wrong = new ArrayList<>(input().items());
        wrong.set(0, ItemStack.EMPTY);
        helper.assertTrue(!recipe.matches(CraftingInput.of(3, 3, wrong), level), "incomplete ring rejected");
        wrong = new ArrayList<>(input().items());
        wrong.set(4, new ItemStack(ModFumos.CREATIVE_PIGMEE_FUMO_ITEM.get()));
        helper.assertTrue(!recipe.matches(CraftingInput.of(3, 3, wrong), level), "only ordinary Pigmee is the catalyst");
        wrong = new ArrayList<>(input().items());
        wrong.set(0, pigmee(1));
        wrong.set(4, new ItemStack(Items.COBBLESTONE));
        helper.assertTrue(!recipe.matches(CraftingInput.of(3, 3, wrong), level), "off-center catalyst rejected");
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void pickupAndShiftCraftPreserveNamedStackedPigmee(GameTestHelper helper) {
        var player = player(helper);
        var menu = table(helper, player, 2, 3);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(ModBlocks.PIGMEE_BUILDING_BLOCK.asItem())
                && menu.getCarried().getCount() == 64, "normal click yields a full stack");
        helper.assertTrue(ItemStack.matches(menu.getSlot(5).getItem(), pigmee(3)), "stacked catalyst unchanged");
        for (int i = 1; i <= 9; i++) {
            if (i != 5) helper.assertTrue(menu.getSlot(i).getItem().getCount() == 1, "one cobblestone consumed per outer slot");
        }
        player.getInventory().add(menu.getCarried());
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(materials(player) == 128, "two crafts yield exactly 128 blocks");
        helper.assertTrue(ItemStack.matches(menu.getSlot(5).getItem(), pigmee(3)), "Pigmee count and components survive repeated crafting");
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "crafting stops when cobblestone runs out");
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void shiftCraftStopsAtFullInventoryWithoutSpendingIngredients(GameTestHelper helper) {
        var player = player(helper);
        var menu = table(helper, player, 3, 1);
        for (int slot = 0; slot < 35; slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(materials(player) == 64, "one free slot allows exactly one craft");
        helper.assertTrue(ItemStack.matches(menu.getSlot(5).getItem(), pigmee(1)), "full inventory does not lose Pigmee");
        for (int i = 1; i <= 9; i++) {
            if (i != 5) helper.assertTrue(menu.getSlot(i).getItem().getCount() == 2, "backpressure must not spend extra cobblestone");
        }
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(materials(player) == 64 && menu.getSlot(1).getItem().getCount() == 2, "blocked retry is inert");
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void otherPigmeeRecipesStillConsumePigmee(GameTestHelper helper) {
        var recipe = (ShapedRecipe) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.parse("ae2lt:pigmee_synthesis_station")).orElseThrow().value();
        var items = recipe.getIngredients().stream()
                .map(ingredient -> ingredient.isEmpty() ? ItemStack.EMPTY : ingredient.getItems()[0].copy()).toList();
        var input = CraftingInput.of(recipe.getWidth(), recipe.getHeight(), items);
        helper.assertTrue(recipe.matches(input, helper.getLevel()), "existing station fixture matches");
        var remaining = recipe.getRemainingItems(input);
        for (int i = 0; i < input.size(); i++) {
            if (input.getItem(i).is(ModFumos.PIGMEE_FUMO_ITEM.get())) {
                helper.assertTrue(remaining.get(i).isEmpty(), "existing recipes still consume ordinary Pigmee");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void basicBlockHasSimplePlacementAndSelfDrop(GameTestHelper helper) {
        var block = ModBlocks.PIGMEE_BUILDING_BLOCK.get();
        var player = player(helper);
        helper.setBlock(TABLE, block);
        var state = helper.getLevel().getBlockState(helper.absolutePos(TABLE));
        helper.assertTrue(!state.hasBlockEntity(), "floor must not create a block entity");
        var tool = new ItemStack(Items.WOODEN_PICKAXE);
        helper.assertTrue(tool.isCorrectToolForDrops(state), "wooden pickaxe is sufficient");
        var drops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(TABLE), null, player, tool);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()) && drops.getFirst().getCount() == 1,
                "Pigmee building block drops exactly itself");
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void allThirtyTwoPanelsPlaceAndDropWithoutBlockEntities(GameTestHelper helper) {
        helper.assertTrue(ModBlocks.PIGMEE_BUILDING_PANELS.size() == 16
                && ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS.size() == 16, "exactly 16 colors of each style");
        var player = player(helper);
        var tool = new ItemStack(Items.WOODEN_PICKAXE);
        var ids = new java.util.HashSet<net.minecraft.world.level.block.Block>();
        for (var panels : java.util.List.of(ModBlocks.PIGMEE_BUILDING_PANELS, ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS)) {
            for (DyeColor color : DyeColor.values()) {
                var block = panels.get(color).get();
                helper.assertTrue(ids.add(block), "each style/color has a distinct block identity");
                helper.assertTrue(block.color() == color, "registered color matches map key");
                helper.setBlock(TABLE, block);
                var state = helper.getLevel().getBlockState(helper.absolutePos(TABLE));
                helper.assertTrue(!state.hasBlockEntity(), "decorative panels have no block entities");
                helper.assertTrue(tool.isCorrectToolForDrops(state), "all panels are mineable with a wooden pickaxe");
                var drops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(TABLE), null, player, tool);
                helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem())
                        && drops.getFirst().getCount() == 1, "every color/style drops exactly itself");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void allInputsOfferAllThirtyTwoDyeFreeStonecuttingOutputs(GameTestHelper helper) {
        var player = player(helper);
        helper.setBlock(TABLE, Blocks.STONECUTTER);
        var outputs = panels();
        var inputs = new ArrayList<>(outputs);
        inputs.add(ModBlocks.PIGMEE_BUILDING_BLOCK.get());
        var expected = outputs.stream().map(Block::asItem).collect(java.util.stream.Collectors.toSet());
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            var input = inputs.get(inputIndex);
            var menu = new StonecutterMenu(14, player.getInventory(),
                    ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(TABLE)));
            player.containerMenu = menu;
            menu.getSlot(0).set(new ItemStack(input));
            helper.assertTrue(menu.getRecipes().size() == 32, "every basic/finished input offers all 32 choices");
            var actual = new java.util.HashSet<net.minecraft.world.item.Item>();
            for (var recipe : menu.getRecipes()) {
                var result = recipe.value().getResultItem(helper.getLevel().registryAccess());
                helper.assertTrue(result.getCount() == 1, "every conversion is strictly 1:1");
                actual.add(result.getItem());
            }
            helper.assertTrue(actual.equals(expected), "all 16 colors and both styles are selectable without dye");
            cut(helper, player, input, outputs.get((inputIndex + 7) % outputs.size()));
        }
        for (var output : outputs) cut(helper, player, ModBlocks.PIGMEE_BUILDING_BLOCK.get(), output);
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void finishedPanelsReturnOneBasicBlockInBothCraftingGrids(GameTestHelper helper) {
        var player = player(helper);
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        for (var block : panels()) {
            var table = new CraftingMenu(13, player.getInventory(),
                    ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(TABLE)));
            player.containerMenu = table;
            table.getSlot(9).set(new ItemStack(block, 3));
            table.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(table.getCarried().is(ModBlocks.PIGMEE_BUILDING_BLOCK.asItem())
                            && table.getCarried().getCount() == 1 && table.getSlot(9).getItem().getCount() == 2,
                    "workbench consumes one finished panel and returns one basic block");
            table.setCarried(ItemStack.EMPTY);
            var inventory = player.inventoryMenu;
            player.containerMenu = inventory;
            for (int slot = 1; slot <= 4; slot++) inventory.getSlot(slot).set(ItemStack.EMPTY);
            inventory.getSlot(4).set(new ItemStack(block, 3));
            inventory.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(inventory.getCarried().is(ModBlocks.PIGMEE_BUILDING_BLOCK.asItem())
                            && inventory.getCarried().getCount() == 1 && inventory.getSlot(4).getItem().getCount() == 2,
                    "backpack 2x2 consumes one finished panel and returns one basic block");
            inventory.setCarried(ItemStack.EMPTY);
        }
        helper.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void conversionRecipesDoNotAcceptUnrelatedMaterials(GameTestHelper helper) {
        var player = player(helper);
        helper.setBlock(TABLE, Blocks.STONECUTTER);
        var panelItems = panels().stream().map(Block::asItem).collect(java.util.stream.Collectors.toSet());
        var restoration = (net.minecraft.world.item.crafting.ShapelessRecipe) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.parse("ae2lt:pigmee_building_block_from_panels")).orElseThrow().value();
        for (var item : java.util.List.of(Items.COBBLESTONE, Items.STONE, Items.WHITE_CONCRETE, Items.RED_DYE,
                ModBlocks.PIGMEE_BUILDING_BLOCK.asItem())) {
            helper.assertTrue(!restoration.matches(CraftingInput.of(1, 1, java.util.List.of(new ItemStack(item))),
                    helper.getLevel()), "only a finished panel matches the crafting return recipe");
            if (item == ModBlocks.PIGMEE_BUILDING_BLOCK.asItem()) continue;
            var menu = new StonecutterMenu(14, player.getInventory(),
                    ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(TABLE)));
            player.containerMenu = menu;
            menu.getSlot(0).set(new ItemStack(item));
            helper.assertTrue(menu.getRecipes().stream().noneMatch(recipe -> panelItems.contains(
                    recipe.value().getResultItem(helper.getLevel().registryAccess()).getItem())),
                    "ordinary stone, concrete, and dyes cannot enter Pigmee conversion recipes");
        }
        var twoPanels = java.util.List.of(new ItemStack(panels().getFirst()), new ItemStack(panels().getLast()));
        helper.assertTrue(!restoration.matches(CraftingInput.of(2, 1, twoPanels), helper.getLevel()),
                "two separate input panels cannot be silently consumed for one output");
        helper.succeed();
    }

    private static java.util.List<Block> panels() {
        var result = new ArrayList<Block>();
        for (DyeColor color : DyeColor.values()) {
            result.add(ModBlocks.PIGMEE_BUILDING_PANELS.get(color).get());
            result.add(ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS.get(color).get());
        }
        return result;
    }

    private static void cut(GameTestHelper helper, ServerPlayer player, Block input, Block expected) {
        var menu = new StonecutterMenu(14, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(TABLE)));
        player.containerMenu = menu;
        menu.getSlot(0).set(new ItemStack(input, 3));
        int selected = -1;
        for (int i = 0; i < menu.getRecipes().size(); i++) {
            if (menu.getRecipes().get(i).value().getResultItem(helper.getLevel().registryAccess()).is(expected.asItem())) {
                selected = i;
                break;
            }
        }
        helper.assertTrue(selected >= 0 && menu.clickMenuButton(player, selected), "requested style is offered by the real stonecutter");
        helper.assertTrue(menu.getSlot(1).getItem().is(expected.asItem()) && menu.getSlot(1).getItem().getCount() == 1,
                "cutting offers exactly 1 output of the expected color/style");
        menu.clicked(1, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getSlot(0).getItem().getCount() == 2 && menu.getCarried().is(expected.asItem())
                && menu.getCarried().getCount() == 1, "one cut consumes exactly 1 input and delivers 1 output");
        menu.setCarried(ItemStack.EMPTY);
    }
}
