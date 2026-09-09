package com.moakiee.ae2lt.debug;

import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.moakiee.ae2lt.item.staff.*;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

@GameTestHolder("ae2lt_staff")
@PrefixGameTestTemplate(false)
public final class StaffProjectionGameTests {
    private static void require(boolean value, String message) {
        if (!value) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
    private static ItemStack original() {
        var stack = StaffEnergy.charged(new ItemStack(ModItems.MIMICRY_STAFF.get()));
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 37))));
        var data = new CompoundTag(); data.putString("foreign_extension", "preserve_in_private_original");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }
    private static ServerPlayer player(GameTestHelper h, ItemStack stack) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "ProjectionTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, stack);
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX(), pos.getY(), pos.getZ());
        return player;
    }
    private static void install(ItemStack stack, StaffModule type) {
        var modules = StaffState.modules(stack);
        modules.set(type.slot(), new ItemStack(ModItems.MIMICRY_MODULES.get(type).get()));
        StaffState.setModules(stack, modules);
    }
    private static ItemStack lock(ServerPlayer player, ItemStack original) {
        require(StaffPhaseService.lock(player, original), "Lock transfers ownership into the private slot");
        var projection = player.getMainHandItem();
        require(projection.getItem() instanceof StaffProjectionItem && projection != original, "Public inventory contains only dedicated projection item");
        require(StaffPhaseService.resolve(projection) == original, "Active projection resolves the unique original by identity");
        require(!projection.has(DataComponents.CONTAINER) && !projection.has(DataComponents.CUSTOM_DATA)
                && !projection.has(ModDataComponents.MIMICRY_MODULES.get()) && !projection.has(ModDataComponents.MIMICRY_ENERGY.get()), "No real payload is mirrored into projection components");
        return projection;
    }

    @GameTest(template = "staff_empty")
    public static void privateOwnershipCostsAndSingleUnlock(GameTestHelper h) {
        var original = original();
        install(original, StaffModule.HARVEST);
        var player = player(h, original);
        var projection = lock(player, original);
        StaffEnergy.consume(projection, 3);
        require(StaffEnergy.stored(original) == StaffEnergy.CAPACITY - 3000, "Usage debits the private original once");
        require(StaffEnergy.capability(projection).receiveEnergy(1000, false) == 1000, "In-place charging credits the same private buffer");
        require(StaffHubSettings.cycle(projection, player, 2), "Settings edit resolves the original");
        require(StaffState.settings(original).harvest() == 1, "New setting persists only in original");
        require(StaffModuleStorage.INSTANCE.uninstallOne(projection, "ae2lt:mimicry_module_harvest").isEmpty(), "Projection cannot be dismantled via module storage API");
        require(com.moakiee.ae2lt.blockentity.workbench.DeviceWorkbenchAdapters.get(projection).isEmpty(), "Workbench never accepts the projection as a device");
        require(!StaffModuleStorage.INSTANCE.canInstallOne(projection, new ItemStack(ModItems.MIMICRY_MODULES.get(StaffModule.SPEED).get())), "Projection cannot be used as a workbench device");
        var stale = projection.copy();
        require(StaffPhaseService.unlock(player, projection), "Unlock succeeds");
        require(player.getMainHandItem() == original && projection.isEmpty(), "Unlock returns the same real stack and destroys active projection");
        require(StaffPhaseService.resolve(stale).isEmpty() && !StaffPhaseService.unlock(player, stale), "No second unlock is possible");
        require(original.get(DataComponents.CONTAINER).getStackInSlot(0).getCount() == 37
                && original.get(DataComponents.CUSTOM_DATA).copyTag().contains("foreign_extension"), "Nested contents and foreign data survive intact");
        require(StaffEnergy.stored(original) == StaffEnergy.CAPACITY - 2000, "Unlock preserves the actual remaining charge");
        h.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void copiesForeignOwnersAndExpiredGenerationsNeverResolve(GameTestHelper h) {
        var original = original();
        var player = player(h, original);
        var projection = lock(player, original);
        var clone = projection.copy();
        require(StaffPhaseService.resolve(clone).isEmpty() && StaffEnergy.stored(clone) == 0, "Ordinary copied token has no server-side authority");
        var other = player(h, ItemStack.EMPTY);
        other.getInventory().setItem(0, clone);
        require(other.getInventory().items.getFirst().isEmpty(), "Foreign projection is cleared at inventory insertion");
        player.getInventory().setItem(1, projection.copy());
        require(player.getInventory().items.get(1).isEmpty(), "Same-owner projection in wrong slot is cleared at insertion");
        var oldGeneration = projection.get(ModDataComponents.MIMICRY_PROJECTION_LINK.get());
        player.getInventory().setItem(0, ItemStack.EMPTY);
        StaffPhaseService.tick(player);
        var replacement = player.getMainHandItem();
        require(StaffPhaseService.resolve(replacement) == original && replacement != projection, "Replacement creates a handle, not another original");
        require(!oldGeneration.generation().equals(replacement.get(ModDataComponents.MIMICRY_PROJECTION_LINK.get()).generation()), "Replacement rotates the generation");
        require(StaffPhaseService.resolve(projection).isEmpty(), "Old original handle cannot regain authority after replacement");
        require(new ItemEntity(h.getLevel(), 0, 0, 0, replacement).getItem().isEmpty(), "Replacement projection shares ItemEntity fallback");
        var target = new ItemStackHandler(1);
        require(target.insertItem(0, replacement.copy(), true).getCount() == 1 && target.insertItem(0, replacement.copy(), false).getCount() == 1
                && target.getStackInSlot(0).isEmpty(), "Automation refuses projection insertion consistently");
        var wrapper = new PlayerMainInvWrapper(player.getInventory());
        require(wrapper.extractItem(0, 1, true).isEmpty() && wrapper.extractItem(0, 1, false).isEmpty(), "Existing extraction mixins protect active projections");
        var swap = new LivingSwapItemsEvent.Hands(player);
        NeoForge.EVENT_BUS.post(swap);
        require(swap.isCanceled(), "F-key hand swapping is prohibited while locked");
        h.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void nativeMiningSnapshotReadsPrivateHarvestOnlyDuringUse(GameTestHelper h) {
        var original = original();
        install(original, StaffModule.HARVEST);
        install(original, StaffModule.COLLECTION);
        original.set(ModDataComponents.MIMICRY_SETTINGS.get(), StaffSettings.DEFAULT.cycle(2).cycle(2).cycle(7));
        var player = player(h, original);
        var projection = lock(player, original);
        var silk = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
        require(projection.getEnchantmentLevel(silk) == 1 && projection.copy().getEnchantmentLevel(silk) == 0, "Only active handle has virtual harvest enchantment");
        var pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, Blocks.STONE);
        require(player.gameMode.destroyBlock(h.absolutePos(pos)), "Real server game-mode mining succeeds");
        require(player.getInventory().contains(new ItemStack(Items.STONE)) && !player.getInventory().contains(new ItemStack(Items.COBBLESTONE)),
                "Native copied loot tool retains Silk Touch and collection through scoped access");
        require(StaffEnergy.stored(original) == StaffEnergy.CAPACITY - 1000, "Native mining charges the real original exactly once");
        require(projection.copy().getEnchantmentLevel(silk) == 0, "Copies cannot read harvest state outside the native call");
        h.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void respawnAndSavedVaultPreserveOneOriginal(GameTestHelper h) {
        var original = original();
        var player = player(h, original);
        var projection = lock(player, original);
        StaffEnergy.consume(projection, 5);
        player.getInventory().dropAll();
        var replacement = new FakePlayer(h.getLevel(), player.getGameProfile());
        replacement.restoreFrom(player, false);
        var next = replacement.getMainHandItem();
        require(StaffPhaseService.resolve(next) == original && StaffPhaseService.resolve(projection).isEmpty(), "Respawn invalidates the old handle and keeps the private original");
        require(player.getInventory().items.getFirst().isEmpty(), "Dead player's public slot has no valid handle");
        var saved = StaffPhaseVault.get(h.getLevel().getServer()).save(new CompoundTag(), h.getLevel().registryAccess());
        var loaded = StaffPhaseVault.load(saved, h.getLevel().registryAccess());
        var savedAgain = loaded.save(new CompoundTag(), h.getLevel().registryAccess());
        var before = new java.util.HashMap<UUID, CompoundTag>();
        var after = new java.util.HashMap<UUID, CompoundTag>();
        for (var value : saved.getList("Entries", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value; before.put(entry.getUUID("Tool"), entry);
        }
        for (var value : savedAgain.getList("Entries", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value; after.put(entry.getUUID("Tool"), entry);
        }
        require(before.equals(after), "Vault identity, generation, slot and original payload survive round-trip persistence independently of map order");
        require(!next.save(h.getLevel().registryAccess()).toString().contains("foreign_extension"), "Public inventory NBT does not contain the private extension payload");
        require(StaffEnergy.stored(next) == StaffEnergy.CAPACITY - 5000, "Respawn does not reset charge");
        require(StaffPhaseService.unlock(replacement, next) && replacement.getMainHandItem() == original, "Respawned player can unlock the unique original");
        h.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void refillPreservesOtherItemsAndBoundsCopies(GameTestHelper h) {
        var original = original();
        var player = player(h, original);
        var projection = lock(player, original);
        // A bypass writes directly to the list. Admission checks are deliberately bypassed here.
        player.getInventory().items.set(0, new ItemStack(Items.EMERALD, 9));
        player.getInventory().items.set(5, projection.copy());
        require(player.getInventory().getItem(5).isEmpty(), "Invalid raw-list insertion is already invisible to ordinary reads");
        StaffPhaseService.tick(player);
        require(player.getInventory().items.get(5).isEmpty(), "Tick clears bypassed invalid token");
        require(StaffPhaseService.resolve(player.getMainHandItem()) == original, "Reserved position refills from private ownership");
        require(player.getInventory().getItem(1).is(Items.EMERALD) && player.getInventory().getItem(1).getCount() == 9, "Unrelated occupant moves intact into available space");
        h.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void projectedCuttingAndMekanismUseThePrivateModules(GameTestHelper h) throws Exception {
        var original = original();
        for (var type : List.of(StaffModule.KNIFE, StaffModule.COLLECTION, StaffModule.SMELTING, StaffModule.WRENCH)) install(original, type);
        original.set(ModDataComponents.MIMICRY_SETTINGS.get(), new StaffSettings(0, 0, 0, 0, 5, false, true, true));
        var player = player(h, original);
        var projection = lock(player, original);
        var pos = new BlockPos(2, 1, 2);
        if (net.neoforged.fml.ModList.get().isLoaded("farmersdelight")) {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("farmersdelight:cutting_board"));
            h.setBlock(pos, block);
            var board = h.getLevel().getBlockEntity(h.absolutePos(pos));
            var inventory = (ItemStackHandler) board.getClass().getMethod("getInventory").invoke(board);
            inventory.setStackInSlot(0, new ItemStack(Items.BEEF));
            var process = board.getClass().getMethod("processStoredItemUsingTool", ItemStack.class, net.minecraft.world.entity.player.Player.class);
            require((boolean) process.invoke(board, projection, player), "Real cutting board accepts active projected knife ability");
            var patty = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("farmersdelight:beef_patty"));
            require(player.getInventory().countItem(patty) == 2 && inventory.getStackInSlot(0).isEmpty(), "Projected cutting smelts and collects exactly one native recipe");
            require(StaffEnergy.stored(original) == StaffEnergy.CAPACITY - 1000, "Projected cutting debits the real FE buffer once");
        }
        if (net.neoforged.fml.ModList.get().isLoaded("mekanism")) {
            player.setShiftKeyDown(true);
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("mekanism:enrichment_chamber"));
            h.setBlock(pos, block);
            var absolute = h.absolutePos(pos);
            var tile = (mekanism.common.tile.interfaces.ISideConfiguration) h.getLevel().getBlockEntity(absolute);
            var info = tile.getConfig().getConfig(mekanism.common.lib.transmitter.TransmissionType.ITEM);
            var side = mekanism.api.RelativeSide.fromDirections(tile.getDirection(), net.minecraft.core.Direction.UP);
            var before = info.getDataType(side);
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false);
            var context = new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            require(projection.getItem().useOn(context).consumesAction() && info.getDataType(side) != before, "Projected wrench retains native Mek configuration");
            require(!projection.has(mekanism.common.registries.MekanismDataComponents.CONFIGURATOR_MODE.get()), "Native configurator callback leaves no temporary projection component");
            original.set(ModDataComponents.MIMICRY_SETTINGS.get(), new StaffSettings(0, 6, 0, 0, 5, false, false, false));
            var result = h.getLevel().getBlockState(absolute).useItemOn(projection, h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            require(result.consumesAction() && h.getLevel().getBlockState(absolute).isAir(), "Projected wrench retains native Mek dismantling");
        }
        h.succeed();
    }
}
