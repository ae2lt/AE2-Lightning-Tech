package com.moakiee.ae2lt.blockentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.moakiee.ae2lt.block.MatrixPortBlock;
import com.moakiee.thunderbolt.ae2.api.crafting.IBatchCraftingProvider;
import com.moakiee.thunderbolt.ae2.api.crafting.BatchDispatchMode;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingMath;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingProfile;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.BaseInternalInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.helpers.MachineSource;

public class MatrixPortBlockEntity extends AENetworkedBlockEntity
        implements IBatchCraftingProvider {
    private static final String TAG_CONTROLLER_POS = "ControllerPos";
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_CLUSTER = "Cluster";
    private static final int BINDING_CHECK_INTERVAL_TICKS = 20;

    private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);
    private final PortPatternItemHandler itemHandler = new PortPatternItemHandler();
    private final MatrixTerminalPatternInventory terminalPatternInventory = new MatrixTerminalPatternInventory();
    private BlockPos controllerPos;
    private UUID boundMachineId;
    private CompoundTag legacyClusterState;
    private boolean formed;
    private boolean patternUpdatePending;
    private List<MatrixPatternStorageBlockEntity> exposedPatternStorages = List.of();
    private boolean exposedPatternStorageDirty = true;
    private List<TerminalPatternSlot> terminalPatternSlots = List.of();
    private boolean terminalPatternSlotsDirty = true;
    private long nextBindingCheckTick;

    public MatrixPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATRIX_PORT.get(), pos, state);
    }

    public static void serverTick(Level level,
                                  BlockPos pos,
                                  BlockState state,
                                  MatrixPortBlockEntity port) {
        if (level.isClientSide) {
            return;
        }
        port.flushPatternUpdate();
        if (level.getGameTime() < port.nextBindingCheckTick) {
            return;
        }
        port.nextBindingCheckTick = level.getGameTime() + BINDING_CHECK_INTERVAL_TICKS;
        port.validateControllerBinding();
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                // Keep the legacy nested NBT key; the registry alias only migrates registry IDs.
                .setTagName("matter_warping_matrix_port")
                .setVisualRepresentation(ModBlocks.MATTER_WARPING_MATRIX_PORT.get())
                .setIdlePowerUsage(8.0D)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(ICraftingProvider.class, this);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return formed ? AECableType.DENSE_SMART : AECableType.NONE;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return formed ? EnumSet.allOf(Direction.class) : Collections.emptySet();
    }

    public IItemHandlerModifiable getPatternItemHandler() {
        return itemHandler;
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public void bindToController(BlockPos controllerPos) {
        if (controllerPos != null) {
            throw new IllegalArgumentException("A matrix link requires its controller UUID");
        }
        boolean bindingChanged = formed || this.controllerPos != null || boundMachineId != null;
        boolean formedChanged = formed;
        this.controllerPos = null;
        this.boundMachineId = null;
        this.formed = false;
        if (formedChanged) onGridConnectableSidesChanged();
        updateLinkState(bindingChanged);
    }

    public void bindToController(BlockPos controllerPos, UUID machineId) {
        if (controllerPos == null || machineId == null) {
            bindToController(null);
            return;
        }
        boolean bindingChanged = !formed
                || !controllerPos.equals(this.controllerPos)
                || !machineId.equals(boundMachineId);
        boolean formedChanged = !formed;
        this.controllerPos = controllerPos.immutable();
        this.boundMachineId = machineId;
        this.formed = true;
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
            if (state.hasProperty(MatrixPortBlock.FORMED)
                    && state.getValue(MatrixPortBlock.FORMED) != formed) {
                level.setBlock(worldPosition, state.setValue(MatrixPortBlock.FORMED, formed), Block.UPDATE_ALL);
                blockStateChanged = true;
            }
        }
        invalidateExposedPatternStorage();
        invalidateTerminalPatternSlots();
        // Re-notifying an unchanged binding schedules another multiblock scan through
        // neighborChanged, creating a permanent scan -> bind -> notify feedback loop.
        if ((bindingChanged || blockStateChanged) && level != null && !level.isClientSide) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
        saveChanges();
        markForUpdate();
        requestCraftingUpdate();
    }

    public boolean isFormed() {
        var controller = getController();
        return formed && boundMachineId != null && controller != null;
    }

    public boolean isLinkedTo(BlockPos controllerPos, UUID machineId) {
        return formed && controllerPos != null && machineId != null
                && controllerPos.equals(this.controllerPos)
                && machineId.equals(boundMachineId);
    }

    public MatrixControllerBlockEntity getController() {
        if (!formed || controllerPos == null || level == null || !level.isLoaded(controllerPos)) {
            return null;
        }
        if (!(level.getBlockEntity(controllerPos) instanceof MatrixControllerBlockEntity controller)
                || !controller.isPersistentStateOwner() || boundMachineId == null
                || !boundMachineId.equals(controller.getMachineId())
                || !controller.isPortActive(worldPosition)) {
            return null;
        }
        return controller;
    }

    public List<MatrixPatternStorageBlockEntity> getPatternStorages() {
        var controller = getController();
        return controller != null ? controller.findPatternStorages() : List.of();
    }

    public MatrixCraftingProfile getCraftingProfile() {
        var controller = getController();
        return controller != null ? controller.getCraftingProfile() : MatrixCraftingProfile.empty();
    }

    public MatrixCraftingMath.Snapshot getLimiterSnapshot() {
        var controller = getController();
        return controller != null
                ? controller.getLimiterSnapshot()
                : MatrixCraftingMath.idleSnapshot(0.0D, 0.0D);
    }

    public boolean isWorking() {
        var controller = getController();
        return controller != null && controller.isWorking();
    }

    public void patternsChanged() {
        invalidateExposedPatternStorage();
        // Several slots can change in one server tick (pattern-terminal transfers and storage
        // upgrades do this). Publish once on the next block-entity tick, after every mutation,
        // instead of refreshing on the first mutation and dropping the remaining changes.
        patternUpdatePending = true;
    }

    public IGrid getGrid() {
        return isFormed() ? getMainNode().getGrid() : null;
    }

    public InternalInventory getTerminalPatternInventory() {
        return terminalPatternInventory;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        var controller = getController();
        return controller != null ? controller.getAvailablePatterns() : List.of();
    }

    @Override
    public boolean isBusy() {
        var controller = getController();
        return controller == null || controller.isMatrixBusy();
    }

    @Override
    public long getBatchCapacity(IPatternDetails details) {
        var controller = getController();
        return controller != null ? controller.getBatchCapacity(details) : 0;
    }

    @Override
    public boolean supportsSingleSeedBatch() {
        return isFormed();
    }

    @Override
    public BatchDispatchMode getBatchDispatchMode(IPatternDetails details) {
        var controller = getController();
        return controller != null ? controller.getBatchDispatchMode() : BatchDispatchMode.NORMAL;
    }

    @Override
    public long pushBatch(IPatternDetails details, KeyCounter[] oneCopyTemplate, long maxCraft) {
        var controller = getController();
        return controller != null
                ? controller.pushBatch(details, oneCopyTemplate, maxCraft) : maxCraft;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        var controller = getController();
        return controller != null && controller.pushPattern(patternDetails, inputHolder);
    }

    public boolean isLinkConnected() {
        return isFormed() && getMainNode().isActive() && getMainNode().getGrid() != null;
    }

    public long insertToNetworkLink(AEKey key, long amount) {
        var grid = getMainNode().getGrid();
        if (grid == null || key == null || amount <= 0) {
            return 0L;
        }
        return grid.getStorageService().getInventory().insert(key, amount, Actionable.MODULATE, actionSource);
    }

    public boolean isConnected() {
        return isLinkConnected();
    }

    public long insertToNetwork(AEKey key, long amount) {
        return insertToNetworkLink(key, amount);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) {
            tag.putLong(TAG_CONTROLLER_POS, controllerPos.asLong());
        }
        tag.putBoolean(TAG_FORMED, formed);
        // Retain old port-owned state only until a controller UUID can migrate it to SavedData.
        if (legacyClusterState != null) tag.put(TAG_CLUSTER, legacyClusterState.copy());
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        controllerPos = tag.contains(TAG_CONTROLLER_POS, Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong(TAG_CONTROLLER_POS))
                : null;
        // Reconnect only after the controller has reclaimed the UUID-backed runtime.
        formed = false;
        boundMachineId = null;
        legacyClusterState = null;
        if (tag.contains(TAG_CLUSTER, Tag.TAG_COMPOUND)) {
            legacyClusterState = tag.getCompound(TAG_CLUSTER).copy();
        }
        invalidateExposedPatternStorage();
    }

    public void spawnToWorld(AEKey key, long amount) {
        Level level = getLevel();
        if (level == null || level.isClientSide || key == null || amount <= 0) {
            return;
        }
        var drops = new ArrayList<ItemStack>();
        key.addDrops(amount, drops, level, getBlockPos());
        for (var drop : drops) {
            if (!drop.isEmpty()) {
                Block.popResource(level, getBlockPos(), drop);
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        nextBindingCheckTick = level != null ? level.getGameTime() : 0L;
    }

    @Override
    public void onReady() {
        super.onReady();
        // AE2 creates this managed node at the end of the server-level tick. If the controller
        // restored the multiblock earlier in that tick, its first attempt to attach the internal
        // pattern-container nodes could not succeed. Retry the publication once now that the
        // port node is ready, without polling or repeatedly scanning a broken structure.
        var controller = getController();
        if (controller != null) {
            controller.scheduleStructureCheck();
        }
    }

    public CompoundTag copyLegacyClusterState() {
        return legacyClusterState != null ? legacyClusterState.copy() : null;
    }

    public void consumeLegacyClusterState() {
        legacyClusterState = null;
        saveChanges();
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModBlocks.MATTER_WARPING_MATRIX_PORT.get().asItem();
    }

    private void validateControllerBinding() {
        if (level == null || level.isClientSide || controllerPos == null) {
            return;
        }
        if (!level.isLoaded(controllerPos)) {
            suspendFromController(controllerPos);
            return;
        }
        if (level.getBlockEntity(controllerPos) instanceof MatrixControllerBlockEntity controller) {
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
        } else if (!level.getBlockState(controllerPos).is(ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get())) {
            bindToController(null);
        }
    }

    private void invalidateExposedPatternStorage() {
        exposedPatternStorageDirty = true;
    }

    private void invalidateTerminalPatternSlots() {
        terminalPatternSlotsDirty = true;
    }

    private List<TerminalPatternSlot> getTerminalPatternSlots() {
        // Keep the controller's structural safety check in the access path. The controller
        // memoizes it for the current tick, so thousands of menu reads pay for one physical
        // validation rather than one validation each. If that validation suspends the port,
        // updateLinkState marks this mapping dirty before we use a removed storage.
        var storages = getPatternStorages();
        if (terminalPatternSlotsDirty) {
            var slots = new ArrayList<TerminalPatternSlot>();
            for (var storage : storages) {
                for (int slot = 0; slot < storage.capacity(); slot++) {
                    slots.add(new TerminalPatternSlot(storage, slot));
                }
            }
            terminalPatternSlots = List.copyOf(slots);
            terminalPatternSlotsDirty = false;
        }
        return terminalPatternSlots;
    }

    private List<MatrixPatternStorageBlockEntity> getExposedPatternStorages() {
        if (exposedPatternStorageDirty) {
            exposedPatternStorages = selectExposedPatternStorages();
            exposedPatternStorageDirty = false;
        }
        return exposedPatternStorages;
    }

    private List<MatrixPatternStorageBlockEntity> selectExposedPatternStorages() {
        var storages = getPatternStorages();
        var readable = storages.stream()
                .filter(storage -> !storage.isEmpty())
                .findFirst()
                .orElse(null);
        var writable = storages.stream()
                .filter(storage -> storage != readable && storage.hasFreeSlot())
                .findFirst()
                .orElse(null);
        if (readable == null) {
            return writable != null ? List.of(writable) : List.of();
        }
        if (writable == null && readable.hasFreeSlot()) {
            return List.of(readable);
        }
        return writable != null ? List.of(readable, writable) : List.of(readable);
    }

    private void requestCraftingUpdate() {
        if (getMainNode().isReady()) {
            ICraftingProvider.requestUpdate(getMainNode());
            patternUpdatePending = false;
        }
    }

    private void flushPatternUpdate() {
        if (patternUpdatePending) {
            requestCraftingUpdate();
        }
    }

    private TerminalPatternSlot terminalPatternSlot(int slot) {
        var slots = getTerminalPatternSlots();
        return slot >= 0 && slot < slots.size() ? slots.get(slot) : null;
    }

    private record TerminalPatternSlot(MatrixPatternStorageBlockEntity storage, int slot) {
    }

    private final class MatrixTerminalPatternInventory extends BaseInternalInventory {
        @Override
        public int size() {
            return getTerminalPatternSlots().size();
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            var slot = terminalPatternSlot(slotIndex);
            return slot == null ? ItemStack.EMPTY : slot.storage().getInventory().getStackInSlot(slot.slot());
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            var slot = terminalPatternSlot(slotIndex);
            if (slot == null) {
                return;
            }
            if (stack != null && !stack.isEmpty() && !slot.storage().isValidPatternStack(stack)) {
                return;
            }
            slot.storage().getInventory().setStackInSlot(slot.slot(), stack == null ? ItemStack.EMPTY : stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            var target = terminalPatternSlot(slot);
            if (target == null) {
                return stack;
            }
            return target.storage().getInventory().insertItem(target.slot(), stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            var target = terminalPatternSlot(slot);
            if (target == null) {
                return ItemStack.EMPTY;
            }
            return target.storage().getInventory().extractItem(target.slot(), amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return terminalPatternSlot(slot) == null ? 0 : 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            var target = terminalPatternSlot(slot);
            return target != null && target.storage().isValidPatternStack(stack);
        }
    }

    private final class PortPatternItemHandler implements IItemHandlerModifiable {
        @Override
        public int getSlots() {
            int slots = 0;
            for (var storage : getExposedPatternStorages()) {
                slots += storage.capacity();
            }
            return slots;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            var target = exposedPatternSlot(slot);
            return target == null
                    ? ItemStack.EMPTY
                    : target.storage().getInventory().getStackInSlot(target.slot());
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            var target = exposedPatternSlot(slot);
            if (target != null) {
                target.storage().getInventory().setStackInSlot(target.slot(), stack);
            }
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            var target = exposedPatternSlot(slot);
            if (target == null || !target.storage().isValidPatternStack(stack)) {
                return stack;
            }
            var remainder = stack.copy();
            for (var storage : getPatternStorages()) {
                for (int i = 0; i < storage.capacity(); i++) {
                    remainder = storage.getInventory().insertItem(i, remainder, simulate);
                    if (remainder.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            var target = exposedPatternSlot(slot);
            return target == null
                    ? ItemStack.EMPTY
                    : target.storage().getInventory().extractItem(target.slot(), amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return exposedPatternSlot(slot) == null ? 0 : 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            var target = exposedPatternSlot(slot);
            return target != null && target.storage().isValidPatternStack(stack);
        }

        private TerminalPatternSlot exposedPatternSlot(int slot) {
            if (slot < 0) {
                return null;
            }
            for (var storage : getExposedPatternStorages()) {
                if (slot < storage.capacity()) {
                    return new TerminalPatternSlot(storage, slot);
                }
                slot -= storage.capacity();
            }
            return null;
        }
    }
}
