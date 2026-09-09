package com.moakiee.ae2lt.debug;

import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.moakiee.ae2lt.item.staff.*;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.*;

@GameTestHolder("ae2lt_staff")
@PrefixGameTestTemplate(false)
public final class StaffPhaseLockGameTests {
    private static void require(boolean value, String message) {
        if (!value) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    private static ItemStack locked() {
        var stack = StaffEnergy.charged(new ItemStack(ModItems.MIMICRY_STAFF.get()));
        stack.set(ModDataComponents.MIMICRY_PHASE_LOCK.get(), true);
        // A third-party attached inventory is represented by an actual transferable component.
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 37))));
        return stack;
    }

    private static ServerPlayer player(GameTestHelper h, ItemStack stack) {
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "StaffPhaseTest"));
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, stack);
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        p.setPos(pos.getX(), pos.getY(), pos.getZ());
        return p;
    }

    private static void intact(ItemStack expected, Player player) {
        require((player.getMainHandItem() == expected || StaffPhaseService.resolve(player.getMainHandItem()) == expected) && expected.getCount() == 1, "The unique original stack remains in its source slot");
        require(expected.get(DataComponents.CONTAINER).getStackInSlot(0).getCount() == 37, "All 37 nested diamonds are preserved");
    }

    @GameTest(template = "staff_empty")
    public static void simulatedAndActualExtractionPreservePayload(GameTestHelper h) {
        var stack = locked();
        var p = player(h, stack);
        for (IItemHandler inv : List.of(new InvWrapper(p.getInventory()), new PlayerMainInvWrapper(p.getInventory()),
                new PlayerInvWrapper(p.getInventory()), new EntityHandsInvWrapper(p))) {
            require(inv.extractItem(0, 64, true).isEmpty(), "Simulated extraction refuses protected payload");
            require(inv.extractItem(0, 64, false).isEmpty(), "Actual extraction refuses protected payload");
            intact(stack, p);
        }
        require(p.getInventory().removeItem(0, 1).isEmpty() && p.getInventory().removeItemNoUpdate(0).isEmpty(), "Native removals also return no item");
        p.setItemSlot(EquipmentSlot.OFFHAND, stack);
        p.getInventory().setItem(0, ItemStack.EMPTY);
        var offhand = new PlayerOffhandInvWrapper(p.getInventory());
        require(offhand.extractItem(0, 1, true).isEmpty() && offhand.extractItem(0, 1, false).isEmpty(), "Offhand wrapper protects both modes");
        var projection = new ItemStack(ModItems.PHASE_LOCK_PROJECTION.get());
        projection.set(DataComponents.CONTAINER, stack.get(DataComponents.CONTAINER));
        p.setItemSlot(EquipmentSlot.CHEST, projection);
        var armor = new EntityArmorInvWrapper(p);
        require(armor.extractItem(2, 1, true).isEmpty() && armor.extractItem(2, 1, false).isEmpty(), "Equipment wrapper refuses armor projection");
        require(p.getItemBySlot(EquipmentSlot.CHEST) == projection, "Projection stays in its armor slot");
        var copy = stack.copy();
        require(ItemStack.matches(copy, stack) && copy != stack, "Normal copy and network snapshots preserve all components");
        var parsed = ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow();
        require(ItemStack.matches(parsed, stack), "Lock and third-party data survive serialization");
        stack.set(ModDataComponents.MIMICRY_PHASE_LOCK.get(), false);
        require(offhand.extractItem(0, 1, true).getCount() == 1, "Unlock restores normal simulation");
        require(offhand.extractItem(0, 1, false).get(DataComponents.CONTAINER).getStackInSlot(0).getCount() == 37
                && p.getOffhandItem().isEmpty(), "Unlock transfers one complete real stack");
        h.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void guiTransferAdmissionAndUnlock(GameTestHelper h) {
        var stack = locked();
        var p = player(h, stack);
        var chest = new SimpleContainer(27);
        var menu = ChestMenu.threeRows(7, p.getInventory(), chest);
        int source = 54;
        for (ClickType type : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.THROW, ClickType.SWAP)) {
            menu.clicked(source, 0, type, p);
            intact(stack, p);
            require(menu.getCarried().isEmpty() && chest.isEmpty(), "No payload escaped into cursor or chest: " + type);
        }
        menu.clicked(0, 0, ClickType.SWAP, p);
        require(chest.isEmpty(), "Number-key swap cannot insert the locked hotbar stack into a chest");
        require(menu.quickMoveStack(p, source).isEmpty(), "Direct native quickMoveStack call also refuses transfer");
        require(menu.getSlot(source).safeTake(1, 64, p).isEmpty(), "Slot.safeTake refuses protected source");
        intact(stack, p);
        menu.setCarried(stack.copy());
        menu.clicked(-999, 0, ClickType.PICKUP, p);
        menu.clicked(0, 0, ClickType.PICKUP, p);
        menu.clicked(0, 1, ClickType.QUICK_CRAFT, p);
        menu.clicked(0, 2, ClickType.QUICK_CRAFT, p);
        require(chest.isEmpty() && !menu.getCarried().isEmpty(), "Even an injected locked cursor cannot drop, insert or drag");
        menu.setCarried(ItemStack.EMPTY);
        var snapshot = StaffHubSettings.snapshot(stack, p, -1);
        require(snapshot.moduleConfigs().size() == 1 && snapshot.moduleConfigs().getFirst().key().equals("staff_phase_lock"),
                "Bare staff can be unlocked without any module");
        require(StaffHubSettings.cycle(stack, p, 11), "Unlock remains available");
        menu.clicked(source, 0, ClickType.QUICK_MOVE, p);
        require(p.getMainHandItem().isEmpty() && chest.getItem(0).get(DataComponents.CONTAINER).getStackInSlot(0).getCount() == 37,
                "Unlocked GUI transfer moves payload exactly once");
        h.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void nativeDropDeathAndRespawnMoveTheOriginal(GameTestHelper h) {
        var stack = locked();
        var p = player(h, stack);
        require(!p.drop(false) && !p.drop(true), "Q and Ctrl-Q refuse before removing source");
        require(p.drop(stack, true, false) == null, "Direct Player.drop also refuses");
        p.getInventory().setItem(1, new ItemStack(Items.DIRT, 3));
        p.getInventory().dropAll();
        intact(stack, p);
        require(p.getInventory().getItem(1).isEmpty(), "Ordinary inventory items still drop normally");
        var next = player(h, ItemStack.EMPTY);
        boolean previous = h.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        try {
            h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, h.getLevel().getServer());
            next.restoreFrom(p, false);
            require(StaffPhaseService.resolve(next.getMainHandItem()) == stack && p.getMainHandItem().isEmpty(), "Respawn transfers the retained original and empties the dead source");
            var kept = new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(), next.getGameProfile());
            h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, h.getLevel().getServer());
            kept.restoreFrom(next, false);
            require(StaffPhaseService.resolve(kept.getMainHandItem()) == stack && next.getMainHandItem().isEmpty(), "keepInventory does not leave a second source reference");
        } finally {
            h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(previous, h.getLevel().getServer());
        }
        h.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void itemEntityConstructorsSettersAndNbtDiscardPayload(GameTestHelper h) {
        var stack = locked();
        var p = player(h, stack);
        var entity = new ItemEntity(h.getLevel(), 0, 0, 0, stack);
        require(entity.getItem().isEmpty(), "ItemEntity constructor carries no protected payload");
        intact(stack, p);
        var moving = new ItemEntity(h.getLevel(), 0, 0, 0, stack, 1, 2, 3);
        require(moving.getItem().isEmpty(), "Explicit-velocity constructor is protected");
        var bare = new ItemEntity(EntityType.ITEM, h.getLevel());
        bare.setItem(stack);
        require(bare.getItem().isEmpty(), "Later setItem is protected");
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.put("Item", stack.save(h.getLevel().registryAccess()));
        bare.load(tag);
        require(bare.getItem().isEmpty(), "NBT loading cannot reintroduce the payload");
        var projection = new ItemStack(ModItems.PHASE_LOCK_PROJECTION.get());
        projection.set(DataComponents.CONTAINER, stack.get(DataComponents.CONTAINER));
        require(new ItemEntity(h.getLevel(), 0, 0, 0, projection).getItem().isEmpty(), "Armor projections share the fallback");
        var ordinary = new ItemStack(Items.DIAMOND, 17);
        bare.setItem(ordinary);
        require(bare.getItem() == ordinary && ordinary.getCount() == 17, "Ordinary drops preserve identity, components and count");
        intact(stack, p);
        h.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void realPneumaticHopperCannotMaterializeSimulatedPayload(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("pneumaticcraft")) { h.succeed(); return; }
        var stack = locked();
        var p = player(h, stack);
        require(StaffPhaseService.lock(p, stack), "Real-mod test uses private ownership and a projection");
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("pneumaticcraft:omnidirectional_hopper"));
        h.setBlock(new BlockPos(2, 1, 2), block);
        var hopper = h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2, 1, 2)));
        var method = hopper.getClass().getDeclaredMethod("importFromInventory", IItemHandler.class, int.class, boolean.class);
        method.setAccessible(true);
        var field = hopper.getClass().getDeclaredField("itemHandler");
        field.setAccessible(true);
        var destination = (IItemHandler) field.get(hopper);
        var source = new PlayerMainInvWrapper(p.getInventory());
        require((int) method.invoke(hopper, source, 64, false) == 0, "Real PNC simulation-first pipeline imports zero locked items");
        for (int i = 0; i < destination.getSlots(); i++) require(destination.getStackInSlot(i).isEmpty(), "Hopper obtained no copy or nested payload");
        intact(stack, p);
        p.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 3));
        require((int) method.invoke(hopper, source, 64, false) == 3, "PNC continues past locked slots and imports ordinary items");
        require(destination.getStackInSlot(0).getCount() == 3 && p.getInventory().getItem(1).isEmpty(), "Ordinary PNC transfer is conserved");
        intact(stack, p);
        require(StaffPhaseService.unlock(p, p.getMainHandItem()), "Unlock private original for control transfer");
        require((int) method.invoke(hopper, source, 1, false) == 1 && p.getMainHandItem().isEmpty(), "Unlocked PNC transfer succeeds once");
        h.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void realChanceBombAndDelayedInventoryCopyExcludeProtectedItems(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("chancecubes")) { h.succeed(); return; }
        var stack = locked();
        var p = player(h, stack);
        require(StaffPhaseService.lock(p, stack), "Real-mod test uses private ownership and a projection");
        var projection = new ItemStack(ModItems.PHASE_LOCK_PROJECTION.get());
        projection.set(DataComponents.CONTAINER, stack.get(DataComponents.CONTAINER));
        p.setItemSlot(EquipmentSlot.CHEST, projection);
        var clazz = Class.forName("chanceCubes.rewards.DefaultRewards$12");
        var ctor = clazz.getDeclaredConstructor(String.class, int.class);
        ctor.setAccessible(true);
        var reward = ctor.newInstance("phase_test", 0);
        var trigger = clazz.getDeclaredMethod("trigger", net.minecraft.server.level.ServerLevel.class, BlockPos.class, Player.class, com.google.gson.JsonObject.class);
        trigger.setAccessible(true);
        trigger.invoke(reward, h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)), p, new com.google.gson.JsonObject());
        intact(stack, p);
        require(p.getItemBySlot(EquipmentSlot.CHEST) == projection, "Real bomb skips protected armor overwrite");
        require(p.getInventory().getItem(1).is(Items.DEAD_BUSH), "Bomb still replaces ordinary slots");
        var copy = Class.forName("chanceCubes.mcwrapper.InventoryWrapper").getMethod("copyInvAToB", Inventory.class, Inventory.class);
        var captured = new Inventory(p);
        copy.invoke(null, p.getInventory(), captured);
        require(captured.getItem(0).isEmpty() && captured.getItem(38).isEmpty(), "Reward snapshot contains no locked staff or projection payload");
        require(captured.getItem(1).is(Items.DEAD_BUSH), "Reward still captures ordinary items");
        var newlyLocked = locked();
        p.getInventory().setItem(1, newlyLocked);
        copy.invoke(null, captured, p.getInventory());
        intact(stack, p);
        require(p.getInventory().getItem(1) == newlyLocked, "Delayed restoration preserves a staff locked after the original capture");
        var clearClass = Class.forName("chanceCubes.rewards.defaultRewards.ClearInventoryReward");
        var clear = clearClass.getConstructor().newInstance();
        clearClass.getMethod("trigger", net.minecraft.server.level.ServerLevel.class, BlockPos.class, Player.class, com.google.gson.JsonObject.class)
                .invoke(clear, h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)), p, new com.google.gson.JsonObject());
        intact(stack, p);
        require(p.getInventory().getItem(1) == newlyLocked && p.getInventory().getItem(2).isEmpty(), "Real clear reward skips protected slots and clears ordinary ones");
        h.succeed();
    }

    @GameTest(template = "staff_empty")
    public static void realLyssaSelectionCannotDamageSpawnOrRemoveLockedStack(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("reliquary")) { h.succeed(); return; }
        var stack = locked();
        var victim = player(h, stack);
        require(StaffPhaseService.lock(victim, stack), "Real-mod test uses private ownership and a projection");
        var thief = player(h, new ItemStack(Items.FISHING_ROD));
        var clazz = Class.forName("reliquary.entity.LyssaHook");
        var hook = (net.minecraft.world.entity.projectile.FishingHook) clazz.getConstructor(Level.class, Player.class, int.class, int.class)
                .newInstance(h.getLevel(), thief, 0, 0);
        var target = net.minecraft.world.entity.projectile.FishingHook.class.getDeclaredField("hookedIn");
        target.setAccessible(true);
        target.set(hook, victim);
        var steal = clazz.getDeclaredMethod("stealFromLivingEntity");
        steal.setAccessible(true);
        long energy = StaffEnergy.stored(stack);
        for (int i = 0; i < 100; i++) steal.invoke(hook);
        intact(stack, victim);
        require(StaffEnergy.stored(stack) == energy, "Rod never charges damage against protected staff");
        require(StaffPhaseService.unlock(victim, victim.getMainHandItem()), "Unlock private original for control theft");
        for (int i = 0; i < 100 && !victim.getMainHandItem().isEmpty(); i++) steal.invoke(hook);
        require(victim.getMainHandItem().isEmpty(), "Unprotected control proves real rod theft path executed");
        h.succeed();
    }
}
