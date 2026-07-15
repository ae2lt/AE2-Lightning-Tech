package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.block.TianshuSupercomputerControllerBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputerPortBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputerStructureBlock;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanAttempt;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockComponent;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanResult;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanner;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanIssue;
import com.moakiee.ae2lt.logic.tianshu.TianshuAutoBuildPlan;
import com.moakiee.ae2lt.logic.tianshu.CpuInternalCoreProfile;
import com.moakiee.ae2lt.logic.tianshu.CpuMainCoreTier;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TianshuSupercomputerControllerBlockEntity extends BlockEntity {
    private static final long NO_SCAN = Long.MIN_VALUE;
    private static final int AUTO_BUILD_INTERVAL_TICKS = 1;
    private static final int CHUNK_RECHECK_INTERVAL_TICKS = 20;
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_PORT_POS = "PortPos";
    private static final String TAG_MIN_POS = "MinPos";
    private static final String TAG_MAX_POS = "MaxPos";
    private static final String TAG_MEMBER_COUNT = "MemberCount";
    private static final String TAG_MAIN_CORE = "MainCore";
    private static final String TAG_CAPACITY_CORES = "CapacityCores";
    private static final String TAG_PARALLEL_CORES = "ParallelCores";
    private static final String TAG_FAST_PLANNING = "FastPlanning";
    private boolean formed;
    private BlockPos portPos;
    private BlockPos minPos;
    private BlockPos maxPos;
    private int memberCount;
    private CpuInternalCoreProfile coreProfile = CpuInternalCoreProfile.empty();
    private boolean structureAvailable;
    private boolean waitingForChunks;
    private long scheduledScanTick = NO_SCAN;
    private long nextChunkCheckTick;
    private List<TianshuMultiblockScanIssue> lastIssues = List.of();
    private boolean fastPlanningEnabled = true;
    private List<TianshuAutoBuildPlan.Placement> autoBuildPlacements = List.of();
    private UUID autoBuildPlayerId;
    private Direction autoBuildFacing = Direction.NORTH;
    private int autoBuildPlacementIndex;
    private int autoBuildPlacedBlocks;
    private long nextAutoBuildTick;

    public TianshuSupercomputerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIANSHU_SUPERCOMPUTER_CONTROLLER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  TianshuSupercomputerControllerBlockEntity controller) {
        if ((controller.formed || controller.waitingForChunks)
                && level.getGameTime() >= controller.nextChunkCheckTick) {
            controller.nextChunkCheckTick = level.getGameTime() + CHUNK_RECHECK_INTERVAL_TICKS;
            controller.checkChunkAvailability();
        }
        if (controller.isAutoBuilding()) {
            controller.tickAutoBuild();
        } else if (controller.scheduledScanTick != NO_SCAN && level.getGameTime() >= controller.scheduledScanTick) {
            controller.scheduledScanTick = NO_SCAN;
            controller.scanNow();
        }
        if (controller.isFormed() && controller.portPos != null && level.isLoaded(controller.portPos)
                && level.getBlockEntity(controller.portPos) instanceof TianshuSupercomputerPortBlockEntity port) {
            port.applyPendingProfile();
        }
    }

    public boolean isFormed() {
        return formed && structureAvailable;
    }

    public BlockPos getPortPos() {
        return portPos;
    }

    public boolean ownsPort(BlockPos candidate) {
        return formed && candidate != null && candidate.equals(portPos);
    }

    public boolean isPortActive(BlockPos candidate) {
        return structureAvailable && ownsPort(candidate);
    }

    public String issueText() {
        return lastIssues.isEmpty() ? profileText() : lastIssues.stream()
                .map(Object::toString).collect(Collectors.joining(", "));
    }

    private String profileText() {
        if (coreProfile.mainCore() == null) return "0";
        String storage = coreProfile.storageBytes() == Long.MAX_VALUE
                ? "∞" : Long.toString(coreProfile.storageBytes());
        return coreProfile.mainCore() + ", " + storage + " bytes, "
                + coreProfile.parallelism() + " parallel";
    }

    public CpuInternalCoreProfile getCoreProfile() {
        return coreProfile;
    }

    public boolean isFastPlanningEnabled() {
        return fastPlanningEnabled;
    }

    public void toggleFastPlanning() {
        fastPlanningEnabled = !fastPlanningEnabled;
        syncPortFastPlanning();
        setChanged();
    }

    public int getPrimaryIssueOrdinal() {
        if (lastIssues.isEmpty()) return -1;
        return lastIssues.getFirst().ordinal();
    }

    public int memberCount() {
        return memberCount;
    }

    public void scheduleStructureCheck() {
        if (level != null && !level.isClientSide) {
            long targetTick = level.getGameTime() + 1L;
            if (scheduledScanTick == NO_SCAN || targetTick < scheduledScanTick) {
                scheduledScanTick = targetTick;
            }
        }
    }

    public void scanNow() {
        if (level == null || level.isClientSide) return;
        Direction orientation = getBlockState().getValue(TianshuSupercomputerControllerBlock.FACING);
        TianshuMultiblockScanAttempt attempt = TianshuMultiblockScanner.scan(level, worldPosition, orientation);
        lastIssues = attempt.issues();
        if (attempt.chunksUnavailable()) {
            suspendForUnloadedChunks();
        } else if (attempt.formed()) {
            form(attempt.result());
        } else {
            deform();
        }
    }

    public void autoBuild(ServerPlayer player) {
        if (level == null || level.isClientSide) return;
        if (isAutoBuilding()) {
            player.displayClientMessage(Component.translatable(
                    "ae2lt.tianshu.build_in_progress").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!ensureStructureChunksLoaded()) {
            return;
        }

        var plan = createAutoBuildPlan();
        if (!plan.blocked().isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "ae2lt.tianshu.build_blocked",
                    plan.blocked().size(),
                    describeBlockedPositions(plan.blocked())).withStyle(ChatFormatting.RED), false);
            return;
        }

        var requirements = autoBuildRequirements(plan);
        if (!player.getAbilities().instabuild) {
            var missing = findMissingRequirements(player, requirements);
            if (!missing.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "ae2lt.tianshu.build_missing",
                        describeMissing(missing)).withStyle(ChatFormatting.RED), false);
                return;
            }
        }

        if (plan.placements().isEmpty()) {
            finishAutoBuild(player, 0);
            return;
        }

        autoBuildPlacements = plan.placements();
        autoBuildPlayerId = player.getUUID();
        autoBuildFacing = getBlockState().getValue(TianshuSupercomputerControllerBlock.FACING);
        autoBuildPlacementIndex = 0;
        autoBuildPlacedBlocks = 0;
        nextAutoBuildTick = level.getGameTime() + AUTO_BUILD_INTERVAL_TICKS;
        scheduledScanTick = NO_SCAN;
        setChanged();
        player.displayClientMessage(Component.translatable(
                "ae2lt.tianshu.build_started",
                autoBuildPlacements.size()).withStyle(ChatFormatting.GREEN), true);
    }

    private boolean isAutoBuilding() {
        return autoBuildPlayerId != null && autoBuildPlacementIndex < autoBuildPlacements.size();
    }

    private void tickAutoBuild() {
        if (level == null || level.getGameTime() < nextAutoBuildTick) return;

        var server = level.getServer();
        var player = server != null ? server.getPlayerList().getPlayer(autoBuildPlayerId) : null;
        if (player == null) {
            clearAutoBuildSession();
            scanNow();
            return;
        }

        var placement = autoBuildPlacements.get(autoBuildPlacementIndex);
        var pos = TianshuMultiblockScanner.worldPos(worldPosition, placement.localPos(), autoBuildFacing);
        if (!level.isLoaded(pos)) {
            nextAutoBuildTick = level.getGameTime() + CHUNK_RECHECK_INTERVAL_TICKS;
            return;
        }
        var current = TianshuMultiblockScanner.componentAt(level, pos);
        if (matchesAutoBuildTarget(current, placement.target())) {
            advanceAutoBuild(player, false);
            return;
        }
        if (current != TianshuMultiblockComponent.AIR) {
            abortAutoBuildPlacement(player, pos);
            return;
        }

        BlockState state = stateForAutoBuild(placement.target());
        Item consumedItem = state.getBlock().asItem();
        if (!player.getAbilities().instabuild
                && (consumedItem == net.minecraft.world.item.Items.AIR || countItem(player, consumedItem) <= 0)) {
            abortAutoBuildMissingItem(player, consumedItem.getDescription());
            return;
        }
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            abortAutoBuildPlacement(player, pos);
            return;
        }
        if (!player.getAbilities().instabuild) {
            consumeItem(player, consumedItem, 1);
        }
        playAutoBuildPlaceSound(player, pos, state);
        advanceAutoBuild(player, true);
    }

    private boolean matchesAutoBuildTarget(
            TianshuMultiblockComponent component,
            TianshuAutoBuildPlan.Target target) {
        return switch (target) {
            case CASING -> component == TianshuMultiblockComponent.CASING;
            case COOLING -> component == TianshuMultiblockComponent.COOLING;
            case GLASS -> component == TianshuMultiblockComponent.GLASS;
            case PORT -> component == TianshuMultiblockComponent.PORT;
        };
    }

    private void playAutoBuildPlaceSound(ServerPlayer player, BlockPos pos, BlockState state) {
        var soundType = state.getSoundType(level, pos, player);
        float volume = (soundType.getVolume() + 1.0F) / 4.0F;
        float pitch = soundType.getPitch() * (0.82F + level.random.nextFloat() * 0.12F);
        level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, volume, pitch);
    }

    private void advanceAutoBuild(ServerPlayer player, boolean placed) {
        if (placed) autoBuildPlacedBlocks++;
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
        scanNow();
        player.displayClientMessage(Component.translatable(
                "ae2lt.tianshu.build_place_failed",
                describePosition(pos)).withStyle(ChatFormatting.RED), false);
    }

    private void abortAutoBuildMissingItem(ServerPlayer player, Component itemName) {
        clearAutoBuildSession();
        scanNow();
        player.displayClientMessage(Component.translatable(
                "ae2lt.tianshu.build_interrupted_missing",
                itemName).withStyle(ChatFormatting.RED), false);
    }

    private void clearAutoBuildSession() {
        autoBuildPlacements = List.of();
        autoBuildPlayerId = null;
        autoBuildPlacementIndex = 0;
        autoBuildPlacedBlocks = 0;
        nextAutoBuildTick = 0L;
        scheduledScanTick = NO_SCAN;
        setChanged();
    }

    private void finishAutoBuild(ServerPlayer player, int placedBlocks) {
        scanNow();
        if (lastIssues.contains(TianshuMultiblockScanIssue.CHUNKS_UNLOADED)) {
            return;
        }
        Component message;
        if (isFormed()) {
            message = placedBlocks == 0
                    ? Component.translatable("ae2lt.tianshu.build_already_complete")
                    : Component.translatable("ae2lt.tianshu.build_complete", placedBlocks);
        } else {
            message = placedBlocks == 0
                    ? Component.translatable("ae2lt.tianshu.build_nothing_to_place")
                    : Component.translatable("ae2lt.tianshu.build_shell_complete", placedBlocks);
        }
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.GREEN), true);
    }

    private TianshuAutoBuildPlan createAutoBuildPlan() {
        Direction facing = getBlockState().getValue(TianshuSupercomputerControllerBlock.FACING);
        return TianshuAutoBuildPlan.create(local -> TianshuMultiblockScanner.componentAt(
                level, TianshuMultiblockScanner.worldPos(worldPosition, local, facing)));
    }

    private Map<Item, Integer> autoBuildRequirements(TianshuAutoBuildPlan plan) {
        var result = new java.util.LinkedHashMap<Item, Integer>();
        for (var placement : plan.placements()) {
            Item item = stateForAutoBuild(placement.target()).getBlock().asItem();
            if (item != net.minecraft.world.item.Items.AIR) result.merge(item, 1, Integer::sum);
        }
        return result;
    }

    private BlockState stateForAutoBuild(TianshuAutoBuildPlan.Target target) {
        return switch (target) {
            case CASING -> ModBlocks.TIANSHU_SUPERCOMPUTER_CASING.get().defaultBlockState();
            case COOLING -> ModBlocks.PHASE_CHANGE_COOLING_UNIT.get().defaultBlockState();
            case GLASS -> ModBlocks.TIANSHU_SUPERCOMPUTER_GLASS.get().defaultBlockState();
            case PORT -> ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get().defaultBlockState();
        };
    }

    private Map<Item, Integer> findMissingRequirements(Player player, Map<Item, Integer> requirements) {
        var missing = new java.util.LinkedHashMap<Item, Integer>();
        for (var entry : requirements.entrySet()) {
            int available = countItem(player, entry.getKey());
            if (available < entry.getValue()) missing.put(entry.getKey(), entry.getValue() - available);
        }
        return missing;
    }

    private int countItem(Player player, Item item) {
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private void consumeItem(Player player, Item item, int amount) {
        int remaining = amount;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            var stack = inventory.getItem(i);
            if (!stack.is(item)) continue;
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            if (stack.isEmpty()) inventory.setItem(i, ItemStack.EMPTY);
            remaining -= consumed;
        }
    }

    private Component describeBlockedPositions(List<BlockPos> localPositions) {
        MutableComponent result = Component.empty();
        Direction facing = getBlockState().getValue(TianshuSupercomputerControllerBlock.FACING);
        int visible = Math.min(localPositions.size(), 4);
        for (int i = 0; i < visible; i++) {
            if (i > 0) result.append(", ");
            result.append(describePosition(TianshuMultiblockScanner.worldPos(
                    worldPosition, localPositions.get(i), facing)));
        }
        if (localPositions.size() > visible) result.append(", ...");
        return result;
    }

    private Component describeMissing(Map<Item, Integer> missing) {
        MutableComponent result = Component.empty();
        int index = 0;
        for (var entry : missing.entrySet()) {
            if (index > 0) result.append(", ");
            result.append(entry.getKey().getDescription()).append(" x").append(Integer.toString(entry.getValue()));
            index++;
        }
        return result;
    }

    private Component describePosition(BlockPos pos) {
        return Component.literal("[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]");
    }

    private void form(TianshuMultiblockScanResult result) {
        structureAvailable = true;
        waitingForChunks = false;
        nextChunkCheckTick = level.getGameTime() + CHUNK_RECHECK_INTERVAL_TICKS;
        if (formed && result.portPos().equals(portPos) && result.minPos().equals(minPos)
                && result.maxPos().equals(maxPos) && result.coreProfile().equals(coreProfile)) {
            for (BlockPos pos : result.members()) setMemberFormed(pos, true);
            syncPortConfiguration();
            syncControllerState();
            return;
        }
        clearStructureBindings();
        formed = true;
        portPos = result.portPos();
        minPos = result.minPos();
        maxPos = result.maxPos();
        memberCount = result.members().size();
        coreProfile = result.coreProfile();
        for (BlockPos pos : result.members()) setMemberFormed(pos, true);
        syncPortConfiguration();
        syncControllerState();
    }

    private void syncPortConfiguration() {
        if (level != null && structureAvailable && portPos != null && level.isLoaded(portPos)
                && level.getBlockEntity(portPos) instanceof TianshuSupercomputerPortBlockEntity port) {
            port.setFastPlanningEnabled(fastPlanningEnabled);
            port.bindToController(worldPosition, coreProfile);
        }
    }

    private void syncPortFastPlanning() {
        if (level != null && structureAvailable && portPos != null && level.isLoaded(portPos)
                && level.getBlockEntity(portPos) instanceof TianshuSupercomputerPortBlockEntity port) {
            port.setFastPlanningEnabled(fastPlanningEnabled);
        }
    }

    private void deform() {
        clearStructureBindings();
        formed = false;
        structureAvailable = false;
        waitingForChunks = false;
        nextChunkCheckTick = 0L;
        portPos = null;
        minPos = null;
        maxPos = null;
        memberCount = 0;
        coreProfile = CpuInternalCoreProfile.empty();
        syncControllerState();
    }

    public void clearStructureBindings() {
        if (level == null || minPos == null || maxPos == null) return;
        for (BlockPos mutable : BlockPos.betweenClosed(minPos, maxPos)) {
            BlockPos pos = mutable.immutable();
            if (!level.isLoaded(pos)) {
                continue;
            }
            setMemberFormed(pos, false);
            if (level.getBlockEntity(pos) instanceof TianshuSupercomputerPortBlockEntity port
                    && worldPosition.equals(port.getControllerPos())) {
                port.bindToController(null);
            }
        }
    }

    private void setMemberFormed(BlockPos pos, boolean value) {
        if (level == null || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(TianshuSupercomputerStructureBlock.FORMED)
                && state.getValue(TianshuSupercomputerStructureBlock.FORMED) != value) {
            level.setBlock(pos, state.setValue(TianshuSupercomputerStructureBlock.FORMED, value), Block.UPDATE_CLIENTS);
        } else if (state.hasProperty(TianshuSupercomputerPortBlock.FORMED)
                && state.getValue(TianshuSupercomputerPortBlock.FORMED) != value) {
            level.setBlock(pos, state.setValue(TianshuSupercomputerPortBlock.FORMED, value), Block.UPDATE_CLIENTS);
        }
    }

    private void syncControllerState() {
        BlockState state = getBlockState();
        boolean activeFormed = isFormed();
        if (state.getValue(TianshuSupercomputerControllerBlock.FORMED) != activeFormed) {
            level.setBlock(worldPosition, state.setValue(
                    TianshuSupercomputerControllerBlock.FORMED, activeFormed), Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    private void checkChunkAvailability() {
        if (level == null || (!formed && !waitingForChunks)) {
            return;
        }
        Direction facing = getBlockState().getValue(TianshuSupercomputerControllerBlock.FACING);
        if (!TianshuMultiblockScanner.areRequiredChunksLoaded(level, worldPosition, facing)) {
            lastIssues = List.of(TianshuMultiblockScanIssue.CHUNKS_UNLOADED);
            suspendForUnloadedChunks();
        } else if (!structureAvailable || waitingForChunks) {
            scheduleStructureCheck();
        }
    }

    private void suspendForUnloadedChunks() {
        boolean changed = !waitingForChunks || structureAvailable;
        waitingForChunks = true;
        structureAvailable = false;
        if (changed && level != null && portPos != null && level.isLoaded(portPos)
                && level.getBlockEntity(portPos) instanceof TianshuSupercomputerPortBlockEntity port) {
            port.suspendFromController(worldPosition);
        }
        if (changed && level != null && !level.isClientSide) {
            syncControllerState();
        }
    }

    private boolean ensureStructureChunksLoaded() {
        Direction facing = getBlockState().getValue(TianshuSupercomputerControllerBlock.FACING);
        if (TianshuMultiblockScanner.areRequiredChunksLoaded(level, worldPosition, facing)) {
            return true;
        }
        lastIssues = List.of(TianshuMultiblockScanIssue.CHUNKS_UNLOADED);
        suspendForUnloadedChunks();
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(TAG_FORMED, formed);
        if (portPos != null) tag.putLong(TAG_PORT_POS, portPos.asLong());
        if (minPos != null) tag.putLong(TAG_MIN_POS, minPos.asLong());
        if (maxPos != null) tag.putLong(TAG_MAX_POS, maxPos.asLong());
        tag.putInt(TAG_MEMBER_COUNT, memberCount);
        tag.putBoolean(TAG_FAST_PLANNING, fastPlanningEnabled);
        if (coreProfile.mainCore() != null) {
            tag.putString(TAG_MAIN_CORE, coreProfile.mainCore().name());
            tag.putInt(TAG_CAPACITY_CORES, coreProfile.capacityCoreCount());
            tag.putInt(TAG_PARALLEL_CORES, coreProfile.parallelCoreCount());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean(TAG_FORMED);
        structureAvailable = false;
        waitingForChunks = false;
        nextChunkCheckTick = 0L;
        portPos = tag.contains(TAG_PORT_POS, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_PORT_POS)) : null;
        minPos = tag.contains(TAG_MIN_POS, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_MIN_POS)) : null;
        maxPos = tag.contains(TAG_MAX_POS, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(TAG_MAX_POS)) : null;
        memberCount = tag.getInt(TAG_MEMBER_COUNT);
        fastPlanningEnabled = !tag.contains(TAG_FAST_PLANNING, Tag.TAG_BYTE)
                || tag.getBoolean(TAG_FAST_PLANNING);
        if (tag.contains(TAG_MAIN_CORE, Tag.TAG_STRING)) {
            try {
                var tier = CpuMainCoreTier.valueOf(tag.getString(TAG_MAIN_CORE));
                coreProfile = com.moakiee.ae2lt.logic.tianshu.CpuInternalCoreCalculator.calculate(
                        tier, tag.getInt(TAG_CAPACITY_CORES), tag.getInt(TAG_PARALLEL_CORES));
            } catch (IllegalArgumentException ignored) {
                coreProfile = CpuInternalCoreProfile.empty();
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        nextChunkCheckTick = level != null ? level.getGameTime() : 0L;
        scheduleStructureCheck();
    }
}
