package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.block.TianshuSupercomputerControllerBlock;
import com.moakiee.ae2lt.blockentity.TianshuSeedStorageBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanIssue;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanner;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockTemplate;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockRole;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopMemberPattern;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceRule;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Real-world JDB checks. Invoke {@link #runAll()} from a suspended server tick thread. */
public final class TianshuJdbHarness {
    private static final BlockPos BASE_CONTROLLER = new BlockPos(8, 200, 8);
    private static volatile String lastReport = "not run";

    public static String runAll() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return lastReport = "FAIL: no running Minecraft server";
        if (!server.isSameThread()) return lastReport = "FAIL: invoke runAll() from the suspended server tick thread";
        var checks = new ArrayList<String>();
        try {
            ServerLevel level = server.overworld();
            runPatternAlgorithmChecks(level, checks);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                runDirection(level, BASE_CONTROLLER.offset(0, 0, direction.get2DDataValue() * 12), direction, checks);
            }
            return lastReport = "PASS: " + checks.size() + " scenarios; " + String.join("; ", checks);
        } catch (Throwable failure) {
            failure.printStackTrace();
            return lastReport = "FAIL: " + failure.getClass().getSimpleName() + ": " + failure.getMessage()
                    + "; completed=" + String.join("; ", checks);
        }
    }

    public static String lastReport() { return lastReport; }

    /** Probes MEGA's real runtime jar through AE2's standard CPU persistence decoder path. */
    public static String runMegaPatternDecodeProbe() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return "FAIL: no running Minecraft server";
        if (!server.isSameThread()) return "FAIL: invoke from the suspended server tick thread";
        try {
            var patternClass = Class.forName("gripe._90.megacells.misc.DecompressionPattern");
            var constructor = patternClass.getConstructor(ItemStack.class, ItemStack.class);
            var original = (appeng.api.crafting.IPatternDetails) constructor.newInstance(
                    new ItemStack(Items.IRON_INGOT, 9), new ItemStack(Items.IRON_BLOCK));
            var definition = original.getDefinition();
            require(definition != null, "MEGA definition is null");
            var level = server.overworld();
            var decodedDirect = appeng.api.crafting.PatternDetailsHelper.decodePattern(definition, level);
            var snapshot = com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot
                    .fromItemStack(definition.toStack(), level.registryAccess());
            var authoringResult = com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternAuthoringService
                    .createFromDraft(
                            java.util.List.of(new com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopMemberPattern(
                                    snapshot, 1L)),
                            appeng.api.stacks.AEItemKey.of(Items.IRON_BLOCK), 1, level);
            var definitionTag = definition.toTag(level.registryAccess());
            definitionTag.putLong("#craftingProgress", 64L);
            var restoredDefinition = AEItemKey.fromTag(level.registryAccess(), definitionTag);
            var decodedRoundTrip = appeng.api.crafting.PatternDetailsHelper.decodePattern(
                    restoredDefinition, level);
            var controlStack = appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new appeng.api.stacks.GenericStack(AEItemKey.of(Items.IRON_INGOT), 9)),
                    List.of(new appeng.api.stacks.GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1)));
            var controlDefinition = AEItemKey.of(controlStack);
            require(controlDefinition != null, "AE2 control definition is null");
            var controlTag = controlDefinition.toTag(level.registryAccess());
            controlTag.putLong("#craftingProgress", 64L);
            var restoredControlDefinition = AEItemKey.fromTag(level.registryAccess(), controlTag);
            var decodedControl = appeng.api.crafting.PatternDetailsHelper.decodePattern(
                    restoredControlDefinition, level);
            var decodersField = appeng.api.crafting.PatternDetailsHelper.class.getDeclaredField("DECODERS");
            decodersField.setAccessible(true);
            var decoders = ((java.util.List<?>) decodersField.get(null)).stream()
                    .map(decoder -> decoder.getClass().getName())
                    .toList();
            var compressionService = Class.forName("gripe._90.megacells.misc.CompressionService");
            var chainsField = compressionService.getDeclaredField("chains");
            chainsField.setAccessible(true);
            int chainCount = ((java.util.List<?>) chainsField.get(null)).size();
            return "MEGA decode: direct=" + className(decodedDirect)
                    + ", nbtRoundTrip=" + className(decodedRoundTrip)
                    + ", definition=" + definition
                    + ", vanillaCpuTaskRoundTrip={control:1->" + (decodedControl == null ? 0 : 1)
                    + ", mega:1->" + (decodedRoundTrip == null ? 0 : 1)
                    + ", lostMegaProgress=" + (decodedRoundTrip == null
                            ? definitionTag.getLong("#craftingProgress") : 0L) + "}"
                    + ", ae2ltAuthoringGate=" + authoringResult.status()
                    + ", compressionChains=" + chainCount
                    + ", registeredDecoders=" + decoders;
        } catch (Throwable failure) {
            failure.printStackTrace();
            return "FAIL: " + failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
    }

    private static String className(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static void runPatternAlgorithmChecks(ServerLevel level, List<String> checks) {
        var seedStack = new ItemStack(Items.NETHERITE_PICKAXE);
        seedStack.setDamageValue(1);
        var returnedSeedStack = new ItemStack(Items.NETHERITE_PICKAXE);
        returnedSeedStack.setDamageValue(2);
        var outputStack = new ItemStack(Items.EMERALD);
        var seed = AEItemKey.of(seedStack);
        var returnedSeed = AEItemKey.of(returnedSeedStack);
        var output = AEItemKey.of(outputStack);
        require(seed != null && returnedSeed != null && output != null, "real overload test keys unavailable");
        var input = new appeng.api.crafting.IPatternDetails.IInput() {
            @Override public appeng.api.stacks.GenericStack[] getPossibleInputs() {
                return new appeng.api.stacks.GenericStack[] {new appeng.api.stacks.GenericStack(seed, 1)};
            }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(appeng.api.stacks.AEKey key, net.minecraft.world.level.Level ignored) {
                return seed.equals(key);
            }
            @Override public appeng.api.stacks.AEKey getRemainingKey(appeng.api.stacks.AEKey key) {
                return null;
            }
        };
        var source = new appeng.api.crafting.IPatternDetails() {
            @Override public AEItemKey getDefinition() { return seed; }
            @Override public IInput[] getInputs() { return new IInput[] {input}; }
            @Override public List<appeng.api.stacks.GenericStack> getOutputs() {
                return List.of(new appeng.api.stacks.GenericStack(returnedSeed, 1),
                        new appeng.api.stacks.GenericStack(output, 1));
            }
        };
        var snapshot = new SourcePatternSnapshot(
                ResourceLocation.fromNamespaceAndPath("ae2", "encoded_processing_pattern"), null, null);
        var parsed = new com.moakiee.thunderbolt.ae2.overload.pattern.ParsedPatternDefinition(
                snapshot,
                List.of(new com.moakiee.thunderbolt.ae2.overload.pattern.ParsedPatternInput(0, seedStack)),
                List.of(
                        new com.moakiee.thunderbolt.ae2.overload.pattern.ParsedPatternOutput(
                                0, returnedSeedStack, false),
                        new com.moakiee.thunderbolt.ae2.overload.pattern.ParsedPatternOutput(
                                1, outputStack, true)));
        var strictDefinition = new com.moakiee.thunderbolt.ae2.overload.pattern.OverloadPatternDetails(
                parsed, com.moakiee.thunderbolt.ae2.overload.model.EncodedOverloadPattern.builder()
                        .input(0, com.moakiee.thunderbolt.ae2.overload.model.MatchMode.STRICT).build());
        var fuzzyDefinition = new com.moakiee.thunderbolt.ae2.overload.pattern.OverloadPatternDetails(
                parsed, com.moakiee.thunderbolt.ae2.overload.model.EncodedOverloadPattern.builder()
                        .input(0, com.moakiee.thunderbolt.ae2.overload.model.MatchMode.ID_ONLY).build());
        var strict = new com.moakiee.thunderbolt.ae2.overload.pattern.Ae2OverloadPatternDetails(
                seed, strictDefinition, source);
        var fuzzy = new com.moakiee.thunderbolt.ae2.overload.pattern.Ae2OverloadPatternDetails(
                seed, fuzzyDefinition, source);
        require(com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternAnalyzer.analyze(
                        List.of(new com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternAnalyzer.Member(strict, 1)),
                        output) == null,
                "real STRICT overload pattern incorrectly matched different NBT output");
        require(com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternAnalyzer.analyze(
                        List.of(new com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternAnalyzer.Member(fuzzy, 1)),
                        output) == null,
                "durability-fuzzy overload was encoded as a closed-loop seed");

        var namedA = new ItemStack(Items.PAPER);
        namedA.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("A"));
        var namedB = new ItemStack(Items.PAPER);
        namedB.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("B"));
        var strictMode = com.moakiee.thunderbolt.ae2.overload.model.MatchMode.STRICT;
        var idOnlyMode = com.moakiee.thunderbolt.ae2.overload.model.MatchMode.ID_ONLY;
        require(analyzeRealOverloadEdge(namedA, namedA, strictMode, strictMode) != null,
                "real STRICT output did not enter STRICT input");
        require(analyzeRealOverloadEdge(namedA, namedB, idOnlyMode, strictMode) != null,
                "real STRICT output did not enter ID_ONLY input");
        require(analyzeRealOverloadEdge(namedA, namedB, idOnlyMode, idOnlyMode) != null,
                "real ID_ONLY output did not enter ID_ONLY input");
        require(analyzeRealOverloadEdge(namedA, namedA, strictMode, idOnlyMode) == null,
                "real ID_ONLY output incorrectly entered STRICT input");

        var damagedA = new ItemStack(Items.NETHERITE_PICKAXE);
        damagedA.setDamageValue(1);
        var damagedB = new ItemStack(Items.NETHERITE_PICKAXE);
        damagedB.setDamageValue(2);
        var variantA = AEItemKey.of(damagedA);
        var variantB = AEItemKey.of(damagedB);
        var reserves = new com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockRepository(() -> 8);
        reserves.set(variantA,
                com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode.IGNORE_SECONDARY, 1_000);
        var group = java.util.Map.<appeng.api.stacks.AEKey, Long>of(variantA, 600L, variantB, 700L);
        long usable = reserves.usablePreexistingStock(variantA, 600, group)
                + reserves.usablePreexistingStock(variantB, 700, group);
        require(usable == 300L, "real NBT variants aggregate reserve usable=" + usable);
        checks.add("algorithms=real-overload-strict/id-only+aggregate-nbt-reserve");
    }

    private static com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopAnalysis analyzeRealOverloadEdge(
            ItemStack inputStack, ItemStack returnedStack,
            com.moakiee.thunderbolt.ae2.overload.model.MatchMode inputMode,
            com.moakiee.thunderbolt.ae2.overload.model.MatchMode outputMode) {
        var inputKey = AEItemKey.of(inputStack);
        var returnedKey = AEItemKey.of(returnedStack);
        var productStack = new ItemStack(Items.EMERALD);
        var productKey = AEItemKey.of(productStack);
        var input = new appeng.api.crafting.IPatternDetails.IInput() {
            @Override public appeng.api.stacks.GenericStack[] getPossibleInputs() {
                return new appeng.api.stacks.GenericStack[] {new appeng.api.stacks.GenericStack(inputKey, 1)};
            }
            @Override public long getMultiplier() { return 1; }
            @Override public boolean isValid(appeng.api.stacks.AEKey key, net.minecraft.world.level.Level ignored) {
                return inputMode == com.moakiee.thunderbolt.ae2.overload.model.MatchMode.ID_ONLY
                        ? inputKey.dropSecondary().equals(key.dropSecondary()) : inputKey.equals(key);
            }
            @Override public appeng.api.stacks.AEKey getRemainingKey(appeng.api.stacks.AEKey key) { return null; }
        };
        var source = new appeng.api.crafting.IPatternDetails() {
            @Override public AEItemKey getDefinition() { return inputKey; }
            @Override public IInput[] getInputs() { return new IInput[] {input}; }
            @Override public List<appeng.api.stacks.GenericStack> getOutputs() {
                return List.of(new appeng.api.stacks.GenericStack(returnedKey, 1),
                        new appeng.api.stacks.GenericStack(productKey, 1));
            }
        };
        var parsed = new com.moakiee.thunderbolt.ae2.overload.pattern.ParsedPatternDefinition(
                new SourcePatternSnapshot(
                        ResourceLocation.fromNamespaceAndPath("ae2", "encoded_processing_pattern"), null, null),
                List.of(new com.moakiee.thunderbolt.ae2.overload.pattern.ParsedPatternInput(0, inputStack)),
                List.of(
                        new com.moakiee.thunderbolt.ae2.overload.pattern.ParsedPatternOutput(0, returnedStack, false),
                        new com.moakiee.thunderbolt.ae2.overload.pattern.ParsedPatternOutput(1, productStack, true)));
        var definition = new com.moakiee.thunderbolt.ae2.overload.pattern.OverloadPatternDetails(
                parsed, com.moakiee.thunderbolt.ae2.overload.model.EncodedOverloadPattern.builder()
                        .input(0, inputMode).output(0, outputMode).build());
        var details = new com.moakiee.thunderbolt.ae2.overload.pattern.Ae2OverloadPatternDetails(
                inputKey, definition, source);
        return com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternAnalyzer.analyze(
                List.of(new com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternAnalyzer.Member(details, 1)),
                productKey);
    }

    private static void runDirection(ServerLevel level, BlockPos controllerPos, Direction direction,
                                     List<String> checks) {
        clearTemplateVolume(level, controllerPos, direction);
        try {
            buildComplete(level, controllerPos, direction);
            var controller = requireController(level, controllerPos);
            controller.scanNow();
            require(controller.isFormed(), direction + " did not form: " + controller.issueText());
            require(controller.memberCount() == 243, direction + " member count=" + controller.memberCount());
            var profile = controller.getCoreProfile();
            require(profile.capacityCoreCount() == 11, direction + " storage cores=" + profile.capacityCoreCount());
            require(profile.parallelCoreCount() == 11, direction + " parallel cores=" + profile.parallelCoreCount());
            require(profile.storageBytes() == 11L * 64L * 1024L * 1024L,
                    direction + " storage bytes=" + profile.storageBytes());
            require(profile.parallelism() == 11 * 128, direction + " parallelism=" + profile.parallelism());
            var functions = controller.getFunctionProfile();
            require(functions.inventoryMaintenanceCoreCount() == 1, direction + " maintenance count mismatch");
            require(functions.closedLoopPatternCoreCount() == 1, direction + " loop core count mismatch");
            require(functions.closedLoopPatternCapacity() == 64, direction + " pattern capacity mismatch");
            require(functions.closedLoopSeedStorageCount() == 1,
                    direction + " seed drive count mismatch");

            BlockPos portPos = TianshuMultiblockScanner.worldPos(
                    controllerPos, TianshuMultiblockTemplate.LOWER_PORT, direction);
            var port = requirePort(level, portPos);
            require(port.isFormed(), direction + " port did not form");
            require(controllerPos.equals(port.getControllerPos()), direction + " port binding mismatch");

            BlockPos seedStoragePos = peripheral(controllerPos, direction, 3);
            var seedDrive = requireSeedDrive(level, seedStoragePos);
            seedDrive.getCellInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
            var seed = AEItemKey.of(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            require(seed != null && port.insertReusableSeed(seed, 3, Actionable.MODULATE) == 3,
                    direction + " seed insert failed");
            BlockPos functionCore = peripheral(controllerPos, direction, 0);
            level.setBlock(functionCore, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            controller.scanNow();
            require(!controller.isFormed(), direction + " stayed formed after function-core removal");
            require(!port.isFormed(), direction + " port stayed formed after breakup");
            require(port.getCableConnectionType(Direction.UP) == appeng.api.util.AECableType.NONE,
                    direction + " unformed port exposed AE cable");
            require(seedDrive.amount(seed, port.getActionSource()) == 3,
                    direction + " deform erased disk seed data");
            require(port.extractReusableSeed(seed, 1, Actionable.MODULATE) == 0,
                    direction + " unformed port allowed seed extraction");
            level.setBlock(functionCore, ModBlocks.INVENTORY_MAINTENANCE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
            controller.scanNow();
            require(controller.isFormed(), direction + " did not reform after function-core repair");
            require(port.extractReusableSeed(seed, 1, Actionable.MODULATE) == 1,
                    direction + " reformed port lost seed access");

            // Repeatedly replace functional units with ordinary valid cores. The structure stays
            // formed, the corresponding capability disappears, and port-owned data must survive.
            var ruleId = java.util.UUID.randomUUID();
            var rule = new InventoryMaintenanceRule(ruleId, seed, 2, 4, 1, true, false, null);
            require(port.getInventoryMaintenance().putRule(rule)
                            == com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceRepository.PutResult.ADDED,
                    direction + " maintenance rule insert failed");
            require(port.getInventoryMaintenance().setReservedStock(ruleId, seed,
                                    com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode.IGNORE_SECONDARY, 2)
                            == com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockRepository.PutResult.ADDED,
                    direction + " reserve insert failed");
            var patternId = java.util.UUID.randomUUID();
            var storedPattern = new ClosedLoopPatternPayload(patternId, 1L,
                    List.of(new ClosedLoopMemberPattern(new SourcePatternSnapshot(
                            ResourceLocation.fromNamespaceAndPath("ae2", "encoded_processing_pattern"), null, null), 1)),
                    List.of(new appeng.api.stacks.GenericStack(seed, 1)), List.of(),
                    List.of(new appeng.api.stacks.GenericStack(seed, 1)), 1, true);
            require(port.getClosedLoopPatternRepository().put(storedPattern)
                            == com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternRepository.PutResult.ADDED,
                    direction + " loop pattern insert failed");

            var savedPort = new net.minecraft.nbt.CompoundTag();
            port.saveAdditional(savedPort, level.registryAccess());
            var loadedCopy = new TianshuSupercomputerPortBlockEntity(portPos, port.getBlockState());
            loadedCopy.loadTag(savedPort, level.registryAccess());
            require(loadedCopy.getInventoryMaintenance().repository().getById(ruleId) != null,
                    direction + " NBT reload lost maintenance rule");
            require(loadedCopy.getInventoryMaintenance().reservedStock(ruleId).matchMode(seed)
                            == com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode.IGNORE_SECONDARY,
                    direction + " NBT reload lost ignore-NBT reserve mode");
            require(loadedCopy.getClosedLoopPatternRepository().get(patternId) != null,
                    direction + " NBT reload lost closed-loop pattern");
            var savedDrive = new net.minecraft.nbt.CompoundTag();
            seedDrive.saveAdditional(savedDrive, level.registryAccess());
            var loadedDrive = new TianshuSeedStorageBlockEntity(seedStoragePos, seedDrive.getBlockState());
            loadedDrive.loadTag(savedDrive, level.registryAccess());
            require(loadedDrive.amount(seed, port.getActionSource()) == 2,
                    direction + " disk NBT reload lost seed contents");

            BlockPos maintenanceCore = peripheral(controllerPos, direction, 0);
            BlockPos loopStorage = peripheral(controllerPos, direction, 2);
            for (int cycle = 0; cycle < 3; cycle++) {
                level.setBlock(seedStoragePos, ModBlocks.STORAGE_SUPERCOMPUTING_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);
                controller.scanNow();
                require(controller.isFormed(), direction + " seed-storage removal deformed cycle " + cycle);
                require(!port.getFunctionProfile().supportsClosedLoopSeeds(), direction + " seed ability remained cycle " + cycle);
                require(port.extractReusableSeed(seed, 1, Actionable.MODULATE) == 0,
                        direction + " disabled seed storage extracted cycle " + cycle);
                level.setBlock(seedStoragePos, ModBlocks.CLOSED_LOOP_SEED_STORAGE.get().defaultBlockState(), Block.UPDATE_ALL);

                level.setBlock(maintenanceCore, ModBlocks.PARALLEL_SUPERCOMPUTING_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);
                controller.scanNow();
                require(controller.isFormed(), direction + " maintenance removal deformed cycle " + cycle);
                require(!port.getFunctionProfile().supportsInventoryMaintenance(), direction + " maintenance remained cycle " + cycle);
                require(port.getInventoryMaintenance().repository().getById(ruleId) != null,
                        direction + " maintenance rule lost cycle " + cycle);
                require(port.getInventoryMaintenance().reservedStock(ruleId).reserve(seed) == 2,
                        direction + " reserve lost cycle " + cycle);
                require(port.getInventoryMaintenance().reservedStock(ruleId).matchMode(seed)
                                == com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode.IGNORE_SECONDARY,
                        direction + " reserve mode lost cycle " + cycle);
                level.setBlock(maintenanceCore, ModBlocks.INVENTORY_MAINTENANCE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);

                level.setBlock(loopStorage, ModBlocks.STORAGE_SUPERCOMPUTING_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);
                controller.scanNow();
                require(controller.isFormed(), direction + " loop-storage removal deformed cycle " + cycle);
                require(port.getClosedLoopPatternRepository().capacity() == 0,
                        direction + " loop capacity remained cycle " + cycle);
                require(port.getClosedLoopPatternRepository().get(patternId) != null,
                        direction + " stored loop lost cycle " + cycle);
                level.setBlock(loopStorage, ModBlocks.CLOSED_LOOP_PATTERN_STORAGE.get().defaultBlockState(), Block.UPDATE_ALL);
                controller.scanNow();
                require(controller.isFormed(), direction + " functional restore failed cycle " + cycle);
                require(port.getFunctionProfile().supportsClosedLoopSeeds()
                                && port.getFunctionProfile().supportsInventoryMaintenance()
                                && port.getClosedLoopPatternRepository().capacity() == 64,
                        direction + " functional restore incomplete cycle " + cycle);
            }

            BlockPos requiredAir = firstIgnored(controllerPos, direction);
            level.setBlock(requiredAir, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            assertIssue(controller, TianshuMultiblockScanIssue.UNEXPECTED_BLOCK, direction + " required-air");
            level.setBlock(requiredAir, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

            BlockPos upperPort = TianshuMultiblockScanner.worldPos(
                    controllerPos, TianshuMultiblockTemplate.UPPER_PORT, direction);
            level.setBlock(upperPort, ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get().defaultBlockState(), Block.UPDATE_ALL);
            assertIssue(controller, TianshuMultiblockScanIssue.MULTIPLE_PORTS, direction + " duplicate-port");
            level.setBlock(upperPort, ModBlocks.TIANSHU_SUPERCOMPUTER_CASING.get().defaultBlockState(), Block.UPDATE_ALL);

            BlockPos center = TianshuMultiblockScanner.worldPos(controllerPos, new BlockPos(3, 3, 3), direction);
            level.setBlock(center, ModBlocks.STORAGE_SUPERCOMPUTING_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);
            assertIssue(controller, TianshuMultiblockScanIssue.MISSING_MAIN_CORE, direction + " missing-main");
            level.setBlock(center, ModBlocks.BASELINE_SUPERCOMPUTING_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);
            BlockPos invalidPeripheral = peripheral(controllerPos, direction, 4);
            level.setBlock(invalidPeripheral, ModBlocks.QUANTUM_SUPERCOMPUTING_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);
            assertIssue(controller, TianshuMultiblockScanIssue.MAIN_CORE_OUTSIDE_CENTER, direction + " peripheral-main");
            level.setBlock(invalidPeripheral, ModBlocks.STORAGE_SUPERCOMPUTING_UNIT.get().defaultBlockState(), Block.UPDATE_ALL);
            controller.scanNow();
            require(controller.isFormed(), direction + " did not recover after invalid-core checks");
            checks.add(direction + "=lifecycle/functions/data/ports/air/cores");
        } finally {
            clearTemplateVolume(level, controllerPos, direction);
        }
    }

    private static void buildComplete(ServerLevel level, BlockPos controllerPos, Direction direction) {
        int peripheralIndex = 0;
        for (int x = 0; x < TianshuMultiblockTemplate.SIZE; x++) for (int y = 0; y < TianshuMultiblockTemplate.SIZE; y++)
            for (int z = 0; z < TianshuMultiblockTemplate.SIZE; z++) {
                var local = new BlockPos(x, y, z);
                var world = TianshuMultiblockScanner.worldPos(controllerPos, local, direction);
                BlockState state = switch (TianshuMultiblockTemplate.roleAt(local)) {
                    case CASING -> ModBlocks.TIANSHU_SUPERCOMPUTER_CASING.get().defaultBlockState();
                    case GLASS -> ModBlocks.TIANSHU_SUPERCOMPUTER_GLASS.get().defaultBlockState();
                    case CONTROLLER -> ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get().defaultBlockState()
                            .setValue(TianshuSupercomputerControllerBlock.FACING, direction);
                    case PORT_CANDIDATE -> local.equals(TianshuMultiblockTemplate.LOWER_PORT)
                            ? ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get().defaultBlockState()
                            : ModBlocks.TIANSHU_SUPERCOMPUTER_CASING.get().defaultBlockState();
                    case CORE_RESERVED -> {
                        if (local.equals(new BlockPos(3, 3, 3))) {
                            yield ModBlocks.BASELINE_SUPERCOMPUTING_UNIT.get().defaultBlockState();
                        }
                        int index = peripheralIndex++;
                        yield switch (index) {
                            case 0 -> ModBlocks.INVENTORY_MAINTENANCE_CORE.get().defaultBlockState();
                            case 1 -> ModBlocks.CLOSED_LOOP_PATTERN_CORE.get().defaultBlockState();
                            case 2 -> ModBlocks.CLOSED_LOOP_PATTERN_STORAGE.get().defaultBlockState();
                            case 3 -> ModBlocks.CLOSED_LOOP_SEED_STORAGE.get().defaultBlockState();
                            default -> index % 2 == 0
                                    ? ModBlocks.STORAGE_SUPERCOMPUTING_UNIT.get().defaultBlockState()
                                    : ModBlocks.PARALLEL_SUPERCOMPUTING_UNIT.get().defaultBlockState();
                        };
                    }
                    case IGNORED -> Blocks.AIR.defaultBlockState();
                };
                level.setBlock(world, state, Block.UPDATE_ALL);
            }
    }

    private static void clearTemplateVolume(ServerLevel level, BlockPos controllerPos, Direction direction) {
        for (int x = 0; x < TianshuMultiblockTemplate.SIZE; x++) for (int y = 0; y < TianshuMultiblockTemplate.SIZE; y++)
            for (int z = 0; z < TianshuMultiblockTemplate.SIZE; z++)
                level.setBlock(TianshuMultiblockScanner.worldPos(controllerPos, new BlockPos(x, y, z), direction),
                        Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static BlockPos firstIgnored(BlockPos controllerPos, Direction direction) {
        for (int x = 0; x < TianshuMultiblockTemplate.SIZE; x++) for (int y = 0; y < TianshuMultiblockTemplate.SIZE; y++)
            for (int z = 0; z < TianshuMultiblockTemplate.SIZE; z++) {
                var local = new BlockPos(x, y, z);
                if (TianshuMultiblockTemplate.roleAt(local) == TianshuMultiblockRole.IGNORED)
                    return TianshuMultiblockScanner.worldPos(controllerPos, local, direction);
            }
        throw new IllegalStateException("template has no required-air position");
    }

    private static BlockPos peripheral(BlockPos controllerPos, Direction direction, int index) {
        int found = 0;
        for (int x = 0; x < TianshuMultiblockTemplate.SIZE; x++) for (int y = 0; y < TianshuMultiblockTemplate.SIZE; y++)
            for (int z = 0; z < TianshuMultiblockTemplate.SIZE; z++) {
                var local = new BlockPos(x, y, z);
                if (TianshuMultiblockTemplate.roleAt(local) == TianshuMultiblockRole.CORE_RESERVED
                        && !local.equals(new BlockPos(3, 3, 3)) && found++ == index)
                    return TianshuMultiblockScanner.worldPos(controllerPos, local, direction);
            }
        throw new IllegalArgumentException("no peripheral index " + index);
    }

    private static void assertIssue(TianshuSupercomputerControllerBlockEntity controller,
                                    TianshuMultiblockScanIssue issue, String context) {
        controller.scanNow();
        require(!controller.isFormed(), context + " unexpectedly formed");
        require(controller.issueText().contains(issue.name()),
                context + " did not report " + issue + ": " + controller.issueText());
    }

    private static TianshuSupercomputerControllerBlockEntity requireController(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TianshuSupercomputerControllerBlockEntity value) return value;
        throw new IllegalStateException("missing controller at " + pos);
    }

    private static TianshuSupercomputerPortBlockEntity requirePort(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TianshuSupercomputerPortBlockEntity value) return value;
        throw new IllegalStateException("missing port at " + pos);
    }

    private static TianshuSeedStorageBlockEntity requireSeedDrive(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TianshuSeedStorageBlockEntity value) return value;
        throw new IllegalStateException("missing seed drive at " + pos);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private TianshuJdbHarness() { }
}
