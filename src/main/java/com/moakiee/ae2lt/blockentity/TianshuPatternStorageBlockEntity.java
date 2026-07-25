package com.moakiee.ae2lt.blockentity;

import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import com.moakiee.ae2lt.logic.tianshu.TianshuFunctionProfile;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternRepository;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternValidator;
import com.moakiee.ae2lt.logic.terminal.InternalPatternContainerLink;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.BaseInternalInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternContainer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One 36-entry physical closed-loop-pattern warehouse. */
public final class TianshuPatternStorageBlockEntity extends BlockEntity implements PatternContainer {
    private static final String TAG_PATTERNS = "ClosedLoopPatterns";
    private static final String TAG_PORT_POS = "PortPos";
    private final ClosedLoopPatternRepository patterns = new ClosedLoopPatternRepository(
            () -> TianshuFunctionProfile.PATTERNS_PER_CLOSED_LOOP_STORAGE);
    private final List<ClosedLoopPatternPayload> terminalPatternSlots = new ArrayList<>();
    private final InternalInventory terminalPatternInventory = new TerminalPatternInventory();
    private final InternalPatternContainerLink terminalLink;
    private BlockPos portPos;

    public TianshuPatternStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TIANSHU_PATTERN_STORAGE.get(), pos, state);
        terminalLink = new InternalPatternContainerLink(this, ModBlocks.CLOSED_LOOP_PATTERN_STORAGE.get());
    }

    public List<ClosedLoopPatternPayload> patterns() {
        return patterns.patterns();
    }

    public void replacePatterns(List<ClosedLoopPatternPayload> payloads) {
        patterns.replaceAll(payloads);
        setChanged();
    }

    public void bindToPort(BlockPos newPortPos) {
        var immutable = newPortPos == null ? null : newPortPos.immutable();
        boolean changed = !java.util.Objects.equals(portPos, immutable);
        portPos = immutable;
        if (portPos == null) {
            terminalLink.disconnect();
        } else {
            var port = resolvePort();
            if (port != null && port.isFormed()) {
                terminalLink.bind(port.getMainNode());
            } else {
                terminalLink.disconnect();
            }
        }
        if (changed) {
            setChanged();
        }
    }

    public BlockPos getPortPos() {
        return portPos;
    }

    @Override
    public IGrid getGrid() {
        return terminalLink.getGrid();
    }

    @Override
    public boolean isVisibleInTerminal() {
        var port = resolvePort();
        return port != null && port.isFormed() && terminalLink.isActive();
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
        return new PatternContainerGroup(
                AEItemKey.of(ModBlocks.CLOSED_LOOP_PATTERN_STORAGE.get()),
                ModBlocks.CLOSED_LOOP_PATTERN_STORAGE.get().getName(),
                List.of(Component.translatable(
                        "ae2lt.tianshu.terminal.tooltip",
                        patterns.size(),
                        patterns.capacity())));
    }

    public void dropStoredPatterns(Level level, BlockPos pos) {
        var item = (ClosedLoopPatternItem) ModItems.CLOSED_LOOP_PATTERN.get();
        for (var payload : patterns.patterns()) {
            Block.popResource(level, pos, item.createStack(payload, level.registryAccess()));
        }
        patterns.clear();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var patternTag = new CompoundTag();
        patterns.writeTo(patternTag, registries);
        tag.put(TAG_PATTERNS, patternTag);
        if (portPos != null) tag.putLong(TAG_PORT_POS, portPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        patterns.readFrom(tag.getCompound(TAG_PATTERNS), registries);
        portPos = tag.contains(TAG_PORT_POS, Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong(TAG_PORT_POS)) : null;
    }

    @Override
    public void onChunkUnloaded() {
        terminalLink.disconnect();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        terminalLink.disconnect();
        super.setRemoved();
    }

    private TianshuSupercomputerPortBlockEntity resolvePort() {
        if (portPos == null || level == null || !level.isLoaded(portPos)) {
            return null;
        }
        return level.getBlockEntity(portPos) instanceof TianshuSupercomputerPortBlockEntity port
                ? port : null;
    }

    private void notifyPatternsChanged() {
        setChanged();
        var port = resolvePort();
        var controller = port != null ? port.getController() : null;
        if (controller != null) {
            controller.patternWarehouseChanged();
        }
    }

    private final class TerminalPatternInventory extends BaseInternalInventory {
        @Override
        public int size() {
            syncSlots();
            return patterns.capacity();
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            var payload = payloadAt(slotIndex);
            if (payload == null || level == null) {
                return ItemStack.EMPTY;
            }
            var item = (ClosedLoopPatternItem) ModItems.CLOSED_LOOP_PATTERN.get();
            return item.createStack(payload, level.registryAccess());
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            if (slotIndex < 0 || slotIndex >= patterns.capacity()) {
                return;
            }
            syncSlots();
            var current = payloadAt(slotIndex);
            if (stack == null || stack.isEmpty()) {
                if (current != null && patterns.remove(current)) {
                    terminalPatternSlots.set(slotIndex, null);
                    notifyPatternsChanged();
                }
                return;
            }
            var payload = readValidPayload(stack);
            if (payload == null) {
                return;
            }
            var result = current == null ? patterns.add(payload) : patterns.replace(current, payload);
            if (result == ClosedLoopPatternRepository.PutResult.ADDED
                    || result == ClosedLoopPatternRepository.PutResult.UPDATED) {
                terminalPatternSlots.set(slotIndex, payload);
                notifyPatternsChanged();
            }
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()
                    || slot < 0 || slot >= patterns.capacity()) {
                return stack;
            }
            syncSlots();
            if (payloadAt(slot) != null || patterns.size() >= patterns.capacity()) {
                return stack;
            }
            var payload = readValidPayload(stack);
            if (payload == null) {
                return stack;
            }
            if (!simulate) {
                if (patterns.add(payload) != ClosedLoopPatternRepository.PutResult.ADDED) {
                    return stack;
                }
                terminalPatternSlots.set(slot, payload);
                notifyPatternsChanged();
            }
            return stack.getCount() <= 1
                    ? ItemStack.EMPTY
                    : stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0 || slot < 0 || slot >= patterns.capacity()) {
                return ItemStack.EMPTY;
            }
            syncSlots();
            var payload = payloadAt(slot);
            if (payload == null) {
                return ItemStack.EMPTY;
            }
            var extracted = getStackInSlot(slot);
            if (!simulate && patterns.remove(payload)) {
                terminalPatternSlots.set(slot, null);
                notifyPatternsChanged();
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= 0 && slot < patterns.capacity() ? 1 : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < patterns.capacity()
                    && payloadAt(slot) == null
                    && readValidPayload(stack) != null;
        }

        private ClosedLoopPatternPayload payloadAt(int slot) {
            if (slot < 0 || slot >= patterns.capacity()) {
                return null;
            }
            syncSlots();
            var payload = terminalPatternSlots.get(slot);
            return patterns.indexOf(payload) >= 0 ? payload : null;
        }

        private ClosedLoopPatternPayload readValidPayload(ItemStack stack) {
            if (level == null || !(stack.getItem() instanceof ClosedLoopPatternItem item)) {
                return null;
            }
            var payload = item.readPayload(stack, level).orElse(null);
            return payload != null && ClosedLoopPatternValidator.validate(payload, level).valid()
                    ? payload : null;
        }

        private void syncSlots() {
            int capacity = patterns.capacity();
            while (terminalPatternSlots.size() < capacity) {
                terminalPatternSlots.add(null);
            }
            while (terminalPatternSlots.size() > capacity) {
                terminalPatternSlots.removeLast();
            }
            var active = patterns.activePatterns();
            for (int i = 0; i < terminalPatternSlots.size(); i++) {
                var pattern = terminalPatternSlots.get(i);
                if (pattern != null && !containsReference(active, pattern)) {
                    terminalPatternSlots.set(i, null);
                }
            }
            for (var pattern : active) {
                if (containsReference(terminalPatternSlots, pattern)) {
                    continue;
                }
                int free = terminalPatternSlots.indexOf(null);
                if (free < 0) {
                    break;
                }
                terminalPatternSlots.set(free, pattern);
            }
        }

        private boolean containsReference(
                List<ClosedLoopPatternPayload> payloads,
                ClosedLoopPatternPayload candidate) {
            for (var payload : payloads) {
                if (payload == candidate) {
                    return true;
                }
            }
            return false;
        }
    }
}
