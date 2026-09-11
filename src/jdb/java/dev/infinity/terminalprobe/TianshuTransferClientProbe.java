package dev.infinity.terminalprobe;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;
import java.util.Arrays;
import java.util.List;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.Internal;
import mezz.jei.common.transfer.RecipeTransferUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.common.Mod;

/** Disposable real-client fixture. Never included in the distributed AE2LT jar. */
@Mod("ae2lt_terminal_probe")
public final class TianshuTransferClientProbe {
    private static volatile String report = "loaded";
    private static BlockPos base;
    private static ItemStack planksPattern;
    private static String recipeId = "minecraft:crafting_table";
    public TianshuTransferClientProbe() {}

    public static String command(String command) {
        var mc = Minecraft.getInstance();
        if (command.equals("status")) return status();
        if (command.equals("setup") || command.startsWith("stock:") || command.startsWith("open:")
                || command.equals("inspect") || command.startsWith("expect:") || command.equals("take") || command.equals("assertresult") || command.equals("native") || command.equals("wirelessfix")) {
            if (mc.getSingleplayerServer() == null) return "server not ready";
            mc.getSingleplayerServer().execute(() -> runSafely(() -> serverCommand(command)));
        } else mc.tell(() -> runSafely(() -> clientCommand(command)));
        return "queued " + command;
    }

    private static void runSafely(Runnable work) {
        try { work.run(); }
        catch (Throwable error) { report = "FAIL " + error; error.printStackTrace(); }
        System.out.println("TIANSHU_TRANSFER_PROBE " + report);
    }

    private static ServerPlayer player() {
        return Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst();
    }

    private static void serverCommand(String command) {
        var player = player();
        var level = player.serverLevel();
        if (command.startsWith("expect:")) {
            var menu = (appeng.menu.me.crafting.CraftConfirmMenu) player.containerMenu;
            try {
                var field = appeng.menu.me.crafting.CraftConfirmMenu.class.getDeclaredField("amount");
                field.setAccessible(true);
                int amount = field.getInt(menu);
                if (amount != Integer.parseInt(command.substring(7))) throw new IllegalStateException("Wrong request amount " + amount);
                report = "PASS native CraftConfirmMenu requested amount=" + amount;
            } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
        } else if (command.equals("setup")) {
            player.closeContainer();
            player.getInventory().clearContent();
            base = player.blockPosition().offset(4, 2, 0);
            for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -3), base.offset(2, 2, 2)))
                level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            PartHelper.setPart(level, base, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
            PartHelper.setPart(level, base, Direction.NORTH, player, ModItems.TIANSHU_CRAFTING_TERMINAL.get());
            level.setBlockAndUpdate(base.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            level.setBlockAndUpdate(base.above(), AEBlocks.DRIVE.block().defaultBlockState());
            level.setBlockAndUpdate(base.below(), AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
            level.setBlockAndUpdate(base.west(), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
            level.setBlockAndUpdate(base.west(2), AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
            level.setBlockAndUpdate(base.south(), AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.SOUTH));
            var recipe = level.getRecipeManager().byKey(ResourceLocation.parse("minecraft:oak_planks")).orElseThrow();
            var crafting = (CraftingRecipe) recipe.value();
            var inputs = new ItemStack[9];
            Arrays.fill(inputs, ItemStack.EMPTY);
            inputs[0] = new ItemStack(Items.OAK_LOG);
            planksPattern = PatternDetailsHelper.encodeCraftingPattern(new RecipeHolder<>(recipe.id(), crafting),
                    inputs, new ItemStack(Items.OAK_PLANKS, 4), false, false);
            serverCommand("stock:auto");
            PartHelper.getPartHost(level, base).markForUpdate();
            report = "built real wired/wireless network at " + base;
        } else if (command.equals("wirelessfix")) {
            level.setBlockAndUpdate(base.south(), AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.SOUTH));
            report = "corrected fixture access point facing so its base connects to the cable";
        } else if (command.startsWith("stock:")) {
            player.closeContainer();
            player.getInventory().clearContent();
            var part = (appeng.parts.reporting.CraftingTerminalPart) PartHelper.getPartHost(level, base).getPart(Direction.NORTH);
            var matrix = part.getSubInventory(appeng.parts.reporting.CraftingTerminalPart.INV_CRAFTING);
            for (int i = 0; i < matrix.size(); i++) matrix.setItemDirect(i, ItemStack.EMPTY);
            var cell = AEItems.ITEM_CELL_1K.stack();
            var storage = StorageCells.getCellInventory(cell, null);
            var source = IActionSource.ofPlayer(player);
            storage.insert(AEItemKey.of(Items.OAK_LOG), 64, Actionable.MODULATE, source);
            storage.insert(AEItemKey.of(Items.COBBLESTONE), 64, Actionable.MODULATE, source);
            if (command.contains("smith") || command.contains("atm")) {
                int playerSlot = 9;
                var names = command.contains("atm")
                        ? List.of("allthemodium:allthemodium_upgrade_smithing_template", "minecraft:netherite_sword", "allthemodium:allthemodium_ingot")
                        : List.of("justdirethings:template_blazegold", "justdirethings:ferricore_sword", "justdirethings:blazegold_ingot");
                for (String name : names) {
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(name));
                    if (command.endsWith("player")) player.getInventory().setItem(playerSlot++, new ItemStack(item));
                    else storage.insert(AEItemKey.of(item), 1, Actionable.MODULATE, source);
                }
            }
            if (command.endsWith("full")) storage.insert(AEItemKey.of(Items.OAK_PLANKS), 64, Actionable.MODULATE, source);
            if (command.endsWith("partial")) storage.insert(AEItemKey.of(Items.OAK_PLANKS), 1, Actionable.MODULATE, source);
            if (command.startsWith("stock:ctrl")) {
                var inputs = new ItemStack[9];
                Arrays.fill(inputs, ItemStack.EMPTY);
                String patternRecipe;
                ItemStack output;
                if (command.contains("smith")) {
                    for (String name : List.of("allthemodium:allthemodium_upgrade_smithing_template", "minecraft:netherite_sword")) {
                        var stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(name)));
                        if (stack.is(Items.NETHERITE_SWORD)) {
                            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Smithing Ctrl Proof"));
                            stack.enchant(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                                    .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 2);
                        }
                        player.getInventory().setItem(stack.is(Items.NETHERITE_SWORD) ? 10 : 9, stack);
                    }
                    // Remove the previous ordinary-transfer fixture's ingot/template/base.
                    for (String name : List.of("allthemodium:allthemodium_ingot", "allthemodium:allthemodium_upgrade_smithing_template", "minecraft:netherite_sword"))
                        storage.extract(AEItemKey.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(name))), 64, Actionable.MODULATE, source);
                    inputs[0] = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("allthemodium:allthemodium_block")));
                    output = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("allthemodium:allthemodium_ingot")), 9);
                    patternRecipe = "allthemodium:allthemodium_ingot_from_block";
                } else if (command.contains("stone")) {
                    inputs[0] = inputs[1] = inputs[3] = inputs[4] = new ItemStack(Items.QUARTZ);
                    output = new ItemStack(Items.QUARTZ_BLOCK);
                    patternRecipe = "minecraft:quartz_block";
                } else {
                    var tool = new ItemStack(Items.IRON_PICKAXE);
                    tool.setDamageValue(200);
                    tool.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Anvil Ctrl Proof"));
                    tool.enchant(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                            .getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 2);
                    player.getInventory().setItem(9, tool);
                    if (command.endsWith("partial")) storage.insert(AEItemKey.of(Items.IRON_INGOT), 1, Actionable.MODULATE, source);
                    inputs[0] = new ItemStack(Items.IRON_BLOCK);
                    output = new ItemStack(Items.IRON_INGOT, 9);
                    patternRecipe = "minecraft:iron_ingot_from_iron_block";
                }
                var recipe = level.getRecipeManager().byKey(ResourceLocation.parse(patternRecipe)).orElseThrow();
                var crafting = (CraftingRecipe) recipe.value();
                var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, Arrays.asList(inputs));
                if (!crafting.matches(input, level)) throw new IllegalStateException("Fixture pattern does not match " + patternRecipe);
                planksPattern = PatternDetailsHelper.encodeCraftingPattern(new RecipeHolder<>(recipe.id(), crafting), inputs, output, false, false);
                storage.insert(AEItemKey.of(inputs[0]), 64, Actionable.MODULATE, source);
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.setHealth(player.getMaxHealth());
                player.getAbilities().invulnerable = true;
                player.onUpdateAbilities();
                player.setExperienceLevels(100);
            }
            storage.persist();
            ((DriveBlockEntity) level.getBlockEntity(base.above())).getInternalInventory().setItemDirect(0, cell);
            var provider = ((PatternProviderBlockEntity) level.getBlockEntity(base.west())).getLogic();
            provider.getPatternInv().setItemDirect(0, command.endsWith("missing") ? ItemStack.EMPTY : planksPattern.copy());
            provider.updatePatterns();
            report = "fixture reset " + command;
        } else if (command.equals("open:wired")) {
            player.closeContainer();
            var part = (appeng.parts.AEBasePart) PartHelper.getPartHost(level, base).getPart(Direction.NORTH);
            MenuOpener.open(TianshuCraftingTermMenu.TYPE, player, MenuLocators.forPart(part));
            report = "wired opened " + player.containerMenu.getClass().getName();
        } else if (command.equals("open:wireless")) {
            player.closeContainer();
            player.teleportTo(base.getX() - 3.0, base.getY() - 2.0, base.getZ());
            var item = ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get();
            var stack = new ItemStack(item);
            stack.set(AEComponents.STORED_ENERGY, 1000000.0);
            stack.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(level.dimension(), base.south()));
            player.getInventory().setItem(0, stack);
            player.inventoryMenu.broadcastChanges();
            item.open(player, MenuLocators.forInventorySlot(0), false);
            report = "wireless opened " + player.containerMenu.getClass().getName();
        } else if (command.equals("take") || command.equals("assertresult")) {
            var menu = (TianshuCraftingTermMenu) player.containerMenu;
            var semantic = switch (menu.workPage) {
                case SMITHING -> com.moakiee.ae2lt.menu.Ae2ltSlotSemantics.TIANSHU_SMITHING;
                case ANVIL -> com.moakiee.ae2lt.menu.Ae2ltSlotSemantics.TIANSHU_ANVIL;
                case STONECUTTING -> com.moakiee.ae2lt.menu.Ae2ltSlotSemantics.TIANSHU_STONECUTTING;
                default -> throw new IllegalStateException("Wrong result page " + menu.workPage);
            };
            var slots = menu.getSlots(semantic);
            var output = slots.getLast().getItem().copy();
            if (output.isEmpty()) throw new IllegalStateException("No native result");
            int xp = player.experienceLevel;
            int cost = menu.getAnvil().getCost();
            System.out.println("NATIVE_ANVIL_BEFORE cost=" + cost + " display=" + menu.anvilCost + " creative=" + player.getAbilities().instabuild + " xp=" + xp);
            int beforePoints = -1, chargedPoints = -1;
            Class<?> xpUtil = null;
            try {
                if (net.neoforged.fml.ModList.get().isLoaded("apothic_enchanting")) {
                    xpUtil = Class.forName("dev.shadowsoffire.placebo.util.EnchantmentUtils");
                    beforePoints = (Integer) xpUtil.getMethod("getExperience", net.minecraft.world.entity.player.Player.class).invoke(null, player);
                }
            } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
            if (command.equals("take")) {
                if (menu.workPage == com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage.ANVIL) {
                    var before = slots.getFirst().getItem();
                    if (!java.util.Objects.equals(before.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), output.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME))
                            || !java.util.Objects.equals(before.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS), output.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS)))
                        throw new IllegalStateException("Repair changed name or enchantments");
                    if (output.getDamageValue() >= before.getDamageValue()) throw new IllegalStateException("Repair did not improve durability");
                }
                menu.doAction(player, appeng.helpers.InventoryAction.CRAFT_ITEM, slots.getLast().index, 0);
                if (!ItemStack.matches(output, menu.getCarried())) throw new IllegalStateException("Result was not picked up");
                if (menu.workPage == com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage.ANVIL) {
                    try {
                        if (xpUtil != null) {
                            chargedPoints = beforePoints - (Integer) xpUtil.getMethod("getExperience", net.minecraft.world.entity.player.Player.class).invoke(null, player);
                            int expected = (Integer) xpUtil.getMethod("getTotalExperienceForLevel", int.class).invoke(null, cost);
                            if (chargedPoints != expected) throw new IllegalStateException("Wrong Apothic XP points charge " + chargedPoints + " expected " + expected);
                        } else if (xp - player.experienceLevel != cost) throw new IllegalStateException("Wrong vanilla XP levels charge");
                    } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
                }
            }
            report = "PASS native " + command + " page=" + menu.workPage + "; result=" + output.save(level.registryAccess())
                    + "; xp=" + xp + "->" + player.experienceLevel + "; anvilCost=" + cost + "; chargedXpPoints=" + chargedPoints;
        } else if (command.equals("native")) {
            player.closeContainer();
            var pos = base.east(3);
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.SMITHING_TABLE.defaultBlockState());
            player.openMenu(level.getBlockState(pos).getMenuProvider(level, pos));
            var menu = (net.minecraft.world.inventory.SmithingMenu) player.containerMenu;
            int slot = 0;
            for (String name : List.of("allthemodium:allthemodium_upgrade_smithing_template", "minecraft:netherite_sword", "allthemodium:allthemodium_ingot"))
                menu.getSlot(slot++).set(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(name))));
            menu.broadcastChanges();
            report = "native smithing result=" + menu.getSlot(3).getItem();
        } else if (command.equals("inspect")) {
            var provider = ((PatternProviderBlockEntity) level.getBlockEntity(base.west())).getLogic();
            var grid = provider.getGrid();
            var inventory = grid.getStorageService().getInventory();
            var result = new StringBuilder("server menu=").append(player.containerMenu.getClass().getName());
            var wap = (appeng.blockentity.networking.WirelessAccessPointBlockEntity) level.getBlockEntity(base.south());
            result.append("; player=").append(player.position()).append("; wap=").append(wap.getBlockState())
                    .append("; range=").append(wap.getRange()).append("; sameGrid=").append(wap.getGrid() == grid)
                    .append("; wapNode=").append(wap.getMainNode().isActive());
            result.append("; patterns=").append(provider.getAvailablePatterns().size());
            result.append("; planksCraftable=").append(grid.getCraftingService().isCraftable(AEItemKey.of(Items.OAK_PLANKS)));
            result.append("; storedPlanks=").append(inventory.extract(AEItemKey.of(Items.OAK_PLANKS), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.ofPlayer(player)));
            if (player.containerMenu instanceof TianshuCraftingTermMenu menu) {
                result.append("; page=").append(menu.workPage).append("; xp=").append(player.experienceLevel).append("; cost=").append(menu.anvilCost);
                for (var semantic : List.of(com.moakiee.ae2lt.menu.Ae2ltSlotSemantics.TIANSHU_SMITHING,
                        com.moakiee.ae2lt.menu.Ae2ltSlotSemantics.TIANSHU_STONECUTTING,
                        com.moakiee.ae2lt.menu.Ae2ltSlotSemantics.TIANSHU_ANVIL)) {
                    result.append("; ").append(semantic).append('=');
                    for (var slot : menu.getSlots(semantic)) result.append(slot.getItem().isEmpty() ? "empty" : slot.getItem().save(level.registryAccess())).append(',');
                }
                result.append("; connected=").append(menu.getLinkStatus()).append("; gridSize=")
                        .append(menu.getCraftingMatrix().size()).append("; grid=");
                for (int i = 0; i < menu.getCraftingMatrix().size(); i++) {
                    result.append(menu.getCraftingMatrix().getStackInSlot(i)).append(',');
                }
            }
            report = result.toString();
        }
    }

    @SuppressWarnings("unchecked")
    private static IRecipeLayoutDrawable layout() {
        var runtime = Internal.getJeiRuntime();
        if (recipeId.startsWith("probe:anvil")) {
            var left = new ItemStack(Items.IRON_PICKAXE);
            left.setDamageValue(1);
            var right = new ItemStack(Items.IRON_INGOT, recipeId.endsWith("4") ? 4 : 1);
            var recipe = runtime.getJeiHelpers().getVanillaRecipeFactory().createAnvilRecipe(left, List.of(right),
                    List.of(new ItemStack(Items.IRON_PICKAXE)), ResourceLocation.parse(recipeId));
            return runtime.getRecipeManager().createRecipeLayoutDrawable(runtime.getRecipeManager().getRecipeCategory(RecipeTypes.ANVIL),
                    recipe, runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
        }
        var holder = Minecraft.getInstance().level.getRecipeManager()
                .byKey(ResourceLocation.parse(recipeId)).orElseThrow();
        var manager = runtime.getRecipeManager();
        var category = holder.value() instanceof net.minecraft.world.item.crafting.SmithingRecipe ? RecipeTypes.SMITHING
                : holder.value() instanceof net.minecraft.world.item.crafting.StonecutterRecipe ? RecipeTypes.STONECUTTING : RecipeTypes.CRAFTING;
        return (IRecipeLayoutDrawable) manager.createRecipeLayoutDrawable(manager.getRecipeCategory((mezz.jei.api.recipe.RecipeType) category), holder,
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static void clientCommand(String command) {
        var mc = Minecraft.getInstance();
        if (command.startsWith("emi:")) {
            try {
                report = (String) Class.forName("dev.infinity.terminalprobe.TianshuEmiProbe")
                        .getMethod("command", String.class).invoke(null, command.substring(4));
            } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
        } else if (command.equals("respawn")) {
            mc.player.respawn();
            mc.setScreen(null);
            report = "fixture player respawned";
        } else if (command.startsWith("recipe:")) {
            recipeId = command.substring(7);
            if (recipeId.equals("stone")) recipeId = mc.level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.STONECUTTING).stream()
                    .filter(r -> r.value().getResultItem(mc.level.registryAccess()).is(Items.QUARTZ_BRICKS)
                            && r.value().matches(new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(Items.QUARTZ_BLOCK)), mc.level))
                    .findFirst().orElseThrow().id().toString();
            var layout = layout();
            Internal.getJeiRuntime().getRecipesGui().showRecipes(layout.getRecipeCategory(), List.of(layout.getRecipe()), List.of());
            report = "JEI showing " + recipeId;
        } else if (command.equals("feedback") || command.equals("transfer")) {
            var runtime = Internal.getJeiRuntime();
            var layout = layout();
            var menu = mc.player.containerMenu;
            var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, layout.getRecipeCategory()).orElseThrow();
            var error = RecipeTransferUtil.getTransferRecipeError(runtime.getRecipeTransferManager(), menu, layout, mc.player).orElse(null);
            report = "handler=" + handler.getClass().getName() + "; ctrl=" + Screen.hasControlDown()
                    + "; error=" + (error == null ? "null" : error.getType() + "/" + error.getClass().getName()
                    + "; color=" + Integer.toHexString(error.getButtonHighlightColor()) + "; missing=" + error.getMissingCountHint());
            if (command.equals("transfer")) report += "; transfer=" + RecipeTransferUtil.transferRecipe(
                    runtime.getRecipeTransferManager(), menu, layout, mc.player, false);
        } else if (command.equals("confirm")) {
            var menu = (appeng.menu.me.crafting.CraftConfirmMenu) mc.player.containerMenu;
            report = "confirm noCPU=" + menu.hasNoCPU() + "; plan=" + menu.getPlan();
            menu.startJob();
        } else if (command.startsWith("page:")) {
            ((TianshuCraftingTermMenu) mc.player.containerMenu).setWorkPage(
                    com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage.valueOf(command.substring(5)));
            report = "work page " + command;
        } else if (command.equals("icons")) {
            var previous = mc.screen;
            mc.setScreen(new Screen(Component.literal("Tianshu item rendering")) {
                @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    graphics.fill(0, 0, width, height, 0xFF303030);
                    graphics.drawString(font, "AE2 crafting | Tianshu crafting | Tianshu pattern", 24, 28, -1);
                    graphics.pose().pushPose();
                    graphics.pose().translate(30, 65, 0);
                    graphics.pose().scale(4, 4, 1);
                    graphics.renderItem(AEParts.CRAFTING_TERMINAL.stack(), 0, 0);
                    graphics.renderItem(new ItemStack(ModItems.TIANSHU_CRAFTING_TERMINAL.get()), 28, 0);
                    graphics.renderItem(new ItemStack(ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get()), 56, 0);
                    graphics.pose().popPose();
                }
                @Override public void onClose() { minecraft.setScreen(previous); }
            });
            int ae = mc.getItemColors().getColor(AEParts.CRAFTING_TERMINAL.stack(), 3);
            int lt = mc.getItemColors().getColor(new ItemStack(ModItems.TIANSHU_CRAFTING_TERMINAL.get()), 3);
            report = "icon tint3 AE=" + Integer.toHexString(ae) + "; LT=" + Integer.toHexString(lt);
        } else if (command.equals("close")) { if (mc.screen != null) mc.screen.onClose(); }
    }

    public static String status() {
        var mc = Minecraft.getInstance();
        return report + "; screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())
                + "; clientMenu=" + (mc.player == null ? "none" : mc.player.containerMenu.getClass().getSimpleName());
    }
}
