package com.moakiee.ae2lt.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import appeng.util.InteractionUtil;
import com.moakiee.ae2lt.item.staff.*;
import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runs in an isolated generated world; no test code is packaged in the release jar. */
@GameTestHolder("ae2lt_staff")
@PrefixGameTestTemplate(false)
public final class MimicryStaffGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static void require(boolean value, String message) {
        if (!value) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    private static ItemStack staff() { return StaffEnergy.charged(new ItemStack(ModItems.MIMICRY_STAFF.get())); }
    private static ItemStack module(StaffModule type) { return new ItemStack(ModItems.MIMICRY_MODULES.get(type).get()); }

    private static void install(ItemStack staff, ItemStack module) {
        var entries = StaffState.modules(staff);
        entries.set(StaffState.slotFor(module), module);
        StaffState.setModules(staff, entries);
    }

    private static void remove(ItemStack staff, StaffModule type) {
        var entries = StaffState.modules(staff);
        entries.set(type.slot(), ItemStack.EMPTY);
        StaffState.setModules(staff, entries);
    }

    private static Player player(GameTestHelper helper, ItemStack staff) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, staff);
        player.setOnGround(true);
        return player;
    }

    private static Holder.Reference<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    @GameTest(template = "staff_empty")
    public static void simultaneousCapabilitiesAndHarvestTiers(GameTestHelper helper) {
        var staff = staff();
        require(StaffEnergy.stored(staff) == StaffEnergy.CAPACITY, "Charged FE buffer");
        require(staff.canPerformAction(ItemAbilities.PICKAXE_DIG) && staff.canPerformAction(ItemAbilities.AXE_DIG)
                && staff.canPerformAction(ItemAbilities.SWORD_DIG), "Base pickaxe, axe and sword abilities");
        require(!staff.canPerformAction(ItemAbilities.SHOVEL_DIG) && !staff.canPerformAction(ItemAbilities.SHEARS_DIG), "No implicit modules");
        require(staff.getDestroySpeed(Blocks.STONE.defaultBlockState()) == 8, "Diamond efficiency");
        install(staff, module(StaffModule.MATTOCK));
        install(staff, module(StaffModule.SHEARS));
        require(staff.canPerformAction(ItemAbilities.SHOVEL_DIG) && staff.canPerformAction(ItemAbilities.HOE_DIG), "Mattock adds both abilities");
        require(staff.canPerformAction(ItemAbilities.SHEARS_DIG), "Shearing is independent of Silk Touch");
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), StaffSettings.DEFAULT.cycle(0));
        require(staff.canPerformAction(ItemAbilities.HOE_TILL) && !staff.canPerformAction(ItemAbilities.SHOVEL_FLATTEN), "Only selected land action");
        require(staff.canPerformAction(ItemAbilities.SHOVEL_DIG) && staff.canPerformAction(ItemAbilities.SHEARS_DIG), "Land mode does not disable mining");
        install(staff, module(StaffModule.NETHERITE));
        require(staff.getDestroySpeed(Blocks.STONE.defaultBlockState()) == 9, "Netherite efficiency");
        require(staff.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "Netherite harvest qualification");
        install(staff, module(StaffModule.UNRESTRICTED));
        require(staff.getDestroySpeed(Blocks.STONE.defaultBlockState()) == 9, "Unrestricted tier does not give infinite speed");
        require(!staff.isCorrectToolForDrops(Blocks.SNOW_BLOCK.defaultBlockState()) || StaffState.has(staff, StaffModule.MATTOCK), "Tier must not create tool types");
        remove(staff, StaffModule.MATTOCK);
        require(!staff.canPerformAction(ItemAbilities.SHOVEL_DIG), "Removal immediately revokes mattock");
        require(staff.canPerformAction(ItemAbilities.SHEARS_DIG), "Removal preserves other modules");
        var player = player(helper, staff);
        require(Blocks.BEDROCK.defaultBlockState().getDestroyProgress(player, helper.getLevel(), helper.absolutePos(POS)) == 0, "Highest tier cannot break bedrock");
        helper.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void moduleEnchantmentsMenuAndPersistence(GameTestHelper helper) {
        var staff = staff();
        var player = player(helper, staff);
        var storage = StaffModuleStorage.INSTANCE;
        var fortune = enchantment(helper, Enchantments.FORTUNE);
        var silk = enchantment(helper, Enchantments.SILK_TOUCH);
        var efficiency = enchantment(helper, Enchantments.EFFICIENCY);
        var mending = enchantment(helper, Enchantments.MENDING);
        var upgrade = module(StaffModule.HARVEST);
        upgrade.enchant(fortune, 7);
        upgrade.enchant(mending, 1);
        require(storage.installOne(staff, upgrade), "Device workbench storage accepts the real enchanted module");
        require(!storage.installOne(staff, upgrade), "One module per purpose");
        require(staff.getEnchantmentLevel(fortune) == 0 && staff.getEnchantmentLevel(mending) == 1, "Normal mode retains other enchantments");
        require(StaffHubSettings.cycle(staff, player, 2), "Can select fortune");
        require(staff.getEnchantmentLevel(fortune) == 7, "Fortune VII from the module");
        require(staff.getAllEnchantments(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)).getLevel(fortune) == 7,
                "Single and bulk queries agree");
        require(StaffHubSettings.cycle(staff, player, 2), "Can select silk");
        require(staff.getEnchantmentLevel(fortune) == 0 && staff.getEnchantmentLevel(silk) == 1, "Exclusive harvesting modes");
        staff.enchant(efficiency, 255);
        require(staff.getEnchantmentLevel(efficiency) == 0, "Forced direct efficiency must not become effective");
        var registries = helper.getLevel().registryAccess();
        var restored = ItemStack.parse(registries, staff.save(registries)).orElseThrow();
        require(StaffState.module(restored, StaffModule.HARVEST).getEnchantmentLevel(fortune) == 7, "Module enchantments survive serialization");
        require(restored.getEnchantmentLevel(silk) == 1, "Selected mode survives serialization");
        require(EnchantmentHelper.getEnchantmentsForCrafting(staff).getLevel(silk) == 0, "Virtual Silk Touch cannot be extracted by crafting");
        var removed = storage.uninstallOne(staff, StaffModuleStorage.typeId(upgrade));
        require(removed.getEnchantmentLevel(fortune) == 7, "Workbench removal returns the enchanted module");
        require(staff.getEnchantmentLevel(silk) == 0 && staff.getEnchantmentLevel(mending) == 0, "Removal revokes every module effect");
        require(storage.uninstallOne(staff, StaffModuleStorage.typeId(upgrade)).isEmpty(), "Cannot remove the module twice");
        helper.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void moduleConflictsAndAnvilRules(GameTestHelper helper) {
        var staff = staff();
        var player = player(helper, staff);
        var storage = StaffModuleStorage.INSTANCE;
        var conflict = enchantment(helper, ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse("ae2lt_staff:silk_conflict")));
        var harvest = module(StaffModule.HARVEST);
        require(harvest.supportsEnchantment(conflict), "Data-pack tool enchantments need no whitelist");
        require(!harvest.supportsEnchantment(enchantment(helper, Enchantments.EFFICIENCY)), "Efficiency is rejected");
        require(!harvest.supportsEnchantment(enchantment(helper, Enchantments.SILK_TOUCH)), "Direct silk is rejected");
        require(harvest.supportsEnchantment(enchantment(helper, Enchantments.UNBREAKING)), "Maintenance enchantments remain allowed");
        harvest.enchant(conflict, 1);
        require(storage.installOne(staff, harvest), "Workbench accepts valid loadout");
        require(StaffHubSettings.cycle(staff, player, 2), "Fortune can coexist with fixture enchantment");
        require(!StaffHubSettings.cycle(staff, player, 2), "Silk conflict must reject the entire setting change");
        require(StaffState.settings(staff).harvest() == 1, "Rejected change leaves old mode intact");
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), StaffState.settings(staff).cycle(2));
        require(staff.getAllEnchantments(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)).isEmpty(), "Externally introduced conflicts cannot run");
        var anvil = new AnvilMenu(2, player.getInventory());
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        book.enchant(enchantment(helper, Enchantments.FORTUNE), 3);
        anvil.getSlot(0).set(module(StaffModule.HARVEST));
        anvil.getSlot(1).set(book);
        anvil.createResult();
        require(!anvil.getSlot(2).getItem().isEmpty(), "Anvil must enchant the module");
        anvil.getSlot(0).set(staff());
        anvil.createResult();
        require(anvil.getSlot(2).getItem().isEmpty(), "Anvil must reject direct staff enchanting");
        anvil.getSlot(1).set(ItemStack.EMPTY);
        anvil.setItemName("Named staff");
        require(!anvil.getSlot(2).getItem().isEmpty(), "Renaming the staff must remain possible");
        helper.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void nativeMiningLootAndSpeed(GameTestHelper helper) {
        var staff = staff();
        install(staff, module(StaffModule.SPEED));
        install(staff, module(StaffModule.HARVEST));
        var player = player(helper, staff);
        var level = helper.getLevel();
        var pos = helper.absolutePos(POS);
        var stone = Blocks.STONE.defaultBlockState();
        float original = stone.getDestroyProgress(player, level, pos);
        var settings = new StaffSettings(0, 0, 2, 1, 2, true, false, false);
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), settings);
        require(Math.abs(stone.getDestroyProgress(player, level, pos) - settings.transformProgress(original)) < .00001F,
                "Real BlockState progress must be transformed exactly once");
        var silk = Block.getDrops(stone, level, pos, null, player, staff);
        require(silk.size() == 1 && silk.getFirst().is(Items.STONE), "Native stone loot must see virtual Silk Touch");
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), StaffSettings.DEFAULT);
        var normal = Block.getDrops(stone, level, pos, null, player, staff);
        require(normal.size() == 1 && normal.getFirst().is(Items.COBBLESTONE), "Normal mode must restore native cobblestone");
        install(staff, module(StaffModule.SHEARS));
        var leaves = Block.getDrops(Blocks.OAK_LEAVES.defaultBlockState(), level, pos, null, player, staff);
        require(leaves.size() == 1 && leaves.getFirst().is(Items.OAK_LEAVES), "Shears must collect leaves without Silk Touch");
        helper.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void postProcessingHonorsOverflowAndCancellation(GameTestHelper helper) {
        var staff = staff();
        install(staff, module(StaffModule.SMELTING));
        install(staff, module(StaffModule.COLLECTION));
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), new StaffSettings(0, 0, 0, 0, 5, false, true, true));
        var player = player(helper, staff);
        for (int i = 1; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().setItem(1, new ItemStack(Items.IRON_INGOT, 62));
        var leftovers = StaffDrops.process(helper.getLevel(), player, staff, new ItemStack(Items.RAW_IRON, 5));
        require(player.getInventory().getItem(1).getCount() == 64, "Only two ingots fit");
        require(leftovers.size() == 1 && leftovers.getFirst().is(Items.IRON_INGOT) && leftovers.getFirst().getCount() == 3, "Three ingots must remain");
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), new StaffSettings(0, 0, 0, 0, 5, false, true, false));
        var once = StaffDrops.process(helper.getLevel(), player, staff, new ItemStack(Items.COBBLESTONE, 5));
        require(once.size() == 1 && once.getFirst().is(Items.STONE) && once.getFirst().getCount() == 5, "Do not recursively smelt stone to smooth stone");
        var split = StaffDrops.process(helper.getLevel(), player, staff, new ItemStack(Items.RAW_IRON, 130));
        require(split.size() == 3 && split.stream().mapToInt(ItemStack::getCount).sum() == 130, "Split overflow without loss");
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), new StaffSettings(0, 0, 0, 0, 5, false, false, true));
        player.getInventory().setItem(1, ItemStack.EMPTY);
        var pos = helper.absolutePos(POS);
        Consumer<BlockDropsEvent> cancel = event -> { if (event.getBreaker() == player) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockDropsEvent.class, cancel);
        try {
            var drops = new ArrayList<ItemEntity>();
            drops.add(new ItemEntity(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.DIAMOND, 2)));
            CommonHooks.handleBlockDrops(helper.getLevel(), pos, Blocks.DIAMOND_ORE.defaultBlockState(), null, drops, player, staff);
            require(player.getInventory().getItem(1).isEmpty(), "Canceled drops must not credit the inventory");
        } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        var drops = new ArrayList<ItemEntity>();
        drops.add(new ItemEntity(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.DIAMOND, 2)));
        CommonHooks.handleBlockDrops(helper.getLevel(), pos, Blocks.DIAMOND_ORE.defaultBlockState(), null, drops, player, staff);
        require(player.getInventory().getItem(1).is(Items.DIAMOND) && player.getInventory().getItem(1).getCount() == 2, "Accepted drops collect once");
        helper.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void wrenchAndKnifeRecognitionAreDynamic(GameTestHelper helper) {
        var staff = staff();
        require(!InteractionUtil.canWrenchDisassemble(staff), "No permanent AE2 wrench tag");
        install(staff, module(StaffModule.WRENCH));
        require(InteractionUtil.canWrenchDisassemble(staff) && InteractionUtil.canWrenchRotate(staff), "AE2 both native wrench operations");
        require(staff.canPerformAction(ItemAbility.get("wrench_configure_items")), "Mek defaults to item configuration");
        require(!staff.canPerformAction(ItemAbility.get("wrench_dismantle")) && !staff.canPerformAction(ItemAbility.get("wrench_rotate")), "Configuration must never imply dismantling");
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), new StaffSettings(0, 6, 0, 0, 5, false, false, false));
        require(staff.canPerformAction(ItemAbility.get("wrench_dismantle")) && !staff.canPerformAction(ItemAbility.get("wrench_configure")), "Explicit dismantle only");
        require(InteractionUtil.canWrenchRotate(staff), "Mek mode must not disable AE2 rotation");
        remove(staff, StaffModule.WRENCH);
        require(!InteractionUtil.canWrenchDisassemble(staff), "Uninstall revokes actual AE2 predicate");
        var knives = TagKey.create(Registries.ITEM, ResourceLocation.parse("farmersdelight:tools/knives"));
        var predicate = ItemPredicate.Builder.item().of(knives).build();
        require(!predicate.test(staff), "Without module, knife loot predicate must fail");
        install(staff, module(StaffModule.KNIFE));
        require(predicate.test(staff), "Knife module must satisfy native knife loot predicate");
        remove(staff, StaffModule.KNIFE);
        require(!predicate.test(staff), "Removal must revoke native knife loot predicate");
        require(!staff.canPerformAction(ItemAbility.get("knife_harvest")), "Removal revokes knife harvest");
        helper.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void damageModuleChangesRealAttributeOnce(GameTestHelper helper) {
        var staff = staff();
        var values = new ArrayList<Double>();
        staff.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> { if (attribute.is(Attributes.ATTACK_DAMAGE)) values.add(modifier.amount()); });
        require(values.size() == 1 && values.getFirst() == 6, "Base tool damage modifier must be 6 (7 with player base)");
        install(staff, module(StaffModule.DAMAGE));
        values.clear();
        staff.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> { if (attribute.is(Attributes.ATTACK_DAMAGE)) values.add(modifier.amount()); });
        require(values.size() == 1 && values.getFirst() == 9, "Default enhanced damage is 10 including player base");
        remove(staff, StaffModule.DAMAGE);
        values.clear();
        staff.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> { if (attribute.is(Attributes.ATTACK_DAMAGE)) values.add(modifier.amount()); });
        require(values.size() == 1 && values.getFirst() == 6, "No stale module attack attribute");
        helper.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void nativeRightClickActionsAndShearing(GameTestHelper helper) {
        var staff = staff();
        var player = player(helper, staff);
        var level = helper.getLevel();
        var pos = helper.absolutePos(POS);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        helper.setBlock(POS, Blocks.OAK_LOG);
        require(staff.getItem().useOn(context).consumesAction(), "Auto axe interaction");
        require(level.getBlockState(pos).is(Blocks.STRIPPED_OAK_LOG), "Native stripping result");
        install(staff, module(StaffModule.MATTOCK));
        helper.setBlock(POS, Blocks.GRASS_BLOCK);
        helper.setBlock(POS.above(), Blocks.AIR);
        require(!staff.getItem().useOn(context).consumesAction(), "Land defaults off");
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), StaffSettings.DEFAULT.cycle(0));
        require(staff.getItem().useOn(context).consumesAction() && level.getBlockState(pos).is(Blocks.FARMLAND), "Selected till action");
        helper.setBlock(POS, Blocks.CAMPFIRE);
        require(staff.getItem().useOn(context).consumesAction(), "Campfire dousing is independent of land setting");
        require(!level.getBlockState(pos).getValue(net.minecraft.world.level.block.CampfireBlock.LIT), "Native campfire extinguish");
        install(staff, module(StaffModule.SHEARS));
        install(staff, module(StaffModule.COLLECTION));
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), new StaffSettings(0, 0, 0, 0, 5, false, false, true));
        var sheep = helper.spawn(EntityType.SHEEP, POS.above());
        require(staff.getItem().interactLivingEntity(staff, player, sheep, InteractionHand.MAIN_HAND).consumesAction(), "Native IShearable action");
        require(sheep.isSheared(), "Sheep state must change");
        require(player.getInventory().countItem(Items.WHITE_WOOL) >= 1, "Only operation wool is collected");
        int wool = player.getInventory().countItem(Items.WHITE_WOOL);
        require(!staff.getItem().interactLivingEntity(staff, player, sheep, InteractionHand.MAIN_HAND).consumesAction(), "Already sheared sheep cannot produce again");
        require(player.getInventory().countItem(Items.WHITE_WOOL) == wool, "No double shearing output");
        helper.setBlock(POS, Blocks.PUMPKIN);
        level.getBlockState(pos).useItemOn(staff, level, player, InteractionHand.MAIN_HAND, hit);
        require(level.getBlockState(pos).is(Blocks.CARVED_PUMPKIN) && player.getInventory().countItem(Items.PUMPKIN_SEEDS) == 4,
                "Native carving collects exactly four seeds");
        helper.setBlock(POS, Blocks.BEEHIVE.defaultBlockState().setValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL, 5));
        level.getBlockState(pos).useItemOn(staff, level, player, InteractionHand.MAIN_HAND, hit);
        require(player.getInventory().countItem(Items.HONEYCOMB) == 3, "Native shearing collects exactly three honeycombs");
        require(level.getBlockState(pos).getValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL) == 0,
                "Native hive state is reset once");
        helper.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void customNegativeHardnessAndTierRestrictions(GameTestHelper helper) {
        var ore = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2lt_staff:custom_progress_ore")).defaultBlockState();
        helper.setBlock(POS, ore);
        var staff = staff();
        var player = player(helper, staff);
        var pos = helper.absolutePos(POS);
        var level = helper.getLevel();
        require(ore.getDestroySpeed(level, pos) == -1, "Fixture must have negative standard hardness");
        require(!staff.isCorrectToolForDrops(ore), "Diamond tier cannot harvest the higher-tier fixture");
        install(staff, module(StaffModule.NETHERITE));
        require(!staff.isCorrectToolForDrops(ore), "Netherite still cannot harvest the highest-tier fixture");
        install(staff, module(StaffModule.UNRESTRICTED));
        require(staff.isCorrectToolForDrops(ore), "Unrestricted tier removes the material gate");
        float original = ore.getDestroyProgress(player, level, pos);
        require(Math.abs(original - 9F / 500) < .000001F, "Keep the block's custom progress calculation");
        install(staff, module(StaffModule.SPEED));
        var settings = new StaffSettings(0, 0, 0, 1, 2, true, false, false);
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), settings);
        require(Math.abs(ore.getDestroyProgress(player, level, pos) - settings.transformProgress(original)) < .000001F,
                "Negative hardness is not an automatic speed veto");
        player.setShiftKeyDown(true);
        require(ore.getDestroyProgress(player, level, pos) == 0, "The block's own player restriction must survive");
        helper.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void nativeMekanismConfigurationAndDismantling(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("mekanism")) { helper.succeed(); return; }
        var staff = staff();
        install(staff, module(StaffModule.WRENCH));
        var player = player(helper, staff);
        player.setShiftKeyDown(true);
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:enrichment_chamber"));
        require(block != Blocks.AIR, "Mek fixture exists");
        helper.setBlock(POS, block);
        var level = helper.getLevel();
        var pos = helper.absolutePos(POS);
        var config = (mekanism.common.tile.interfaces.ISideConfiguration) level.getBlockEntity(pos);
        var info = config.getConfig().getConfig(mekanism.common.lib.transmitter.TransmissionType.ITEM);
        var relative = mekanism.api.RelativeSide.fromDirections(config.getDirection(), Direction.UP);
        var before = info.getDataType(relative);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        require(staff.getItem().useOn(context).consumesAction(), "Configurator callback is reachable");
        require(info.getDataType(relative) != before, "Native side configuration actually changes");
        require(level.getBlockState(pos).is(block), "Default configuration must not dismantle the machine");
        require(!staff.has(mekanism.common.registries.MekanismDataComponents.CONFIGURATOR_MODE.get()), "Temporary native mode must not leave duplicate state");
        // Use the target block's native wrench callback, preserving its carried data and security.
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), new StaffSettings(0, 6, 0, 0, 5, false, false, false));
        var result = level.getBlockState(pos).useItemOn(staff, level, player, InteractionHand.MAIN_HAND, hit);
        require(result.consumesAction() && level.getBlockState(pos).isAir(), "Explicit Mek dismantle follows native block behavior");
        helper.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void nativeCuttingBoardModulesAndOutput(GameTestHelper helper) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("farmersdelight")) { helper.succeed(); return; }
        var staff = staff();
        var player = player(helper, staff);
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farmersdelight:cutting_board"));
        helper.setBlock(POS, block);
        var board = helper.getLevel().getBlockEntity(helper.absolutePos(POS));
        var inventory = (net.neoforged.neoforge.items.ItemStackHandler) board.getClass().getMethod("getInventory").invoke(board);
        var process = board.getClass().getMethod("processStoredItemUsingTool", ItemStack.class, Player.class);
        inventory.setStackInSlot(0, new ItemStack(Items.BEEF, 2));
        require(!(boolean) process.invoke(board, staff, player), "Knife recipe needs a knife module");
        require(inventory.getStackInSlot(0).getCount() == 2, "Rejected recipe leaves the input unchanged");
        install(staff, module(StaffModule.KNIFE));
        install(staff, module(StaffModule.COLLECTION));
        install(staff, module(StaffModule.SMELTING));
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), new StaffSettings(0, 0, 0, 0, 5, false, true, true));
        int energy = StaffEnergy.stored(staff);
        require((boolean) process.invoke(board, staff, player), "Native knife recipe must process");
        require(inventory.getStackInSlot(0).getCount() == 1, "Native cutting consumes one input");
        require(StaffEnergy.stored(staff) == energy - StaffEnergy.FE_PER_USE, "Native cutting charges FE once");
        var patty = BuiltInRegistries.ITEM.get(ResourceLocation.parse("farmersdelight:beef_patty"));
        require(player.getInventory().countItem(patty) == 2, "Cutting outputs must smelt and collect exactly once");
        remove(staff, StaffModule.KNIFE);
        inventory.setStackInSlot(0, new ItemStack(Items.OAK_LOG));
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), new StaffSettings(0, 0, 0, 0, 5, false, false, true));
        require((boolean) process.invoke(board, staff, player), "Base axe cutting must work without knife module");
        require(player.getInventory().countItem(Items.STRIPPED_OAK_LOG) == 1, "Native stripped-log product");
        inventory.setStackInSlot(0, new ItemStack(Items.CLAY));
        require(!(boolean) process.invoke(board, staff, player), "Clay recipe needs the mattock module");
        install(staff, module(StaffModule.MATTOCK));
        require((boolean) process.invoke(board, staff, player), "Shovel cutting works with land mode off");
        require(player.getInventory().countItem(Items.CLAY_BALL) == 4, "Native clay output count");
        helper.succeed();
    }
    @GameTest(template = "staff_empty")
    public static void energyPaymentAndEmptyToolGuards(GameTestHelper helper) {
        var staff = new ItemStack(ModItems.MIMICRY_STAFF.get());
        var energy = staff.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM);
        require(energy != null && energy.getEnergyStored() == 0, "Crafted staff exposes an empty FE input");
        require(energy.receiveEnergy(5000, true) == 5000 && energy.getEnergyStored() == 0, "Charging simulation is read-only");
        require(energy.receiveEnergy(Integer.MAX_VALUE, false) == StaffEnergy.CAPACITY, "Charge is bounded");
        require(energy.extractEnergy(1000, false) == 0, "The staff is not a battery output");
        var player = player(helper, staff);
        var harvest = module(StaffModule.HARVEST);
        harvest.enchant(enchantment(helper, Enchantments.UNBREAKING), 10);
        harvest.enchant(enchantment(helper, Enchantments.MENDING), 1);
        install(staff, harvest);
        staff.hurtAndBreak(5, player, EquipmentSlot.MAINHAND);
        require(StaffEnergy.stored(staff) == StaffEnergy.CAPACITY - 5000, "Native durability is converted to FE before Unbreaking");
        require(staff.getCount() == 1 && !staff.isDamaged(), "Energy use cannot break the staff or request Mending");
        staff.setDamageValue(10000);
        require(staff.getDamageValue() == 0, "Physical damage cannot corrupt FE storage");
        var restored = ItemStack.parse(helper.getLevel().registryAccess(), staff.save(helper.getLevel().registryAccess())).orElseThrow();
        require(StaffEnergy.stored(restored) == StaffEnergy.stored(staff), "FE survives serialization");
        staff.set(ModDataComponents.MIMICRY_ENERGY.get(), 999);
        require(!staff.canPerformAction(ItemAbilities.PICKAXE_DIG), "Sub-use energy disables functional abilities");
        var pos = helper.absolutePos(POS);
        require(Blocks.STONE.defaultBlockState().getDestroyProgress(player, helper.getLevel(), pos) == 0, "No power means no mining progress");
        var attack = new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(player, helper.spawn(EntityType.PIG, POS.above()));
        NeoForge.EVENT_BUS.post(attack);
        require(attack.isCanceled(), "An exhausted staff cannot attack");
        helper.setBlock(POS, Blocks.OAK_LOG);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        require(!staff.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "Exhausted stripping fails before changing the world");
        require(helper.getLevel().getBlockState(pos).is(Blocks.OAK_LOG), "No unpaid stripped log");
        staff.set(ModDataComponents.MIMICRY_ENERGY.get(), 1000);
        require(staff.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "Last full use works");
        require(StaffEnergy.stored(staff) == 0 && staff.getCount() == 1, "Last use empties FE and preserves modules");
        helper.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void naturesBlessingPaysBeforeNativeEffect(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("apothic_enchanting")) { helper.succeed(); return; }
        var staff = staff();
        var harvest = module(StaffModule.HARVEST);
        var blessing = enchantment(helper, ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse("apothic_enchanting:natures_blessing")));
        require(harvest.supportsEnchantment(blessing), "Modded hoe enchantment can be stored");
        harvest.enchant(blessing, 1);
        install(staff, harvest);
        require(staff.getEnchantmentLevel(blessing) == 0, "Hoe-only effect requires the mattock");
        install(staff, module(StaffModule.MATTOCK));
        require(staff.getEnchantmentLevel(blessing) == 1, "Mattock enables the enchantment independently of land mode");
        var player = player(helper, staff);
        helper.setBlock(POS.below(), Blocks.FARMLAND);
        helper.setBlock(POS, Blocks.WHEAT);
        var pos = helper.absolutePos(POS);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        staff.set(ModDataComponents.MIMICRY_ENERGY.get(), 4999);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit));
        require(helper.getLevel().getBlockState(pos).getValue(net.minecraft.world.level.block.CropBlock.AGE) == 0, "Insufficient full enchantment cost cannot grow crops");
        require(StaffEnergy.stored(staff) == 4999, "Failed growth charges nothing");
        staff.set(ModDataComponents.MIMICRY_ENERGY.get(), 5000);
        var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        NeoForge.EVENT_BUS.post(event);
        require(event.isCanceled() && event.getCancellationResult().consumesAction(), "Native bonemeal callback succeeds");
        require(helper.getLevel().getBlockState(pos).getValue(net.minecraft.world.level.block.CropBlock.AGE) > 0, "Crop actually grows");
        require(StaffEnergy.stored(staff) == 0 && staff.getCount() == 1, "Native level-I cost is exactly 5000 FE");
        helper.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void existingWorkbenchInstallsAndReturnsEnchantedModules(GameTestHelper helper) {
        var staff = staff();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "StaffWorkbench"));
        var pos = helper.absolutePos(POS);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        helper.setBlock(POS, com.moakiee.ae2lt.registry.ModBlocks.OVERLOAD_DEVICE_WORKBENCH.get());
        var host = (com.moakiee.ae2lt.blockentity.OverloadDeviceWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
        host.getDeviceInventory().setItemDirect(0, staff);
        require(host.hasInstalledDevice(), "Existing workbench recognizes the new device kind");
        require(net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), pos).equals(
                com.moakiee.ae2lt.device.network.RailgunNetworkBinding.INSTANCE.getBoundPos(staff)),
                "Native workbench insertion automatically binds the staff lightning network");
        var menu = new com.moakiee.ae2lt.menu.OverloadDeviceWorkbenchMenu(1, player.getInventory(), host);
        var upgrade = module(StaffModule.HARVEST);
        var fortune = enchantment(helper, Enchantments.FORTUNE);
        upgrade.enchant(fortune, 7);
        require(menu.getSlot(2).mayPlace(upgrade), "Workbench input accepts enchanted module");
        menu.getSlot(2).set(upgrade);
        for (int i = 0; i < menu.INSTALL_TICKS - 1; i++) menu.broadcastChanges();
        require(!StaffState.has(staff, StaffModule.HARVEST), "Installation retains the native workbench delay");
        menu.broadcastChanges();
        require(menu.getSlot(2).getItem().isEmpty(), "Successful installation consumes one input");
        require(StaffState.module(staff, StaffModule.HARVEST).getEnchantmentLevel(fortune) == 7, "Installed module preserves enchantments");
        require(menu.hasCoreInstalled(), "Staff does not request a nonexistent structural core");
        var removed = host.uninstallOneModule(helper.getLevel().registryAccess(), StaffModuleStorage.typeId(module(StaffModule.HARVEST)));
        require(removed.getEnchantmentLevel(fortune) == 7 && !StaffState.has(staff, StaffModule.HARVEST), "Workbench uninstall returns the actual module exactly once");
        player.setItemInHand(InteractionHand.OFF_HAND, staff);
        require(DeviceHubMenu.findStaff(player) == staff, "The old equipment hub finds offhand staff");
        var hub = new DeviceHubMenu(2, player.getInventory(), DeviceHubMenu.TAB_STAFF);
        install(staff, module(StaffModule.SPEED));
        hub.selectModule(0);
        hub.toggleModule(0);
        require(StaffState.settings(staff).speed(), "Existing hub toggle action changes the staff");
        hub.cycleSelectedModuleConfig(0);
        require(StaffState.settings(staff).baseTicks() == 3, "Existing hub config action changes base time");
        var snapshot = StaffHubSettings.snapshot(staff, player, 0);
        require(snapshot.moduleConfigs().size() == 3 && snapshot.powered(), "Staff status uses the old hub synchronization model");
        var core = new ItemStack(ModItems.RAILGUN_MODULE_CORE.get());
        require(menu.getSlot(2).mayPlace(core), "Native workbench admits the existing overload core");
        menu.getSlot(2).set(core);
        for (int i = 0; i < menu.INSTALL_TICKS; i++) menu.broadcastChanges();
        require(StaffState.has(staff, com.moakiee.ae2lt.item.railgun.RailgunModuleType.CORE)
                && menu.getSlot(2).getItem().isEmpty(), "Native workbench consumes and installs core exactly once");
        helper.succeed();
    }

}
