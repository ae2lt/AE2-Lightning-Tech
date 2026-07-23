package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.block.TianshuSupercomputerPortBlock;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPool;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableSet;
import com.moakiee.ae2lt.logic.tianshu.TianshuFunctionProfile;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternRepository;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternValidator;
import com.moakiee.ae2lt.logic.tianshu.maintenance.TianshuInventoryMaintenanceService;
import com.moakiee.thunderbolt.ae2.crafting.ExtendedCraftingCpuClusterProvider;
import com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCpuPoolProvider;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.BaseInternalInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.me.helpers.MachineSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * AE network link for a Tianshu controller. Runtime state and functional services belong to the
 * controller; this block entity only exposes them to the grid and keeps link/migration metadata.
 */
public class TianshuSupercomputerPortBlockEntity extends AENetworkedBlockEntity
        implements TimeWheelCraftingCpuPoolProvider, ICraftingProvider, ICraftingRequester,
        PatternContainer {
    private static final String TAG_CONTROLLER_POS = "ControllerPos";
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_CPU_POOL = "CpuPool";
    private static final String TAG_CLOSED_LOOP_PATTERNS = "ClosedLoopPatterns";
    private static final String TAG_TIANSHU_ID = "TianshuId";
    private static final String TAG_MAINTENANCE = "InventoryMaintenance";
    private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);
    private final TianshuTerminalPatternInventory terminalPatternInventory =
            new TianshuTerminalPatternInventory();
    private final List<UUID> terminalPatternSlots = new java.util.ArrayList<>();
    private static final int BINDING_CHECK_INTERVAL_TICKS = 20;
    private BlockPos controllerPos;
    private UUID boundMachineId;
    private TimeWheelCraftingCpuPool linkedCpuPool;
    private boolean formed;

    // Compatibility staging only. These disappear after a controller imports the old port state.
    private UUID legacyTianshuId;
    private CompoundTag legacyRuntimeState;
    private CompoundTag legacyPatternState;
    private long nextBindingCheckTick;

    public TianshuSupercomputerPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIANSHU_SUPERCOMPUTER_PORT.get(), pos, state);
    }

    public static void serverTick(Level level,
                                  BlockPos pos,
                                  BlockState state,
                                  TianshuSupercomputerPortBlockEntity port) {
        if (level.isClientSide || level.getGameTime() < port.nextBindingCheckTick) {
            return;
        }
        port.nextBindingCheckTick = level.getGameTime() + BINDING_CHECK_INTERVAL_TICKS;
        port.validateControllerBinding();
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("tianshu_supercomputer_port")
                .setVisualRepresentation(ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get())
                .setIdlePowerUsage(8.0D)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(ExtendedCraftingCpuClusterProvider.class, this)
                .addService(TimeWheelCraftingCpuPoolProvider.class, this)
                .addService(ICraftingProvider.class, this)
                .addService(ICraftingRequester.class, this);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return formed ? AECableType.DENSE_SMART : AECableType.NONE;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return formed ? EnumSet.allOf(Direction.class) : Collections.emptySet();
    }

    public void bindToController(@Nullable BlockPos controllerPos) {
        if (controllerPos != null) {
            throw new IllegalArgumentException("A Tianshu link requires its controller UUID and CPU pool");
        }
        boolean bindingChanged = formed
                || this.controllerPos != null
                || boundMachineId != null
                || linkedCpuPool != null;
        boolean formedChanged = formed;
        this.controllerPos = null;
        this.boundMachineId = null;
        this.linkedCpuPool = null;
        this.formed = false;
        if (formedChanged) onGridConnectableSidesChanged();
        updateLinkState(bindingChanged);
    }

    public void bindToController(BlockPos controllerPos, UUID machineId,
                                 TimeWheelCraftingCpuPool cpuPool) {
        if (controllerPos == null || machineId == null || cpuPool == null) {
            bindToController(null);
            return;
        }
        boolean bindingChanged = !formed
                || !controllerPos.equals(this.controllerPos)
                || !machineId.equals(boundMachineId)
                || linkedCpuPool != cpuPool;
        boolean formedChanged = !formed;
        this.controllerPos = controllerPos.immutable();
        this.boundMachineId = machineId;
        this.linkedCpuPool = cpuPool;
        this.formed = true;
        this.legacyTianshuId = null;
        if (formedChanged) onGridConnectableSidesChanged();
        updateLinkState(bindingChanged);
    }

    public void suspendFromController(BlockPos expectedControllerPos) {
        if (formed && expectedControllerPos != null && expectedControllerPos.equals(controllerPos)) {
            formed = false;
            onGridConnectableSidesChanged();
            updateLinkState(true);
        }
    }

    private void updateLinkState(boolean bindingChanged) {
        boolean blockStateChanged = false;
        if (level != null && !level.isClientSide) {
            var state = getBlockState();
            if (state.hasProperty(TianshuSupercomputerPortBlock.FORMED)
                    && state.getValue(TianshuSupercomputerPortBlock.FORMED) != formed) {
                level.setBlock(worldPosition,
                        state.setValue(TianshuSupercomputerPortBlock.FORMED, formed),
                        Block.UPDATE_ALL);
                blockStateChanged = true;
            }
            // Re-notifying an unchanged binding schedules another multiblock scan through
            // neighborChanged, creating a permanent scan -> bind -> notify feedback loop.
            if (bindingChanged || blockStateChanged) {
                level.updateNeighborsAt(worldPosition, state.getBlock());
            }
            refreshCraftingProvider();
        }
        saveChanges();
        markForUpdate();
    }

    public void refreshCraftingProvider() {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            grid.getCraftingService().refreshNodeCraftingProvider(getMainNode().getNode());
        }
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public boolean isLinkedTo(BlockPos controllerPos, UUID machineId) {
        return formed && controllerPos != null && machineId != null
                && controllerPos.equals(this.controllerPos)
                && machineId.equals(boundMachineId);
    }

    public boolean isFormed() {
        return getController() != null;
    }

    public boolean isLinkActive() {
        return getController() != null && getMainNode().isActive()
                && getMainNode().getGrid() != null;
    }

    public TianshuFunctionProfile getFunctionProfile() {
        var controller = getController();
        return controller != null ? controller.getFunctionProfile() : TianshuFunctionProfile.empty();
    }

    @Nullable
    public ClosedLoopPatternRepository getClosedLoopPatternRepository() {
        var controller = getController();
        return controller != null ? controller.getClosedLoopPatternRepository() : null;
    }

    public UUID getTianshuId() {
        var controller = getController();
        return controller != null ? controller.getTianshuId()
                : boundMachineId != null ? boundMachineId : new UUID(0L, 0L);
    }

    public UUID getLegacyTianshuId() {
        return legacyTianshuId;
    }

    @Nullable
    public TianshuInventoryMaintenanceService getInventoryMaintenance() {
        var controller = getController();
        return controller != null ? controller.getInventoryMaintenance() : null;
    }

    public void tickTianshuFunctions() {
        var maintenance = getInventoryMaintenance();
        if (maintenance != null) maintenance.tick();
    }

    public void maintenanceStateChanged() {
        var controller = getController();
        if (controller != null) controller.maintenanceStateChanged();
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        var controller = getController();
        return controller != null ? controller.getRequestedJobs() : ImmutableSet.of();
    }

    @Override
    public long insertCraftedItems(
            ICraftingLink link, AEKey what, long amount, Actionable mode) {
        var controller = getController();
        return controller != null ? controller.insertCraftedItems(link, what, amount, mode) : 0L;
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        var controller = getController();
        if (controller != null) controller.jobStateChange(link);
    }

    public long insertReusableSeed(AEKey key, long amount, Actionable mode) {
        var controller = getController();
        return controller != null ? controller.insertReusableSeed(key, amount, mode) : 0L;
    }

    public long reusableSeedAmount(AEKey key) {
        var controller = getController();
        return controller != null ? controller.reusableSeedAmount(key) : 0L;
    }

    public void seedDrivesChanged() {
        var controller = getController();
        if (controller != null) controller.seedStorageChanged();
    }

    public void closedLoopPatternsChanged() {
        var controller = getController();
        if (controller != null) controller.closedLoopPatternsChanged();
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        var controller = getController();
        return controller != null ? controller.getAvailablePatterns() : List.of();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        // Closed-loop macros are contracted planning nodes, never executable provider recipes.
        return false;
    }

    @Override
    public boolean isBusy() {
        var controller = getController();
        return controller == null || !controller.isCpuActive();
    }

    @Override
    @Nullable
    public TimeWheelCraftingCpuPool getTimeWheelCraftingCpuPool() {
        return isNetworkActive() && getController() != null ? linkedCpuPool : null;
    }

    public IGrid getGrid() {
        return formed ? getMainNode().getGrid() : null;
    }

    @Override
    public boolean isVisibleInTerminal() {
        return isFormed() && getController() != null
                && getClosedLoopPatternRepository() != null;
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return terminalPatternInventory;
    }

    @Override
    public long getTerminalSortOrder() {
        var pos = getBlockPos();
        return (long) pos.getZ() << 24 ^ (long) pos.getX() << 8 ^ pos.getY();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        var capacity = getFunctionProfile().closedLoopPatternCapacity();
        var stored = getClosedLoopPatternRepository();
        return new PatternContainerGroup(
                AEItemKey.of(ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get()),
                ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get().getName(),
                List.of(Component.translatable(
                        "ae2lt.tianshu.terminal.tooltip",
                        stored != null ? stored.activePatterns().size() : 0,
                        capacity)));
    }

    public IActionSource getActionSource() {
        return actionSource;
    }

    public boolean isNetworkActive() {
        return formed && getMainNode().isActive() && getMainNode().getGrid() != null;
    }

    /**
     * AE2 keeps terminal slot indexes stable while swapping an item. The repository itself is a
     * compact ordered map, so this view keeps only transient UUID references for empty slots;
     * payload data and persistence remain owned by the controller repository.
     */
    private final class TianshuTerminalPatternInventory extends BaseInternalInventory {
        @Override
        public int size() {
            var repository = getClosedLoopPatternRepository();
            if (repository == null) return 0;
            syncSlots(repository);
            return repository.capacity();
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            var payload = payloadAt(slotIndex);
            if (payload == null || level == null) return ItemStack.EMPTY;
            var item = (com.moakiee.ae2lt.item.ClosedLoopPatternItem)
                    ModItems.CLOSED_LOOP_PATTERN.get();
            return item.createStack(payload, level.registryAccess());
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            var repository = getClosedLoopPatternRepository();
            if (repository == null || slotIndex < 0 || slotIndex >= repository.capacity()) return;
            syncSlots(repository);
            var current = payloadAt(slotIndex);
            if (stack == null || stack.isEmpty()) {
                if (current == null) return;
                repository.remove(current.patternId());
                terminalPatternSlots.set(slotIndex, null);
                notifyPatternsChanged();
                return;
            }
            var payload = readValidPayload(stack);
            if (payload == null || containsDifferentId(repository, payload, slotIndex)) return;
            if (current != null && !current.patternId().equals(payload.patternId())) {
                repository.remove(current.patternId());
            }
            var result = repository.put(payload);
            if (result == ClosedLoopPatternRepository.PutResult.ADDED
                    || result == ClosedLoopPatternRepository.PutResult.UPDATED) {
                terminalPatternSlots.set(slotIndex, payload.patternId());
                notifyPatternsChanged();
            }
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) return stack;
            var repository = getClosedLoopPatternRepository();
            if (repository == null || slot < 0 || slot >= repository.capacity()
                    || payloadAt(slot) != null) return stack;
            var payload = readValidPayload(stack);
            if (payload == null) return stack;
            syncSlots(repository);
            if (repository.size() >= repository.capacity()
                    || containsId(repository, payload.patternId())) return stack;
            if (!simulate) {
                var result = repository.put(payload);
                if (result != ClosedLoopPatternRepository.PutResult.ADDED
                        && result != ClosedLoopPatternRepository.PutResult.UPDATED) return stack;
                terminalPatternSlots.set(slot, payload.patternId());
                notifyPatternsChanged();
            }
            return stack.getCount() <= 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            var repository = getClosedLoopPatternRepository();
            if (repository == null || slot < 0 || slot >= repository.capacity()) {
                return ItemStack.EMPTY;
            }
            syncSlots(repository);
            var payload = payloadAt(slot);
            if (payload == null) return ItemStack.EMPTY;
            var extracted = getStackInSlot(slot);
            if (!simulate) {
                repository.remove(payload.patternId());
                terminalPatternSlots.set(slot, null);
                notifyPatternsChanged();
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= 0 && slot < size() ? 1 : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < size() && payloadAt(slot) == null
                    && readValidPayload(stack) != null;
        }

        private ClosedLoopPatternPayload payloadAt(int slot) {
            var repository = getClosedLoopPatternRepository();
            if (repository == null || slot < 0 || slot >= repository.capacity()) return null;
            syncSlots(repository);
            var id = terminalPatternSlots.get(slot);
            return id != null ? repository.get(id) : null;
        }

        private ClosedLoopPatternPayload readValidPayload(ItemStack stack) {
            if (level == null || !(stack.getItem() instanceof com.moakiee.ae2lt.item.ClosedLoopPatternItem item)) {
                return null;
            }
            var payload = item.readPayload(stack, level).orElse(null);
            return payload != null && ClosedLoopPatternValidator.validate(payload, level).valid()
                    ? payload : null;
        }

        private void notifyPatternsChanged() {
            var controller = getController();
            if (controller != null) controller.closedLoopPatternsChanged();
        }

        private boolean containsId(ClosedLoopPatternRepository repository, UUID id) {
            return repository.get(id) != null;
        }

        private boolean containsDifferentId(
                ClosedLoopPatternRepository repository, ClosedLoopPatternPayload payload, int slot) {
            var existing = repository.get(payload.patternId());
            var current = payloadAt(slot);
            return existing != null
                    && (current == null || !current.patternId().equals(payload.patternId()));
        }

        private void syncSlots(ClosedLoopPatternRepository repository) {
            int capacity = repository.capacity();
            while (terminalPatternSlots.size() < capacity) terminalPatternSlots.add(null);
            while (terminalPatternSlots.size() > capacity) {
                terminalPatternSlots.remove(terminalPatternSlots.size() - 1);
            }
            var active = repository.activePatterns();
            var activeIds = new java.util.HashSet<UUID>();
            for (var pattern : active) activeIds.add(pattern.patternId());
            for (int i = 0; i < terminalPatternSlots.size(); i++) {
                var id = terminalPatternSlots.get(i);
                if (id != null && !activeIds.contains(id)) terminalPatternSlots.set(i, null);
            }
            for (var pattern : active) {
                if (terminalPatternSlots.contains(pattern.patternId())) continue;
                int free = terminalPatternSlots.indexOf(null);
                if (free < 0) break;
                terminalPatternSlots.set(free, pattern.patternId());
            }
        }
    }

    @Nullable
    public TianshuSupercomputerControllerBlockEntity getController() {
        if (!formed || controllerPos == null || boundMachineId == null
                || level == null || !level.isLoaded(controllerPos)) return null;
        if (!(level.getBlockEntity(controllerPos)
                instanceof TianshuSupercomputerControllerBlockEntity controller)
                || !controller.isPersistentStateOwner()
                || !boundMachineId.equals(controller.getMachineId())
                || !controller.isPortActive(worldPosition)) {
            return null;
        }
        return controller;
    }

    public void persistRuntimeStateIfChanged() {
        var controller = getController();
        if (controller != null) controller.persistRuntimeStateIfChanged();
    }

    @Nullable
    public CompoundTag copyLegacyRuntimeState() {
        return legacyRuntimeState != null ? legacyRuntimeState.copy() : null;
    }

    public void consumeLegacyRuntimeState() {
        legacyRuntimeState = null;
        saveChanges();
    }

    @Nullable
    public CompoundTag copyLegacyPatternState() {
        return legacyPatternState != null ? legacyPatternState.copy() : null;
    }

    public void consumeLegacyPatternState() {
        legacyPatternState = null;
        saveChanges();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong(TAG_CONTROLLER_POS, controllerPos.asLong());
        tag.putBoolean(TAG_FORMED, formed);
        if (legacyTianshuId != null) tag.putUUID(TAG_TIANSHU_ID, legacyTianshuId);
        if (legacyRuntimeState != null) {
            if (legacyRuntimeState.contains(TAG_MAINTENANCE, Tag.TAG_COMPOUND)) {
                tag.put(TAG_MAINTENANCE,
                        legacyRuntimeState.getCompound(TAG_MAINTENANCE).copy());
            }
            if (legacyRuntimeState.contains(TAG_CPU_POOL, Tag.TAG_COMPOUND)) {
                tag.put(TAG_CPU_POOL, legacyRuntimeState.getCompound(TAG_CPU_POOL).copy());
            }
        }
        if (legacyPatternState != null) {
            tag.put(TAG_CLOSED_LOOP_PATTERNS, legacyPatternState.copy());
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        controllerPos = tag.contains(TAG_CONTROLLER_POS, Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong(TAG_CONTROLLER_POS)) : null;
        // A position cache is not authority. Stay disconnected until the loaded controller
        // reclaims its UUID and explicitly rebinds this link.
        formed = false;
        boundMachineId = null;
        linkedCpuPool = null;
        legacyTianshuId = tag.hasUUID(TAG_TIANSHU_ID) ? tag.getUUID(TAG_TIANSHU_ID) : null;
        legacyPatternState = tag.contains(TAG_CLOSED_LOOP_PATTERNS, Tag.TAG_COMPOUND)
                ? tag.getCompound(TAG_CLOSED_LOOP_PATTERNS).copy() : null;
        var runtime = new CompoundTag();
        if (tag.contains(TAG_MAINTENANCE, Tag.TAG_COMPOUND)) {
            runtime.put(TAG_MAINTENANCE, tag.getCompound(TAG_MAINTENANCE).copy());
        }
        if (tag.contains(TAG_CPU_POOL, Tag.TAG_COMPOUND)) {
            runtime.put(TAG_CPU_POOL, tag.getCompound(TAG_CPU_POOL).copy());
        }
        legacyRuntimeState = runtime.isEmpty() ? null : runtime;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        nextBindingCheckTick = level != null ? level.getGameTime() : 0L;
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get().asItem();
    }

    private void validateControllerBinding() {
        if (level == null || level.isClientSide || controllerPos == null) {
            return;
        }
        if (!level.isLoaded(controllerPos)) {
            suspendFromController(controllerPos);
            return;
        }
        if (level.getBlockEntity(controllerPos) instanceof TianshuSupercomputerControllerBlockEntity controller) {
            if (controller.isPortActive(worldPosition)) {
                if (!formed) {
                    controller.scheduleStructureCheck();
                }
            } else if (controller.ownsPort(worldPosition)) {
                suspendFromController(controllerPos);
                controller.scheduleStructureCheck();
            } else {
                bindToController(null);
            }
        } else if (!level.getBlockState(controllerPos).is(ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get())) {
            bindToController(null);
        }
    }
}
