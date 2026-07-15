package com.moakiee.ae2lt.blockentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.moakiee.ae2lt.block.MatrixPortBlock;
import com.moakiee.thunderbolt.ae2.api.crafting.IBatchCraftingProvider;
import com.moakiee.thunderbolt.ae2.api.crafting.BatchDispatchMode;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingMath;
import com.moakiee.ae2lt.logic.craft.MatrixCraftingProfile;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.BaseInternalInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
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
    private static final int BINDING_CHECK_INTERVAL_TICKS = 20;

    private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);
    private final PortPatternItemHandler itemHandler = new PortPatternItemHandler();
    private final MatrixTerminalPatternInventory terminalPatternInventory = new MatrixTerminalPatternInventory();
    private BlockPos controllerPos;
    private boolean formed;
    private long lastPatternUpdateTick = Long.MIN_VALUE;
    private MatrixPatternStorageBlockEntity exposedPatternStorage;
    private boolean exposedPatternStorageDirty = true;
    private long nextBindingCheckTick;

    public MatrixPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATRIX_PORT.get(), pos, state);
    }

    public static void serverTick(Level level,
                                  BlockPos pos,
                                  BlockState state,
                                  MatrixPortBlockEntity port) {
        if (level.isClientSide || level.getGameTime() < port.nextBindingCheckTick) {
            return;
        }
        port.nextBindingCheckTick = level.getGameTime() + BINDING_CHECK_INTERVAL_TICKS;
        port.validateControllerBinding();
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
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
        BlockPos newControllerPos = controllerPos == null ? null : controllerPos.immutable();
        updateControllerBinding(newControllerPos, newControllerPos != null);
    }

    public void suspendFromController(BlockPos expectedControllerPos) {
        if (formed && expectedControllerPos != null && expectedControllerPos.equals(controllerPos)) {
            updateControllerBinding(controllerPos, false);
        }
    }

    private void updateControllerBinding(BlockPos newControllerPos, boolean newFormed) {
        boolean formedChanged = formed != newFormed;
        this.controllerPos = newControllerPos;
        this.formed = newFormed;
        if (formedChanged) {
            onGridConnectableSidesChanged();
            if (level != null && !level.isClientSide) {
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
        }
        if (level != null && !level.isClientSide) {
            var state = getBlockState();
            if (state.hasProperty(MatrixPortBlock.FORMED)
                    && state.getValue(MatrixPortBlock.FORMED) != formed) {
                level.setBlock(worldPosition, state.setValue(MatrixPortBlock.FORMED, formed), Block.UPDATE_ALL);
            }
        }
        invalidateExposedPatternStorage();
        saveChanges();
        markForUpdate();
        requestCraftingUpdate();
    }

    public boolean isFormed() {
        return formed;
    }

    public MatrixControllerBlockEntity getController() {
        if (!formed || controllerPos == null || level == null || !level.isLoaded(controllerPos)) {
            return null;
        }
        return level.getBlockEntity(controllerPos) instanceof MatrixControllerBlockEntity controller
                && controller.isPortActive(worldPosition) ? controller : null;
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
        return formed ? getMainNode().getGrid() : null;
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
        return controller == null || controller.isCraftingBusy();
    }

    @Override
    public int getBatchCapacity(IPatternDetails details) {
        var controller = getController();
        return controller != null ? controller.getBatchCapacity(details) : 0;
    }

    @Override
    public BatchDispatchMode getBatchDispatchMode(IPatternDetails details) {
        var controller = getController();
        return controller != null ? controller.getBatchDispatchMode() : BatchDispatchMode.NORMAL;
    }

    @Override
    public int pushBatch(IPatternDetails details, KeyCounter[] oneCopyTemplate, int maxCraft) {
        var controller = getController();
        return controller != null ? controller.pushBatch(details, oneCopyTemplate, maxCraft) : maxCraft;
    }

    public boolean isConnected() {
        return formed && getMainNode().isActive() && getMainNode().getGrid() != null;
    }

    public long insertToNetwork(AEKey key, long amount) {
        var grid = getMainNode().getGrid();
        if (grid == null || key == null || amount <= 0) {
            return 0L;
        }
        return grid.getStorageService().getInventory().insert(key, amount, Actionable.MODULATE, actionSource);
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
                updateControllerBinding(null, false);
            }
        } else if (!level.getBlockState(controllerPos).is(ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get())) {
            updateControllerBinding(null, false);
        }
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
