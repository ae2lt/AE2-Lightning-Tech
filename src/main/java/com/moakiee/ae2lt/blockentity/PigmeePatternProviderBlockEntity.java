package com.moakiee.ae2lt.blockentity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.helpers.MachineSource;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import com.moakiee.ae2lt.block.PigmeePatternProviderBlock;
import com.moakiee.ae2lt.logic.PigmeePatternProviderReturnInventory;
import com.moakiee.ae2lt.menu.PigmeePatternProviderMenu;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class PigmeePatternProviderBlockEntity extends AENetworkedBlockEntity
        implements InternalInventoryHost, ICraftingProvider {
    public static final int PATTERN_SLOT_COUNT = 3;

    private static final String TAG_PATTERNS = "Patterns";
    private static final String TAG_RETURN_INVENTORY = "ReturnInventory";
    private static final String TAG_PENDING_DISPATCH = "PendingDispatch";
    private static final String TAG_PENDING_DIRECTION = "PendingDirection";

    private final AppEngInternalInventory patternInventory = new AppEngInternalInventory(this, PATTERN_SLOT_COUNT, 1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return PatternDetailsHelper.isEncodedPattern(stack);
        }
    };
    private final PigmeePatternProviderReturnInventory returnInventory =
            new PigmeePatternProviderReturnInventory(this::onReturnInventoryChanged);
    private final List<IPatternDetails> patterns = new ArrayList<>(PATTERN_SLOT_COUNT);
    private final List<GenericStack> pendingDispatch = new ArrayList<>();
    private @Nullable Direction pendingDirection;
    private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);

    public PigmeePatternProviderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIGMEE_PATTERN_PROVIDER.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setTagName("pigmee_pattern_provider")
                .setVisualRepresentation(ModBlocks.PIGMEE_PATTERN_PROVIDER.get())
                .setIdlePowerUsage(1.0D)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(ICraftingProvider.class, this);
    }

    public static void serverTick(
            Level level,
            BlockPos pos,
            BlockState state,
            PigmeePatternProviderBlockEntity blockEntity) {
        if (!level.isClientSide() && blockEntity.getMainNode().isActive()) {
            blockEntity.flushPendingDispatch();
            blockEntity.returnItemsToNetwork();
        }
    }

    public InternalInventory getPatternInventory() {
        return patternInventory;
    }

    public PigmeePatternProviderReturnInventory getReturnInventory() {
        return returnInventory;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return patterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!getMainNode().isActive() || isBusy() || !patterns.contains(patternDetails) || level == null) {
            return false;
        }

        var inventoryTargets = new ArrayList<Direction>();
        for (var direction : getActiveTargetDirections()) {
            BlockPos targetPos = worldPosition.relative(direction);
            Direction targetSide = direction.getOpposite();
            var craftingMachine = ICraftingMachine.of(level, targetPos, targetSide);
            if (craftingMachine != null && craftingMachine.acceptsPlans()) {
                if (craftingMachine.pushPattern(patternDetails, inputHolder, targetSide)) {
                    return true;
                }
                continue;
            }
            inventoryTargets.add(direction);
        }

        if (!patternDetails.supportsPushInputsToExternalInventory()) {
            return false;
        }

        for (var direction : inventoryTargets) {
            var target = getTarget(direction);
            if (target == null || !acceptsEveryInput(target, inputHolder)) {
                continue;
            }

            pendingDirection = direction;
            patternDetails.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
                long inserted = target.insert(what, amount, Actionable.MODULATE);
                if (inserted < amount) {
                    pendingDispatch.add(new GenericStack(what, amount - inserted));
                }
            });
            if (pendingDispatch.isEmpty()) {
                pendingDirection = null;
            } else {
                saveChanges();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        return !pendingDispatch.isEmpty();
    }

    private static boolean acceptsEveryInput(PatternProviderTarget target, KeyCounter[] inputHolder) {
        for (var input : inputHolder) {
            for (var stack : input) {
                if (target.insert(stack.getKey(), stack.getLongValue(), Actionable.SIMULATE) <= 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private PatternProviderTarget getTarget(Direction direction) {
        if (level == null) {
            return null;
        }
        BlockPos targetPos = worldPosition.relative(direction);
        BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);
        return PatternProviderTarget.get(level, targetPos, targetBlockEntity, direction.getOpposite(), actionSource);
    }

    private void flushPendingDispatch() {
        if (pendingDispatch.isEmpty()) {
            return;
        }
        var target = pendingDirection == null ? null : getTarget(pendingDirection);
        if (target == null) {
            return;
        }

        boolean changed = false;
        for (var iterator = pendingDispatch.listIterator(); iterator.hasNext();) {
            var stack = iterator.next();
            long inserted = target.insert(stack.what(), stack.amount(), Actionable.MODULATE);
            if (inserted >= stack.amount()) {
                iterator.remove();
                changed = true;
            } else if (inserted > 0) {
                iterator.set(new GenericStack(stack.what(), stack.amount() - inserted));
                changed = true;
            }
        }
        if (changed) {
            if (pendingDispatch.isEmpty()) {
                pendingDirection = null;
            }
            saveChanges();
        }
    }

    private void returnItemsToNetwork() {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            returnInventory.drainInto(grid.getStorageService().getInventory(), actionSource);
        }
    }

    private EnumSet<Direction> getTargetDirections() {
        var direction = getBlockState().getValue(PigmeePatternProviderBlock.PUSH_DIRECTION).getDirection();
        return direction == null ? EnumSet.allOf(Direction.class) : EnumSet.of(direction);
    }

    private EnumSet<Direction> getActiveTargetDirections() {
        var directions = getTargetDirections();
        var node = getMainNode().getNode();
        if (node == null) {
            return directions;
        }

        for (var entry : node.getInWorldConnections().entrySet()) {
            var otherNode = entry.getValue().getOtherSide(node);
            var owner = otherNode.getOwner();
            if (owner instanceof PatternProviderLogicHost
                    || owner instanceof PigmeePatternProviderBlockEntity
                    || (owner instanceof InterfaceLogicHost && otherNode.getGrid().equals(getMainNode().getGrid()))) {
                directions.remove(entry.getKey());
            }
        }
        return directions;
    }

    private void updatePatterns() {
        patterns.clear();
        if (level != null) {
            for (var stack : patternInventory) {
                var details = PatternDetailsHelper.decodePattern(stack, level);
                if (details != null) {
                    patterns.add(details);
                }
            }
        }
        ICraftingProvider.requestUpdate(getMainNode());
    }

    private void onReturnInventoryChanged() {
        saveChanges();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        saveChanges();
        if (inventory == patternInventory) {
            updatePatterns();
        }
    }

    @Override
    public boolean isClientSide() {
        return level == null || level.isClientSide();
    }

    @Override
    public void onReady() {
        super.onReady();
        updatePatterns();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        patternInventory.writeToNBT(tag, TAG_PATTERNS, registries);
        returnInventory.writeToChildTag(tag, TAG_RETURN_INVENTORY, registries);

        var pendingTag = new ListTag();
        for (var stack : pendingDispatch) {
            pendingTag.add(GenericStack.writeTag(registries, stack));
        }
        if (pendingTag.isEmpty()) {
            tag.remove(TAG_PENDING_DISPATCH);
            tag.remove(TAG_PENDING_DIRECTION);
        } else {
            tag.put(TAG_PENDING_DISPATCH, pendingTag);
            if (pendingDirection != null) {
                tag.putByte(TAG_PENDING_DIRECTION, (byte) pendingDirection.get3DDataValue());
            } else {
                tag.remove(TAG_PENDING_DIRECTION);
            }
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        patternInventory.readFromNBT(tag, TAG_PATTERNS, registries);
        returnInventory.readFromTag(tag.getList(TAG_RETURN_INVENTORY, Tag.TAG_COMPOUND), registries);

        pendingDispatch.clear();
        var pendingTag = tag.getList(TAG_PENDING_DISPATCH, Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingTag.size(); i++) {
            var stack = GenericStack.readTag(registries, pendingTag.getCompound(i));
            if (stack != null && stack.amount() > 0) {
                pendingDispatch.add(stack);
            }
        }
        if (pendingDispatch.isEmpty()) {
            pendingDirection = null;
        } else if (tag.contains(TAG_PENDING_DIRECTION, Tag.TAG_BYTE)) {
            pendingDirection = Direction.from3DDataValue(tag.getByte(TAG_PENDING_DIRECTION));
        } else {
            pendingDirection = null;
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var stack : patternInventory) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        returnInventory.addDrops(drops, level, pos);
        for (var stack : pendingDispatch) {
            stack.what().addDrops(stack.amount(), drops, level, pos);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        patternInventory.clear();
        returnInventory.clear();
        pendingDispatch.clear();
        pendingDirection = null;
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.SMART;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        var direction = getBlockState().getValue(PigmeePatternProviderBlock.PUSH_DIRECTION).getDirection();
        return direction == null
                ? EnumSet.allOf(Direction.class)
                : EnumSet.complementOf(EnumSet.of(direction));
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        onGridConnectableSidesChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ae2lt.pigmee_pattern_provider");
    }

    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(PigmeePatternProviderMenu.TYPE, player, locator);
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModBlocks.PIGMEE_PATTERN_PROVIDER.get().asItem();
    }
}
