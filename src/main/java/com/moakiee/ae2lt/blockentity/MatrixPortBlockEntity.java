package com.moakiee.ae2lt.blockentity;

import java.util.List;
import java.util.UUID;

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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.BaseInternalInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.me.helpers.MachineSource;

public class MatrixPortBlockEntity extends AENetworkedBlockEntity
        implements IBatchCraftingProvider, PatternContainer {
    private static final String TAG_CONTROLLER_POS = "ControllerPos";
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_CLUSTER = "Cluster";

    private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);
    private final PortPatternItemHandler itemHandler = new PortPatternItemHandler();
    private final MatrixTerminalPatternInventory terminalPatternInventory = new MatrixTerminalPatternInventory();
    private BlockPos controllerPos;
    private UUID boundMachineId;
    private CompoundTag legacyClusterState;
    private boolean formed;
    private long lastPatternUpdateTick = Long.MIN_VALUE;
    private MatrixPatternStorageBlockEntity exposedPatternStorage;
    private boolean exposedPatternStorageDirty = true;

    public MatrixPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATRIX_PORT.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("matter_warping_matrix_port")
                .setVisualRepresentation(ModBlocks.MATTER_WARPING_MATRIX_PORT.get())
                .setIdlePowerUsage(8.0D)
                .addService(ICraftingProvider.class, this);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return formed ? AECableType.DENSE_SMART : AECableType.NONE;
    }

    @Override
    public java.util.Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return formed ? java.util.EnumSet.allOf(Direction.class) : java.util.Set.of();
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
        this.controllerPos = null;
        this.boundMachineId = null;
        this.formed = false;
        updateLinkState();
    }

    public void bindToController(BlockPos controllerPos, UUID machineId) {
        if (controllerPos == null || machineId == null) {
            bindToController(null);
            return;
        }
        this.controllerPos = controllerPos.immutable();
        this.boundMachineId = machineId;
        this.formed = true;
        updateLinkState();
    }

    private void updateLinkState() {
        invalidateExposedPatternStorage();
        if (level != null && !level.isClientSide) {
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
                || !boundMachineId.equals(controller.getMachineId())) {
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
        long now = level != null ? level.getGameTime() : Long.MIN_VALUE;
        if (now == Long.MIN_VALUE || lastPatternUpdateTick != now) {
            lastPatternUpdateTick = now;
            requestCraftingUpdate();
        }
    }

    @Override
    public IGrid getGrid() {
        return isFormed() ? getMainNode().getGrid() : null;
    }

    @Override
    public boolean isVisibleInTerminal() {
        return formed && getController() != null;
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return terminalPatternInventory;
    }

    @Override
    public long getTerminalSortOrder() {
        var pos = getBlockPos();
        return (long) pos.getZ() << 24
                ^ (long) pos.getX() << 8
                ^ pos.getY();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        return new PatternContainerGroup(
                AEItemKey.of(ModBlocks.MATTER_WARPING_MATRIX_PORT.get()),
                ModBlocks.MATTER_WARPING_MATRIX_PORT.get().getName(),
                List.of(Component.translatable(
                        "ae2lt.matrix.terminal.tooltip",
                        getPatternStorages().size(),
                        terminalPatternInventory.size())));
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
    public int getBatchCapacity(IPatternDetails details) {
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
    public int pushBatch(IPatternDetails details, KeyCounter[] oneCopyTemplate, int maxCraft) {
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

    private void invalidateExposedPatternStorage() {
        exposedPatternStorageDirty = true;
    }

    private MatrixPatternStorageBlockEntity getExposedPatternStorage() {
        if (exposedPatternStorageDirty) {
            exposedPatternStorage = selectExposedPatternStorage();
            exposedPatternStorageDirty = false;
        }
        return exposedPatternStorage;
    }

    private MatrixPatternStorageBlockEntity selectExposedPatternStorage() {
        var firstStorage = (MatrixPatternStorageBlockEntity) null;
        var firstFree = (MatrixPatternStorageBlockEntity) null;
        for (var storage : getPatternStorages()) {
            if (firstStorage == null) {
                firstStorage = storage;
            }
            if (!storage.isEmpty()) {
                return storage;
            }
            if (firstFree == null && storage.hasFreeSlot()) {
                firstFree = storage;
            }
        }
        return firstFree != null ? firstFree : firstStorage;
    }

    private void requestCraftingUpdate() {
        if (getMainNode().isReady()) {
            ICraftingProvider.requestUpdate(getMainNode());
        }
    }

    private TerminalPatternSlot terminalPatternSlot(int slot) {
        if (slot < 0) {
            return null;
        }
        int remaining = slot;
        for (var storage : getPatternStorages()) {
            int capacity = storage.capacity();
            if (remaining < capacity) {
                return new TerminalPatternSlot(storage, remaining);
            }
            remaining -= capacity;
        }
        return null;
    }

    private record TerminalPatternSlot(MatrixPatternStorageBlockEntity storage, int slot) {
    }

    private final class MatrixTerminalPatternInventory extends BaseInternalInventory {
        @Override
        public int size() {
            int slots = 0;
            for (var storage : getPatternStorages()) {
                slots += storage.capacity();
            }
            return slots;
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
            var storage = exposedStorage();
            return storage != null ? storage.capacity() : 0;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            var storage = requireStorage(slot);
            return storage == null ? ItemStack.EMPTY : storage.getInventory().getStackInSlot(slot);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            var storage = requireStorage(slot);
            if (storage != null) {
                storage.getInventory().setStackInSlot(slot, stack);
            }
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            var exposed = requireStorage(slot);
            if (exposed == null || !exposed.isValidPatternStack(stack)) {
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
            var storage = requireStorage(slot);
            return storage == null ? ItemStack.EMPTY : storage.getInventory().extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return requireStorage(slot) == null ? 0 : 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            var storage = requireStorage(slot);
            return storage != null && storage.isValidPatternStack(stack);
        }

        private MatrixPatternStorageBlockEntity requireStorage(int slot) {
            var storage = exposedStorage();
            if (storage == null || slot < 0 || slot >= storage.capacity()) {
                return null;
            }
            return storage;
        }

        private MatrixPatternStorageBlockEntity exposedStorage() {
            return getExposedPatternStorage();
        }
    }
}
