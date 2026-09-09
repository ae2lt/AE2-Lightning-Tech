package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.device.network.RailgunNetworkBinding;
import com.moakiee.ae2lt.logic.railgun.RailgunBinding;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import com.moakiee.ae2lt.item.staff.*;
import com.moakiee.ae2lt.registry.ModDataComponents;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_staff")
@PrefixGameTestTemplate(false)
public final class StaffCombatGameTests {
    private static void require(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
    private static ItemStack staff() {
        var stack = StaffEnergy.charged(new ItemStack(ModItems.MIMICRY_STAFF.get()));
        require(StaffModuleStorage.INSTANCE.installOne(stack, new ItemStack(ModItems.MIMICRY_MODULES.get(StaffModule.DAMAGE).get())), "Install damage module");
        return stack;
    }
    private static void settings(ItemStack stack, int area, int damage, int execution) {
        stack.set(ModDataComponents.MIMICRY_SETTINGS.get(), new StaffSettings(0, 0, 0, 0, 5, false, false, false, area, damage, execution));
    }
    private static ServerPlayer player(GameTestHelper h, ItemStack stack) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "StaffCombatTest"));
        com.moakiee.ae2lt.device.network.RailgunNetworkBinding.INSTANCE.bind(stack,
                net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(1, 1, 0))));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setNoGravity(true);
        // Apothic Attributes gives players random critical hits; disable only that fixture attribute.
        net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(
                net.minecraft.resources.ResourceLocation.parse("apothic_attributes:crit_chance")).ifPresent(holder -> {
                    var attribute = player.getAttribute(holder);
                    if (attribute != null) attribute.setBaseValue(0);
                });
        var p = center(h).add(0, 0, -2);
        player.setPos(p.x, p.y, p.z);
        charge(player);
        return player;
    }
    private static void powered(GameTestHelper h, Runnable test) {
        h.setBlock(new BlockPos(0, 1, 0), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(new BlockPos(1, 1, 0), com.moakiee.ae2lt.registry.ModBlocks.OVERLOAD_DEVICE_WORKBENCH.get());
        h.setBlock(new BlockPos(2, 1, 0), appeng.core.definitions.AEBlocks.DRIVE.block());
        var drive = (appeng.blockentity.storage.DriveBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2, 1, 0)));
        var cell = new ItemStack(ModItems.BULK_LIGHTNING_STORAGE_COMPONENT.get());
        com.moakiee.ae2lt.item.BulkLightningStorageCellItem.writeStoredAmounts(cell, 100_000, 100_000);
        require(drive.getInternalInventory().insertItem(0, cell, false).isEmpty(), "Real drive accepts lightning cell");
        h.runAfterDelay(40, () -> {
            var bench = (com.moakiee.ae2lt.blockentity.OverloadDeviceWorkbenchBlockEntity)
                    h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(1, 1, 0)));
            require(bench.isActive(), "Bound real workbench grid is active");
            test.run();
        });
    }

    private static void charge(ServerPlayer player) {
        for (int i = 0; i < 20; i++) player.doTick();
        player.setOnGround(true);
        player.setSprinting(false);
        require(player.getAttackStrengthScale(.5F) > .9F, "Native player tick fully charges the fixture");
    }
    private static Vec3 center(GameTestHelper h) { return Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 3, 4))); }
    private static Cow cow(GameTestHelper h, Vec3 pos) {
        var cow = new Cow(EntityType.COW, h.getLevel());
        prepare(cow, pos);
        h.getLevel().addFreshEntity(cow);
        return cow;
    }
    private static void prepare(Cow cow, Vec3 pos) {
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        cow.setHealth(1000);
        cow.setPos(pos.x, pos.y, pos.z);
    }

    @GameTest(template = "staff_combat_empty")
    public static void damageTiersUnlockAndDowngrade(GameTestHelper h) {
        powered(h, () -> {
        var stack = staff();
        var player = player(h, stack);
        int[] tiers = {1, 5, 10, 20, 100};
        for (int i = 0; i < tiers.length; i++) {
            settings(stack, 0, i, 0);
            charge(player);
            var cow = cow(h, center(h));
            player.attack(cow);
            require(Math.abs((1000 - cow.getHealth()) - tiers[i]) < .01, "Native base damage tier " + tiers[i] + ": " + cow.getHealth());
            cow.discard();
        }
        settings(stack, 0, 4, 0);
        require(StaffHubSettings.cycle(stack, player, 9) && StaffState.settings(stack).damage() == 0, "Locked tiers are skipped");
        var core = new ItemStack(ModItems.RAILGUN_MODULE_CORE.get());
        require(StaffModuleStorage.INSTANCE.installOne(stack, core), "Existing core installs in staff");
        settings(stack, 0, 5, 0);
        charge(player);
        var cow = cow(h, center(h));
        player.attack(cow);
        require(cow.getHealth() == 500, "Core unlocks native 500 damage");
        cow.discard();
        StaffModuleStorage.INSTANCE.uninstallOne(stack, StaffModuleStorage.typeId(core));
        require(StaffCombat.baseDamage(stack) == 100, "Removing core immediately removes 500 tier");
        var multi = new ItemStack(ModItems.RAILGUN_MODULE_MULTIDIMENSIONAL_EXECUTION.get());
        require(StaffModuleStorage.INSTANCE.installOne(stack, multi), "Existing multi module installs");
        require(!StaffModuleStorage.INSTANCE.installOne(stack, new ItemStack(ModItems.RAILGUN_MODULE_OVERLOAD_EXECUTION.get())), "Execution modules share one slot");
        settings(stack, 0, 6, 0);
        require(StaffCombat.infinite(stack), "Multi unlocks infinity without core");
        var restored = ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow();
        require(StaffCombat.infinite(restored), "New slots and all combat settings survive save/load");
        StaffModuleStorage.INSTANCE.uninstallOne(stack, StaffModuleStorage.typeId(multi));
        require(!StaffCombat.infinite(stack) && StaffCombat.baseDamage(stack) == 100, "Removing multi immediately revokes infinity");
        h.succeed();
            });
    }

    @GameTest(template = "staff_combat_empty")
    public static void allAreaModesUseOneChargeAndNativeEnchantments(GameTestHelper h) {
        powered(h, () -> {
        for (int mode = 0; mode < 5; mode++) {
            var stack = staff();
            settings(stack, mode, 2, 0);
            var entries = StaffState.modules(stack);
            entries.get(StaffModule.DAMAGE.slot()).enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 5);
            StaffState.setModules(stack, entries);
            var player = player(h, stack);
            var targets = new ArrayList<Cow>();
            for (double x : new double[]{0, 1, 2, 3, 4}) targets.add(cow(h, center(h).add(x, 0, 0)));
            player.attack(targets.getFirst());
            require(targets.getFirst().getHealth() == 987, "Primary receives native Sharpness V at full charge");
            if (mode == 1) {
                require(targets.get(1).getHealth() < 1000, "Sweep mode uses native sword sweep");
                require(StaffEnergy.stored(stack) == StaffEnergy.CAPACITY - 1000, "Native sweep charges one swing");
            } else {
                int count = mode < 2 ? 1 : mode;
                for (int i = 1; i < targets.size(); i++) require(targets.get(i).getHealth() == (i < count ? 987 : 1000),
                        "Mode " + mode + " target " + i + " respects area and shared full charge: " + targets.get(i).getHealth());
                require(StaffEnergy.stored(stack) == StaffEnergy.CAPACITY - count * 1000, "Every native area hit pays once");
            }
            require(player.getAttackStrengthScale(.5F) < .2F, "Final cooldown resets once");
            targets.forEach(Cow::discard);
        }
        h.succeed();
            });
    }

    @GameTest(template = "staff_combat_empty")
    public static void areaRespectsCancellationBudgetAndVisibility(GameTestHelper h) {
        powered(h, () -> {
        var stack = staff();
        settings(stack, 4, 2, 0);
        var player = player(h, stack);
        var primary = cow(h, center(h));
        var near = cow(h, center(h).add(1, 0, 0));
        var far = cow(h, center(h).add(3, 0, 0));
        Consumer<AttackEntityEvent> cancel = e -> { if (e.getEntity() == player && e.getTarget() == primary) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, AttackEntityEvent.class, cancel);
        try { player.attack(primary); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        require(primary.getHealth() == 1000 && near.getHealth() == 1000 && StaffEnergy.stored(stack) == StaffEnergy.CAPACITY,
                "Cancelled primary causes no area or FE payment");
        stack.set(ModDataComponents.MIMICRY_ENERGY.get(), 2000);
        charge(player);
        player.attack(primary);
        require(primary.getHealth() == 990 && near.getHealth() == 990 && far.getHealth() == 1000 && StaffEnergy.stored(stack) == 0,
                "Partial FE attacks nearest secondary then stops");
        StaffEnergy.charged(stack);
        primary.invulnerableTime = 0;
        player.resetAttackStrengthTicker();
        player.attack(primary);
        require(near.getHealth() == 990 && far.getHealth() == 1000 && StaffEnergy.stored(stack) == StaffEnergy.CAPACITY - 1000,
                "Uncharged attack never triggers an area");
        var ally = cow(h, center(h).add(-1, 0, 0));
        var board = h.getLevel().getScoreboard();
        var team = board.addPlayerTeam("staff_" + primary.getId());
        board.addPlayerToTeam(player.getScoreboardName(), team);
        board.addPlayerToTeam(ally.getScoreboardName(), team);
        var pet = EntityType.WOLF.create(h.getLevel());
        pet.setOwnerUUID(player.getUUID()); pet.setTame(true, false);
        pet.setPos(center(h).add(-2, 0, 0)); pet.setNoAi(true); pet.setNoGravity(true); h.getLevel().addFreshEntity(pet);
        var candidates = StaffCombat.secondaryTargets(player, primary, primary.position(), 4);
        require(!candidates.contains(ally) && !candidates.contains(pet), "Area excludes allies and own pets");
        board.removePlayerTeam(team); ally.discard(); pet.discard();
        var wall = h.absolutePos(new BlockPos(5, 3, 3));
        for (int y = 0; y < 3; y++) h.getLevel().setBlockAndUpdate(wall.above(y), Blocks.STONE.defaultBlockState());
        far.setPos(center(h).add(2, 0, -1));
        require(!StaffCombat.secondaryTargets(player, primary, primary.position(), 4).contains(far), "Area excludes targets hidden by a wall");
        for (int y = 0; y < 3; y++) h.getLevel().setBlockAndUpdate(wall.above(y), Blocks.AIR.defaultBlockState());
        primary.discard(); near.discard(); far.discard();
        h.succeed();
            });
    }

    @GameTest(template = "staff_combat_empty")
    public static void executionUsesSharedHealthAndDeathEngine(GameTestHelper h) {
        powered(h, () -> {
        var stack = staff();
        require(StaffModuleStorage.INSTANCE.installOne(stack, new ItemStack(ModItems.RAILGUN_MODULE_CORE.get())), "Install core");
        var overload = new ItemStack(ModItems.RAILGUN_MODULE_OVERLOAD_EXECUTION.get());
        require(StaffModuleStorage.INSTANCE.installOne(stack, overload), "Install overload execution");
        settings(stack, 0, 5, 1);
        var player = player(h, stack);
        // A real entity callback rejects ordinary hurt, as protected bosses may do.
        var target = new Cow(EntityType.COW, h.getLevel()) {
            @Override public boolean hurt(DamageSource source, float amount) { return false; }
        };
        prepare(target, center(h)); h.getLevel().addFreshEntity(target);
        stack.set(ModDataComponents.MIMICRY_ENERGY.get(), 20_000_000);
        player.attack(target);
        require(target.getHealth() == 1000 && StaffEnergy.stored(stack) == 20_000_000, "Execution must reserve base hit FE before taking surcharge");
        StaffEnergy.charged(stack); charge(player); player.attack(target);
        require(target.getHealth() == 500 && StaffEnergy.stored(stack) == StaffEnergy.CAPACITY - 20_001_000,
                "Shared HP record works after hurt rejection, pays surcharge plus one hit");
        charge(player); player.attack(target);
        require(target.isDeadOrDying() && StaffEnergy.stored(stack) == StaffEnergy.CAPACITY - 40_002_000, "Second 500 settles through normal death");
        target.discard();
        StaffModuleStorage.INSTANCE.uninstallOne(stack, StaffModuleStorage.typeId(overload));
        require(StaffModuleStorage.INSTANCE.installOne(stack, new ItemStack(ModItems.RAILGUN_MODULE_MULTIDIMENSIONAL_EXECUTION.get())), "Replace with multidimensional execution");
        for (int mode = 0; mode < 3; mode++) {
            var immune = new Cow(EntityType.COW, h.getLevel()) {
                @Override public boolean hurt(DamageSource source, float amount) { require(Float.isFinite(amount), "Infinity must not leak NaN or infinity into callbacks"); return false; }
            };
            prepare(immune, center(h)); h.getLevel().addFreshEntity(immune);
            settings(stack, 0, 6, mode); StaffEnergy.charged(stack); charge(player); player.attack(immune);
            require(mode == 0 ? immune.isAlive() : immune.isDeadOrDying() || immune.isRemoved(), "Execution mode " + mode);
            require(mode != 2 || immune.isRemoved(), "Forced mode uses existing removal chain");
            require(StaffEnergy.stored(stack) == StaffEnergy.CAPACITY - (mode == 0 ? 0 : 1000), "Multidimensional has no overload surcharge");
            immune.discard();
        }
        h.succeed();
            });
    }

    @GameTest(template = "staff_combat_empty")
    public static void areaExecutionPaysEachTargetAndStopsAtBudget(GameTestHelper h) {
        powered(h, () -> {
        var stack = staff();
        StaffModuleStorage.INSTANCE.installOne(stack, new ItemStack(ModItems.RAILGUN_MODULE_CORE.get()));
        StaffModuleStorage.INSTANCE.installOne(stack, new ItemStack(ModItems.RAILGUN_MODULE_OVERLOAD_EXECUTION.get()));
        settings(stack, 4, 5, 1);
        var player = player(h, stack);
        var targets = new ArrayList<Cow>();
        for (int x = 0; x < 3; x++) {
            var immune = new Cow(EntityType.COW, h.getLevel()) {
                @Override public boolean hurt(DamageSource source, float amount) { return false; }
            };
            prepare(immune, center(h).add(x, 0, 0)); h.getLevel().addFreshEntity(immune); targets.add(immune);
        }
        stack.set(ModDataComponents.MIMICRY_ENERGY.get(), 40_002_000);
        player.attack(targets.getFirst());
        require(targets.get(0).getHealth() == 500 && targets.get(1).getHealth() == 500 && targets.get(2).getHealth() == 1000,
                "Execution-backed primary hit opens area and each target uses full captured charge");
        require(StaffEnergy.stored(stack) == 0, "Two targets each pay 20,001,000 FE, then area stops");
        targets.forEach(Cow::discard);
        h.succeed();
            });
    }

    @GameTest(template = "staff_combat_empty")
    public static void projectedCombatSharesLightningHealthRecordsAndFe(GameTestHelper h) {
        powered(h, () -> {
            var original = staff();
            StaffModuleStorage.INSTANCE.installOne(original, new ItemStack(ModItems.RAILGUN_MODULE_CORE.get()));
            StaffModuleStorage.INSTANCE.installOne(original, new ItemStack(ModItems.RAILGUN_MODULE_OVERLOAD_EXECUTION.get()));
            settings(original, 0, 5, 1);
            var player = player(h, original);
            require(StaffPhaseService.lock(player, original), "Move combat staff to private ownership");
            var projection = player.getMainHandItem();
            long lightning = available(storage(h), LightningKey.EXTREME_HIGH_VOLTAGE);
            var target = new Cow(EntityType.COW, h.getLevel()) {
                @Override public boolean hurt(DamageSource source, float amount) { return false; }
            };
            prepare(target, center(h)); h.getLevel().addFreshEntity(target);
            charge(player); player.attack(target);
            require(target.getHealth() == 500 && StaffEnergy.stored(original) == StaffEnergy.CAPACITY - 20_001_000,
                    "Projected native attack uses the private damage configuration and pays real FE once");
            require(available(storage(h), LightningKey.EXTREME_HIGH_VOLTAGE) == lightning - 256, "Projected combat uses the original network binding and lightning bill");
            require(original.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                    && !projection.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA), "Execution health records remain solely on the original");
            charge(player); player.attack(target);
            require(target.isDeadOrDying() && StaffEnergy.stored(original) == StaffEnergy.CAPACITY - 40_002_000, "Next hit reuses the same health record");
            require(StaffPhaseService.unlock(player, projection) && player.getMainHandItem() == original, "Combat state survives a single unlock");
            target.discard(); h.succeed();
        });
    }

    private static MEStorage storage(GameTestHelper h) {
        var bench = (com.moakiee.ae2lt.blockentity.OverloadDeviceWorkbenchBlockEntity)
                h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(1, 1, 0)));
        return bench.getGrid().getStorageService().getInventory();
    }
    private static long available(MEStorage storage, LightningKey key) {
        return storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }
    private static void clearLightning(MEStorage storage) {
        storage.extract(LightningKey.HIGH_VOLTAGE, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
        storage.extract(LightningKey.EXTREME_HIGH_VOLTAGE, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
    }

    @GameTest(template = "staff_combat_empty")
    public static void networkLightningGatesDamageAndCompensatesExactly(GameTestHelper h) {
        powered(h, () -> {
            var stack = staff();
            var player = player(h, stack);
            var inventory = storage(h);
            settings(stack, 0, 4, 0);
            RailgunNetworkBinding.INSTANCE.unbind(stack);
            var target = cow(h, center(h));
            player.attack(target);
            require(target.getHealth() == 1000 && StaffEnergy.stored(stack) == StaffEnergy.CAPACITY,
                    "Unbound enhanced attack applies neither damage nor FE cost");
            settings(stack, 0, 3, 0); charge(player); player.attack(target);
            require(target.getHealth() == 980 && StaffEnergy.stored(stack) == StaffEnergy.CAPACITY - 1000,
                    "Ordinary 20 damage remains usable without a network");
            target.discard();
            RailgunNetworkBinding.INSTANCE.bind(stack, net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(1, 1, 0))));
            require(StaffModuleStorage.INSTANCE.installOne(stack, new ItemStack(ModItems.RAILGUN_MODULE_CORE.get())), "Core supports staff compensation");
            settings(stack, 0, 5, 0); StaffEnergy.charged(stack);
            long bill = StaffLightning.cost(stack, true).ehv();
            clearLightning(inventory);
            inventory.insert(LightningKey.HIGH_VOLTAGE, bill * 16 - 1, Actionable.MODULATE, IActionSource.empty());
            target = cow(h, center(h)); charge(player); player.attack(target);
            require(target.getHealth() == 1000 && StaffEnergy.stored(stack) == StaffEnergy.CAPACITY
                    && available(inventory, LightningKey.HIGH_VOLTAGE) == bill * 16 - 1,
                    "A one-HV shortage cannot produce a high-damage hit or a partial debit");
            inventory.insert(LightningKey.HIGH_VOLTAGE, 1, Actionable.MODULATE, IActionSource.empty());
            charge(player); player.attack(target);
            require(target.getHealth() == 500 && available(inventory, LightningKey.HIGH_VOLTAGE) == 0,
                    "Real ME grid pays 500 damage using exact 16:1 core compensation");
            target.discard();
            inventory.insert(LightningKey.EXTREME_HIGH_VOLTAGE, bill, Actionable.MODULATE, IActionSource.empty());
            var immune = new Cow(EntityType.COW, h.getLevel()) {
                @Override public boolean hurt(DamageSource source, float amount) { return false; }
            };
            prepare(immune, center(h)); h.getLevel().addFreshEntity(immune);
            int fe = StaffEnergy.stored(stack); charge(player); player.attack(immune);
            require(available(inventory, LightningKey.EXTREME_HIGH_VOLTAGE) == bill && StaffEnergy.stored(stack) == fe,
                    "Rejected hurt without execution refunds lightning and consumes no FE");
            immune.discard();
            var restored = ItemStack.parse(h.getLevel().registryAccess(), stack.save(h.getLevel().registryAccess())).orElseThrow();
            require(RailgunBinding.resolve(restored, player).success(), "Workbench network binding survives serialization");
            h.succeed();
        });
    }

    @GameTest(template = "staff_combat_empty")
    public static void areaLightningBudgetAndCanceledSecondaryAreIndependent(GameTestHelper h) {
        powered(h, () -> {
            var stack = staff(); settings(stack, 4, 2, 0);
            var player = player(h, stack);
            var inventory = storage(h); clearLightning(inventory);
            for (boolean cancelNear : new boolean[]{false, true}) {
                inventory.insert(LightningKey.HIGH_VOLTAGE, 2, Actionable.MODULATE, IActionSource.empty());
                var targets = new ArrayList<Cow>();
                for (int x = 0; x < 3; x++) targets.add(cow(h, center(h).add(x, 0, 0)));
                Consumer<AttackEntityEvent> cancel = e -> {
                    if (cancelNear && e.getEntity() == player && e.getTarget() == targets.get(1)) e.setCanceled(true);
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, AttackEntityEvent.class, cancel);
                StaffEnergy.charged(stack); charge(player);
                try { player.attack(targets.getFirst()); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
                require(targets.get(0).getHealth() == 990
                        && targets.get(1).getHealth() == (cancelNear ? 1000 : 990)
                        && targets.get(2).getHealth() == (cancelNear ? 990 : 1000),
                        "Each actual area hit requires lightning, canceled secondaries do not take payment: "
                                + targets.stream().map(Cow::getHealth).toList());
                require(available(inventory, LightningKey.HIGH_VOLTAGE) == 0 && StaffEnergy.stored(stack) == StaffEnergy.CAPACITY - 2000,
                        "Two successful hits consume two HV and two base FE payments");
                targets.forEach(Cow::discard);
            }
            h.succeed();
        });
    }
}
