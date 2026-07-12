package com.moakiee.ae2lt.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.moakiee.ae2lt.block.MatrixControllerBlock;
import com.moakiee.ae2lt.block.MatrixFormedBlock;
import com.moakiee.ae2lt.block.MatrixMultiblockComponentBlock;
import com.moakiee.ae2lt.block.MatrixMultiblockDirectionalBlock;
import com.moakiee.ae2lt.block.MatrixPatternStorageBlock;
import com.moakiee.ae2lt.logic.craft.MatrixAutoBuildPlan;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingMath;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingProfile;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingUnit;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockMember;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanAttempt;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanResult;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanner;
import com.moakiee.ae2lt.network.MatrixControllerActionPacket;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MatrixControllerBlockEntity extends BlockEntity {
    private static final long NO_SCHEDULED_SCAN = Long.MIN_VALUE;
    private static final int AUTO_BUILD_INTERVAL_TICKS = 1;
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_ORIENTATION = "Orientation";
    private static final String TAG_PORT_POS = "PortPos";
    private static final String TAG_MIN_POS = "MinPos";
    private static final String TAG_MAX_POS = "MaxPos";
    private static final String TAG_MEMBER_COUNT = "MemberCount";
    private static final String TAG_PATTERN_STORAGE_COUNT = "PatternStorageCount";
    private static final String TAG_CRAFTING_UNIT_COUNT = "CraftingUnitCount";
    private static final String TAG_CLOSED_LOOP_PROCESSOR_COUNT = "ClosedLoopProcessorCount";

    private boolean formed;
    private Direction orientation = Direction.NORTH;
    private BlockPos portPos;
    private BlockPos minPos;
    private BlockPos maxPos;
    private int memberCount;
    private int patternStorageCount;
    private int craftingUnitCount;
    private int closedLoopProcessorCount;
    private List<BlockPos> patternStoragePositions = List.of();
    private List<MatrixPatternStorageBlockEntity> cachedPatternStorages = List.of();
    private List<MatrixCraftingUnit> cachedCraftingUnits = List.of();
    private boolean structureCacheValid;
    private long scheduledScanTick = NO_SCHEDULED_SCAN;
    private List<MatrixAutoBuildPlan.Placement> autoBuildPlacements = List.of();
    private UUID autoBuildPlayerId;
    private Direction autoBuildFacing = Direction.NORTH;
    private int autoBuildPlacementIndex;
    private int autoBuildPlacedBlocks;
    private long nextAutoBuildTick;

    public MatrixControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MATRIX_CONTROLLER.get(), pos, blockState);
        orientation = orientationFromState(blockState);
    }

    public static void serverTick(net.minecraft.world.level.Level level,
                                  BlockPos pos,
                                  BlockState state,
                                  MatrixControllerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        if (be.formed && !be.structureCacheValid) {
            be.scheduleStructureCheck();
        }
        if (be.isAutoBuilding()) {
            be.tickAutoBuild();
        } else if (be.scheduledScanTick != NO_SCHEDULED_SCAN && level.getGameTime() >= be.scheduledScanTick) {
            be.scheduledScanTick = NO_SCHEDULED_SCAN;
            be.refreshStructure();
        }
        be.syncRenderState();
    }

    public boolean isFormed() {
        return formed;
    }

    public Direction getOrientation() {
        return orientationFromState(getBlockState());
    }

    public BlockPos getPortPos() {
        return portPos;
    }

    public boolean hasClosedLoopProcessor() {
        return formed && closedLoopProcessorCount > 0;
    }

    public int getClosedLoopProcessorCount() {
        return closedLoopProcessorCount;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public int getPatternStorageCount() {
        return patternStorageCount;
    }

    public int getCraftingUnitCount() {
        return craftingUnitCount;
    }

    public int getPatternSlotCount() {
        int total = 0;
        for (var storage : findPatternStorages()) {
            total += storage.capacity();
        }
        return total;
    }

    public MatrixCraftingProfile getCraftingProfile() {
        var port = getPort();
        return port != null ? port.getCraftingProfile() : MatrixCraftingProfile.empty();
    }

    public MatrixCraftingMath.Snapshot getLimiterSnapshot() {
        var port = getPort();
        return port != null
                ? port.getLimiterSnapshot()
                : MatrixCraftingMath.idleSnapshot(0.0D, 0.0D);
    }

    public void performAction(MatrixControllerActionPacket.Action action, ServerPlayer player) {
        if (level == null || level.isClientSide) {
            return;
        }
        switch (action) {
            case AUTO_BUILD -> autoBuild(player);
            case UPGRADE_PATTERN_STORAGE -> upgradePatternStorage(player);
        }
    }

    public void scheduleStructureCheck() {
        if (level == null || level.isClientSide) {
            return;
        }
        long targetTick = level.getGameTime() + 1L;
        if (scheduledScanTick == NO_SCHEDULED_SCAN || targetTick < scheduledScanTick) {
            scheduledScanTick = targetTick;
        }
        setChanged();
    }

    public void clearStructureBindings() {
        setBoundsConnectedTextureFormed(false);
        clearBindingsInStoredBounds();
    }

    public void scanAndForm(ServerPlayer player) {
        var attempt = scanCurrent();
        if (!attempt.formed()) {
            deform();
            player.displayClientMessage(Component.translatable(
                    "ae2lt.matrix.scan_failed",
                    describeIssues(attempt)).withStyle(ChatFormatting.RED), true);
            return;
        }

        form(attempt.result());
        player.displayClientMessage(Component.translatable(
                "ae2lt.matrix.formed",
                memberCount,
                patternStorageCount,
                craftingUnitCount).withStyle(ChatFormatting.GREEN), true);
    }

    public void autoBuild(ServerPlayer player) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (isAutoBuilding()) {
            player.displayClientMessage(Component.translatable(
                    "ae2lt.matrix.build_in_progress").withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        int patternStorageBudget = player.getAbilities().instabuild
                ? Integer.MAX_VALUE
                : countPatternStorageItems(player);
        var plan = createAutoBuildPlan(patternStorageBudget);
        if (!plan.blocked().isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "ae2lt.matrix.build_blocked",
                    plan.blocked().size(),
                    describeBlockedPositions(plan.blocked())).withStyle(ChatFormatting.RED), false);
            return;
        }

        var requirements = autoBuildRequirementsForMissingBlocks(plan);
        if (!player.getAbilities().instabuild) {
            var missing = findMissingRequirements(player, requirements);
            if (!missing.isEmpty() || plan.missingPatternStorages() > 0) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.matrix.build_missing",
                        describeMissing(missing, plan.missingPatternStorages())).withStyle(ChatFormatting.RED), false);
                return;
            }
        }

        if (plan.placements().isEmpty()) {
            finishAutoBuild(player, 0);
            return;
        }

        autoBuildPlacements = plan.placements();
        autoBuildPlayerId = player.getUUID();
        autoBuildFacing = getOrientation();
        autoBuildPlacementIndex = 0;
        autoBuildPlacedBlocks = 0;
        nextAutoBuildTick = level.getGameTime() + AUTO_BUILD_INTERVAL_TICKS;
        scheduledScanTick = NO_SCHEDULED_SCAN;
        setChanged();
        player.displayClientMessage(Component.translatable(
                "ae2lt.matrix.build_started",
                autoBuildPlacements.size()).withStyle(ChatFormatting.GREEN), true);
    }

    private boolean isAutoBuilding() {
        return autoBuildPlayerId != null && autoBuildPlacementIndex < autoBuildPlacements.size();
    }

    private void tickAutoBuild() {
        if (level == null || level.getGameTime() < nextAutoBuildTick) {
            return;
        }

        var server = level.getServer();
        var player = server != null ? server.getPlayerList().getPlayer(autoBuildPlayerId) : null;
        if (player == null) {
            clearAutoBuildSession();
            refreshStructure();
            return;
        }

        var placement = autoBuildPlacements.get(autoBuildPlacementIndex);
        var pos = MatrixMultiblockScanner.worldPos(worldPosition, placement.localPos(), autoBuildFacing);
        var current = MatrixMultiblockScanner.componentAt(level, pos);
        if (matchesAutoBuildTarget(current, placement.target())) {
            advanceAutoBuild(player, false);
            return;
        }
        if (current != MatrixMultiblockComponent.AIR) {
            abortAutoBuildPlacement(player, pos);
            return;
        }

        Item consumedItem = null;
        BlockState state;
        if (placement.target() == MatrixAutoBuildPlan.Target.PATTERN_STORAGE
                && !player.getAbilities().instabuild) {
            consumedItem = findPatternStorageItem(player);
            state = stateForPatternStorageItem(consumedItem);
            if (state == null) {
                abortAutoBuildMissingItem(player, Component.translatable("ae2lt.matrix.pattern_storage_any"));
                return;
            }
        } else {
            state = stateForAutoBuild(placement.target());
        }
        if (state == null || state.isAir()) {
            abortAutoBuildPlacement(player, pos);
            return;
        }
        if (!player.getAbilities().instabuild && consumedItem == null) {
            consumedItem = state.getBlock().asItem();
            if (consumedItem == net.minecraft.world.item.Items.AIR || countItem(player, consumedItem) <= 0) {
                abortAutoBuildMissingItem(player, consumedItem.getDescription());
                return;
            }
        }

        boolean placed = pos.equals(worldPosition)
                ? level.setBlock(pos, getBlockState().setValue(
                        MatrixMultiblockDirectionalBlock.FACING, autoBuildFacing), Block.UPDATE_ALL)
                : level.setBlock(pos, state, Block.UPDATE_ALL);
        if (!placed) {
            abortAutoBuildPlacement(player, pos);
            return;
        }
        if (consumedItem != null) {
            consumeItem(player, consumedItem, 1);
        }
        playAutoBuildPlaceSound(player, pos, state);
        advanceAutoBuild(player, true);
    }

    private boolean matchesAutoBuildTarget(MatrixMultiblockComponent component, MatrixAutoBuildPlan.Target target) {
        return switch (target) {
            case CASING -> component == MatrixMultiblockComponent.MATRIX_CASING;
            case CONSTRAINT_FRAME -> component == MatrixMultiblockComponent.MATRIX_CONSTRAINT_FRAME;
            case GLASS -> component == MatrixMultiblockComponent.MATRIX_GLASS;
            case PORT -> component == MatrixMultiblockComponent.MATRIX_PORT;
            case PATTERN_STORAGE -> component.isPatternStorage();
        };
    }

    private void playAutoBuildPlaceSound(ServerPlayer player, BlockPos pos, BlockState state) {
        var soundType = state.getSoundType(level, pos, player);
        float volume = (soundType.getVolume() + 1.0F) / 4.0F;
        float pitch = soundType.getPitch() * (0.82F + level.random.nextFloat() * 0.12F);
        level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, volume, pitch);
    }

    private void advanceAutoBuild(ServerPlayer player, boolean placed) {
        if (placed) {
            autoBuildPlacedBlocks++;
        }
        autoBuildPlacementIndex++;
        if (autoBuildPlacementIndex >= autoBuildPlacements.size()) {
            int placedBlocks = autoBuildPlacedBlocks;
            clearAutoBuildSession();
            finishAutoBuild(player, placedBlocks);
            return;
        }
        nextAutoBuildTick = level.getGameTime() + AUTO_BUILD_INTERVAL_TICKS;
    }

    private void abortAutoBuildPlacement(ServerPlayer player, BlockPos pos) {
        clearAutoBuildSession();
        autoBuildPlacementFailed(player, pos);
    }

    private void abortAutoBuildMissingItem(ServerPlayer player, Component itemName) {
        clearAutoBuildSession();
        refreshStructure();
        player.displayClientMessage(Component.translatable(
                "ae2lt.matrix.build_interrupted_missing",
                itemName).withStyle(ChatFormatting.RED), false);
    }

    private void clearAutoBuildSession() {
        autoBuildPlacements = List.of();
        autoBuildPlayerId = null;
        autoBuildPlacementIndex = 0;
        autoBuildPlacedBlocks = 0;
        nextAutoBuildTick = 0L;
        scheduledScanTick = NO_SCHEDULED_SCAN;
        setChanged();
    }

    private void autoBuildPlacementFailed(ServerPlayer player, BlockPos pos) {
        refreshStructure();
        player.displayClientMessage(Component.translatable(
                "ae2lt.matrix.build_place_failed",
                describePosition(pos)).withStyle(ChatFormatting.RED), false);
    }

    private void finishAutoBuild(ServerPlayer player, int placedBlocks) {
        var attempt = scanCurrent();
        if (attempt.formed()) {
            form(attempt.result());
            Component message = placedBlocks == 0
                    ? Component.translatable("ae2lt.matrix.build_already_complete")
                    : Component.translatable("ae2lt.matrix.build_complete", placedBlocks);
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.GREEN), true);
            return;
        }

        deform();
        Component message = placedBlocks == 0
                ? Component.translatable("ae2lt.matrix.build_nothing_to_place")
                : Component.translatable("ae2lt.matrix.build_placed", placedBlocks);
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.GREEN), true);
    }

    public void upgradePatternStorage(ServerPlayer player) {
        var attempt = scanCurrent();
        if (!attempt.formed()) {
            player.displayClientMessage(Component.translatable(
                    "ae2lt.matrix.scan_failed",
                    describeIssues(attempt)).withStyle(ChatFormatting.RED), true);
            return;
        }

        var t1Storages = new ArrayList<MatrixPatternStorageBlockEntity>();
        for (var member : attempt.result().patternMembers()) {
            if (member.component() != MatrixMultiblockComponent.PATTERN_STORAGE_T1) {
                continue;
            }
            if (level.getBlockEntity(member.worldPos()) instanceof MatrixPatternStorageBlockEntity storage) {
                t1Storages.add(storage);
            }
        }

        if (t1Storages.isEmpty()) {
            player.displayClientMessage(Component.translatable("ae2lt.matrix.upgrade_none")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        int available = countUpgradeItems(player);
        if (available <= 0 && !player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable("ae2lt.matrix.upgrade_missing")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        int toUpgrade = player.getAbilities().instabuild ? t1Storages.size() : Math.min(available, t1Storages.size());
        for (int i = 0; i < toUpgrade; i++) {
            upgradeStorageInPlace(t1Storages.get(i));
        }
        if (!player.getAbilities().instabuild) {
            consumeUpgradeItems(player, toUpgrade);
        }
        scanAndForm(player);
        player.displayClientMessage(Component.translatable(
                "ae2lt.matrix.upgraded",
                toUpgrade,
                t1Storages.size()).withStyle(ChatFormatting.GREEN), true);
    }

    public List<MatrixPatternStorageBlockEntity> findPatternStorages() {
        if (level == null || !formed) {
            return List.of();
        }
        ensureStructureCache();
        return cachedPatternStorages;
    }

    public List<MatrixCraftingUnit> findCraftingUnits() {
        if (level == null || !formed) {
            return List.of();
        }
        ensureStructureCache();
        return cachedCraftingUnits;
    }

    private MatrixMultiblockScanAttempt scanCurrent() {
        return MatrixMultiblockScanner.scan(worldPosition, getOrientation(),
                pos -> MatrixMultiblockScanner.componentAt(level, pos));
    }

    private MatrixPortBlockEntity getPort() {
        if (level == null || !formed || portPos == null || !level.isLoaded(portPos)) {
            return null;
        }
        return level.getBlockEntity(portPos) instanceof MatrixPortBlockEntity port ? port : null;
    }

    private void form(MatrixMultiblockScanResult result) {
        clearBindingsInStoredBounds();
        formed = true;
        orientation = result.orientation();
        portPos = result.portPos();
        minPos = result.minPos();
        maxPos = result.maxPos();
        memberCount = result.members().size();
        patternStorageCount = result.patternMembers().size();
        craftingUnitCount = result.craftingMembers().size();
        closedLoopProcessorCount = result.closedLoopProcessorCount();
        patternStoragePositions = result.patternMembers().stream()
                .map(member -> member.worldPos().immutable())
                .toList();
        cachedPatternStorages = resolvePatternStorages(result);
        cachedCraftingUnits = result.craftingUnits();
        structureCacheValid = true;

        bindMembers(result);
        setMembersFormed(result, true);
        setChangedAndUpdate();
    }

    private void deform() {
        clearBindingsInStoredBounds();
        setBoundsConnectedTextureFormed(false);
        formed = false;
        portPos = null;
        minPos = null;
        maxPos = null;
        memberCount = 0;
        patternStorageCount = 0;
        craftingUnitCount = 0;
        closedLoopProcessorCount = 0;
        patternStoragePositions = List.of();
        cachedPatternStorages = List.of();
        cachedCraftingUnits = List.of();
        structureCacheValid = false;
        setChangedAndUpdate();
    }

    private List<MatrixPatternStorageBlockEntity> resolvePatternStorages(MatrixMultiblockScanResult result) {
        var storages = new ArrayList<MatrixPatternStorageBlockEntity>();
        for (var member : result.patternMembers()) {
            if (level.isLoaded(member.worldPos())
                    && level.getBlockEntity(member.worldPos()) instanceof MatrixPatternStorageBlockEntity storage) {
                storages.add(storage);
            }
        }
        return List.copyOf(storages);
    }

    private void refreshStructure() {
        var attempt = scanCurrent();
        if (attempt.formed()) {
            form(attempt.result());
        } else if (formed) {
            deform();
        }
    }

    private void ensureStructureCache() {
        if (!formed || structureCacheValid || level == null || level.isClientSide) {
            return;
        }
        refreshStructure();
    }

    private void bindMembers(MatrixMultiblockScanResult result) {
        if (level.isLoaded(result.portPos())
                && level.getBlockEntity(result.portPos()) instanceof MatrixPortBlockEntity port) {
            port.bindToController(worldPosition);
        }
        for (MatrixMultiblockMember member : result.patternMembers()) {
            if (level.isLoaded(member.worldPos())
                    && level.getBlockEntity(member.worldPos()) instanceof MatrixPatternStorageBlockEntity storage) {
                storage.setControllerPos(worldPosition);
            }
        }
    }

    private void clearBindingsInStoredBounds() {
        if (level == null || minPos == null || maxPos == null) {
            return;
        }
        for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
            for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
                for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                    var pos = new BlockPos(x, y, z);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    var be = level.getBlockEntity(pos);
                    if (be instanceof MatrixPortBlockEntity port && worldPosition.equals(port.getControllerPos())) {
                        port.bindToController(null);
                    } else if (be instanceof MatrixPatternStorageBlockEntity storage
                            && worldPosition.equals(storage.getControllerPos())) {
                        storage.setControllerPos(null);
                    }
                }
            }
        }
    }

    private void setMembersFormed(MatrixMultiblockScanResult result, boolean formedValue) {
        for (MatrixMultiblockMember member : result.members()) {
            setConnectedTextureFormed(member.worldPos(), formedValue);
        }
    }

    private void setBoundsConnectedTextureFormed(boolean formedValue) {
        if (level == null || minPos == null || maxPos == null) {
            return;
        }
        for (int x = minPos.getX(); x <= maxPos.getX(); x++) {
            for (int y = minPos.getY(); y <= maxPos.getY(); y++) {
                for (int z = minPos.getZ(); z <= maxPos.getZ(); z++) {
                    setConnectedTextureFormed(new BlockPos(x, y, z), formedValue);
                }
            }
        }
    }

    // Toggle FORMED on connected-texture matrix blocks. Client-only update.
    private void setConnectedTextureFormed(BlockPos pos, boolean formedValue) {
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof MatrixMultiblockComponentBlock componentBlock
                && componentBlock.matrixComponent(state) != MatrixMultiblockComponent.MATRIX_CONTROLLER
                && state.hasProperty(MatrixFormedBlock.FORMED)
                && state.getValue(MatrixFormedBlock.FORMED) != formedValue) {
            level.setBlock(pos, state.setValue(MatrixFormedBlock.FORMED, formedValue), Block.UPDATE_CLIENTS);
        }
    }

    private void syncRenderState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(MatrixControllerBlock.FORMED) || !state.hasProperty(MatrixControllerBlock.WORKING)) {
            return;
        }
        boolean working = isWorking();
        if (state.getValue(MatrixControllerBlock.FORMED) != formed
                || state.getValue(MatrixControllerBlock.WORKING) != working) {
            level.setBlock(worldPosition, state
                    .setValue(MatrixControllerBlock.FORMED, formed)
                    .setValue(MatrixControllerBlock.WORKING, working), Block.UPDATE_CLIENTS);
        }
    }

    private boolean isWorking() {
        var port = getPort();
        return formed && port != null && port.isWorking();
    }

    private MatrixAutoBuildPlan createAutoBuildPlan(int patternStorageBudget) {
        Direction facing = getOrientation();
        return MatrixAutoBuildPlan.create(local -> MatrixMultiblockScanner.componentAt(
                level,
                MatrixMultiblockScanner.worldPos(worldPosition, local, facing)), patternStorageBudget);
    }

    private java.util.Map<Item, Integer> autoBuildRequirementsForMissingBlocks(MatrixAutoBuildPlan plan) {
        var result = new java.util.LinkedHashMap<Item, Integer>();
        for (var placement : plan.placements()) {
            if (placement.target() == MatrixAutoBuildPlan.Target.PATTERN_STORAGE) {
                continue;
            }
            var state = stateForAutoBuild(placement.target());
            if (state == null || state.isAir()) {
                continue;
            }
            Item item = state.getBlock().asItem();
            if (item != net.minecraft.world.item.Items.AIR) {
                result.merge(item, 1, Integer::sum);
            }
        }
        return result;
    }

    private BlockState stateForAutoBuild(MatrixAutoBuildPlan.Target target) {
        return switch (target) {
            case CASING -> ModBlocks.MATTER_WARPING_MATRIX_CASING.get().defaultBlockState();
            case CONSTRAINT_FRAME -> ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME.get().defaultBlockState();
            case GLASS -> ModBlocks.MATTER_WARPING_MATRIX_GLASS.get().defaultBlockState();
            case PORT -> ModBlocks.MATTER_WARPING_MATRIX_PORT.get().defaultBlockState();
            case PATTERN_STORAGE -> ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T1.get().defaultBlockState();
        };
    }

    private java.util.Map<Item, Integer> findMissingRequirements(Player player, java.util.Map<Item, Integer> requirements) {
        var missing = new java.util.LinkedHashMap<Item, Integer>();
        for (var entry : requirements.entrySet()) {
            int available = countItem(player, entry.getKey());
            if (available < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - available);
            }
        }
        return missing;
    }

    private int countItem(Player player, Item item) {
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private int countPatternStorageItems(Player player) {
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isPatternStorageItem(stack.getItem())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private Item findPatternStorageItem(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && isPatternStorageItem(stack.getItem())) {
                return stack.getItem();
            }
        }
        return null;
    }

    private BlockState stateForPatternStorageItem(Item item) {
        if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof MatrixPatternStorageBlock) {
            return blockItem.getBlock().defaultBlockState();
        }
        return null;
    }

    private boolean isPatternStorageItem(Item item) {
        return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof MatrixPatternStorageBlock;
    }

    private void consumeItem(Player player, Item item, int amount) {
        int remaining = amount;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            var stack = inventory.getItem(i);
            if (!stack.is(item)) {
                continue;
            }
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            if (stack.isEmpty()) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
            remaining -= consumed;
        }
    }

    private Component describeMissing(java.util.Map<Item, Integer> missing, int missingPatternStorages) {
        MutableComponent result = Component.empty();
        int totalEntries = missing.size() + (missingPatternStorages > 0 ? 1 : 0);
        int visibleEntries = 0;
        if (missingPatternStorages > 0) {
            appendMissingEntry(result, Component.translatable("ae2lt.matrix.pattern_storage_any"),
                    missingPatternStorages, visibleEntries++);
        }
        for (var entry : missing.entrySet()) {
            appendMissingEntry(result, entry.getKey().getDescription(), entry.getValue(), visibleEntries++);
            if (visibleEntries >= 4 && totalEntries > visibleEntries) {
                result.append(", ...");
                break;
            }
        }
        return result;
    }

    private void appendMissingEntry(MutableComponent result, Component name, int count, int index) {
        if (index > 0) {
            result.append(", ");
        }
        result.append(name).append(" x").append(Integer.toString(count));
    }

    private void upgradeStorageInPlace(MatrixPatternStorageBlockEntity oldStorage) {
        var pos = oldStorage.getBlockPos();
        var contents = oldStorage.copyContents();
        level.setBlock(pos, ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T2.get().defaultBlockState(),
                Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof MatrixPatternStorageBlockEntity newStorage) {
            newStorage.loadContents(contents);
            newStorage.setControllerPos(worldPosition);
        }
    }

    private int countUpgradeItems(Player player) {
        Item upgrade = ModItems.MATTER_WARPING_MATRIX_PATTERN_STORAGE_UPGRADE.get();
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.is(upgrade)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void consumeUpgradeItems(Player player, int amount) {
        Item upgrade = ModItems.MATTER_WARPING_MATRIX_PATTERN_STORAGE_UPGRADE.get();
        int remaining = amount;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            var stack = inventory.getItem(i);
            if (!stack.is(upgrade)) {
                continue;
            }
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            if (stack.isEmpty()) {
                inventory.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
            }
            remaining -= consumed;
        }
    }

    private Component describeBlockedPositions(List<BlockPos> localPositions) {
        MutableComponent result = Component.empty();
        Direction facing = getOrientation();
        int visible = Math.min(localPositions.size(), 4);
        for (int i = 0; i < visible; i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(describePosition(MatrixMultiblockScanner.worldPos(
                    worldPosition, localPositions.get(i), facing)));
        }
        if (localPositions.size() > visible) {
            result.append(", ...");
        }
        return result;
    }

    private Component describePosition(BlockPos pos) {
        return Component.literal("[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]");
    }

    private String describeIssues(MatrixMultiblockScanAttempt attempt) {
        if (attempt.issues().isEmpty()) {
            return "unknown";
        }
        return attempt.issues().stream().map(Enum::name).reduce((a, b) -> a + ", " + b).orElse("unknown");
    }

    private Direction orientationFromState(BlockState state) {
        if (state.hasProperty(MatrixMultiblockDirectionalBlock.FACING)) {
            Direction facing = state.getValue(MatrixMultiblockDirectionalBlock.FACING);
            if (facing.getAxis() != Direction.Axis.Y) {
                return facing;
            }
        }
        return Direction.NORTH;
    }

    private void setChangedAndUpdate() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(TAG_FORMED, formed);
        tag.putInt(TAG_ORIENTATION, orientation.get3DDataValue());
        if (portPos != null) {
            tag.putLong(TAG_PORT_POS, portPos.asLong());
        }
        if (minPos != null) {
            tag.putLong(TAG_MIN_POS, minPos.asLong());
        }
        if (maxPos != null) {
            tag.putLong(TAG_MAX_POS, maxPos.asLong());
        }
        tag.putInt(TAG_MEMBER_COUNT, memberCount);
        tag.putInt(TAG_PATTERN_STORAGE_COUNT, patternStorageCount);
        tag.putInt(TAG_CRAFTING_UNIT_COUNT, craftingUnitCount);
        tag.putInt(TAG_CLOSED_LOOP_PROCESSOR_COUNT, closedLoopProcessorCount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean(TAG_FORMED);
        structureCacheValid = false;
        patternStoragePositions = List.of();
        cachedPatternStorages = List.of();
        cachedCraftingUnits = List.of();
        orientation = Direction.from3DDataValue(tag.getInt(TAG_ORIENTATION));
        if (orientation.getAxis() == Direction.Axis.Y) {
            orientation = Direction.NORTH;
        }
        portPos = tag.contains(TAG_PORT_POS, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_PORT_POS)) : null;
        minPos = tag.contains(TAG_MIN_POS, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_MIN_POS)) : null;
        maxPos = tag.contains(TAG_MAX_POS, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_MAX_POS)) : null;
        memberCount = tag.getInt(TAG_MEMBER_COUNT);
        patternStorageCount = tag.getInt(TAG_PATTERN_STORAGE_COUNT);
        craftingUnitCount = tag.getInt(TAG_CRAFTING_UNIT_COUNT);
        closedLoopProcessorCount = Math.max(0, tag.getInt(TAG_CLOSED_LOOP_PROCESSOR_COUNT));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        scheduleStructureCheck();
    }
}
