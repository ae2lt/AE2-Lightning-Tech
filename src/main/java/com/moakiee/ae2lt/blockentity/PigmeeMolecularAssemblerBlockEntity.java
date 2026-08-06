package com.moakiee.ae2lt.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.client.render.crafting.AssemblerAnimationStatus;
import appeng.core.AELog;
import appeng.crafting.CraftingEvent;
import appeng.menu.AutoCraftingMenu;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.network.PigmeeAssemblerAnimationPacket;
import com.moakiee.ae2lt.registry.ModBlockEntities;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Independent, fixed-speed crafting machine for Pigmee Tech.
 *
 * <p>This intentionally does not extend AE2's molecular assembler and therefore
 * has no upgrade inventory at all.</p>
 */
public final class PigmeeMolecularAssemblerBlockEntity extends AENetworkedInvBlockEntity
        implements IGridTickable, ICraftingMachine, IPowerChannelState {
    public static final ResourceLocation INV_MAIN = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "pigmee_molecular_assembler");

    private static final String TAG_AUTOMATIC_PATTERN = "automaticPattern";
    private static final String TAG_PUSH_DIRECTION = "pushDirection";
    private static final int CRAFTING_SLOT_COUNT = 9;
    private static final int OUTPUT_SLOT = 9;
    private static final int PATTERN_SLOT = 10;
    private static final int MAX_PROGRESS = 100;
    private static final int ENERGY_PER_TICK = 10;

    private final CraftingContainer craftingGrid =
            new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
    private final AppEngInternalInventory itemInventory =
            new AppEngInternalInventory(this, CRAFTING_SLOT_COUNT + 1, 1);
    private final AppEngInternalInventory patternInventory =
            new AppEngInternalInventory(this, 1, 1);
    private final InternalInventory exposedInventory =
            new FilteredInternalInventory(itemInventory, new CraftingGridFilter());
    private final InternalInventory combinedInventory =
            new CombinedInternalInventory(itemInventory, patternInventory);

    private @Nullable IMolecularAssemblerSupportedPattern activePattern;
    private ItemStack savedAutomaticPattern = ItemStack.EMPTY;
    private @Nullable Direction pushDirection;
    private double progress;
    private boolean automaticJob;
    private boolean awake;
    private boolean powered;
    private boolean reboot = true;

    @OnlyIn(Dist.CLIENT)
    private @Nullable AssemblerAnimationStatus animationStatus;

    public PigmeeMolecularAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIGMEE_MOLECULAR_ASSEMBLER.get(), pos, state);
        getMainNode()
                .setIdlePowerUsage(0.0D)
                .addService(IGridTickable.class, this);
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        Component name = hasCustomName()
                ? getCustomName()
                : ModBlocks.PIGMEE_MOLECULAR_ASSEMBLER.get().asItem().getDescription();
        return new PatternContainerGroup(
                AEItemKey.of(ModBlocks.PIGMEE_MOLECULAR_ASSEMBLER.get()),
                name,
                List.of());
    }

    @Override
    public boolean pushPattern(
            IPatternDetails patternDetails,
            KeyCounter[] inputs,
            Direction ejectionDirection) {
        if (activePattern != null
                || !combinedInventory.isEmpty()
                || !(patternDetails instanceof IMolecularAssemblerSupportedPattern supportedPattern)) {
            return false;
        }

        automaticJob = true;
        activePattern = supportedPattern;
        pushDirection = ejectionDirection;
        supportedPattern.fillCraftingGrid(inputs, itemInventory::setItemDirect);

        for (var input : inputs) {
            input.removeZeros();
            if (!input.isEmpty()) {
                clearAutomaticJob();
                itemInventory.clear();
                throw new IllegalStateException(
                        "Pigmee assembler could not place all supplied crafting inputs");
            }
        }

        updateSleepState();
        saveChanges();
        return true;
    }

    @Override
    public boolean acceptsPlans() {
        return patternInventory.isEmpty();
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (INV_MAIN.equals(id)) {
            return combinedInventory;
        }
        return super.getSubInventory(id);
    }

    @Override
    public InternalInventory getInternalInventory() {
        return combinedInventory;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(Direction side) {
        return exposedInventory;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        if (inventory == itemInventory || inventory == patternInventory) {
            recalculatePattern();
            saveChanges();
        }
    }

    public int getCraftingProgress() {
        return (int) progress;
    }

    public @Nullable IMolecularAssemblerSupportedPattern getCurrentPattern() {
        if (isClientSide()) {
            var decoded = PatternDetailsHelper.decodePattern(
                    patternInventory.getStackInSlot(0), level);
            return decoded instanceof IMolecularAssemblerSupportedPattern supported
                    ? supported
                    : null;
        }
        return activePattern;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        recalculatePattern();
        updateSleepState();
        return new TickingRequest(1, 1, !awake);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!itemInventory.getStackInSlot(OUTPUT_SLOT).isEmpty()) {
            pushOutput(itemInventory.getStackInSlot(OUTPUT_SLOT));
            ejectInvalidInput();
            progress = 0;
            updateSleepState();
            return awake ? TickRateModulation.IDLE : TickRateModulation.SLEEP;
        }

        if (activePattern == null || !awake) {
            updateSleepState();
            return TickRateModulation.SLEEP;
        }

        if (reboot) {
            ticksSinceLastCall = 1;
            reboot = false;
        }

        progress += usePower(ticksSinceLastCall);
        if (progress < MAX_PROGRESS) {
            return TickRateModulation.FASTER;
        }

        populateTransientCraftingGrid();
        var positionedInput = craftingGrid.asPositionedCraftInput();
        var compactInput = positionedInput.input();
        var output = activePattern.assemble(compactInput, level);
        progress = 0;

        if (output.isEmpty()) {
            updateSleepState();
            return TickRateModulation.IDLE;
        }

        output.onCraftedBySystem(level);
        CraftingEvent.fireAutoCraftingEvent(level, activePattern, output, craftingGrid);
        var remainders = activePattern.getRemainingItems(compactInput);

        pushOutput(output.copy());

        int inputLeft = positionedInput.left();
        int inputTop = positionedInput.top();
        for (int y = 0; y < craftingGrid.getHeight(); y++) {
            for (int x = 0; x < craftingGrid.getWidth(); x++) {
                if (y < inputTop || x < inputLeft) {
                    itemInventory.setItemDirect(x + y * craftingGrid.getWidth(), ItemStack.EMPTY);
                }
            }
        }
        for (int y = 0; y < compactInput.height(); y++) {
            for (int x = 0; x < compactInput.width(); x++) {
                int inventorySlot =
                        x + inputLeft + (y + inputTop) * craftingGrid.getWidth();
                itemInventory.setItemDirect(
                        inventorySlot,
                        remainders.get(x + y * compactInput.width()));
            }
        }

        if (patternInventory.isEmpty()) {
            clearAutomaticJob();
        }
        ejectInvalidInput();
        sendCraftingAnimation(node, output);
        saveChanges();
        updateSleepState();
        return awake ? TickRateModulation.IDLE : TickRateModulation.SLEEP;
    }

    private int usePower(int ticksSinceLastCall) {
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        return (int) grid.getEnergyService().extractAEPower(
                ticksSinceLastCall * ENERGY_PER_TICK,
                Actionable.MODULATE,
                PowerMultiplier.CONFIG);
    }

    private void populateTransientCraftingGrid() {
        for (int slot = 0; slot < CRAFTING_SLOT_COUNT; slot++) {
            craftingGrid.setItem(slot, itemInventory.getStackInSlot(slot));
        }
    }

    private boolean hasMaterials() {
        if (activePattern == null) {
            return false;
        }
        populateTransientCraftingGrid();
        return !activePattern.assemble(craftingGrid.asCraftInput(), level).isEmpty();
    }

    private void pushOutput(ItemStack output) {
        if (pushDirection == null) {
            for (var direction : Direction.values()) {
                output = pushTo(output, direction);
            }
        } else {
            output = pushTo(output, pushDirection);
        }
        itemInventory.setItemDirect(OUTPUT_SLOT, output);
    }

    private ItemStack pushTo(ItemStack output, Direction direction) {
        if (output.isEmpty() || level == null) {
            return output;
        }
        var target = InternalInventory.wrapExternal(
                level,
                worldPosition.relative(direction),
                direction.getOpposite());
        if (target == null) {
            return output;
        }
        var remainder = target.addItems(output);
        if (remainder.getCount() != output.getCount()) {
            saveChanges();
        }
        return remainder;
    }

    private void ejectInvalidInput() {
        if (!itemInventory.getStackInSlot(OUTPUT_SLOT).isEmpty()) {
            return;
        }
        for (int slot = 0; slot < CRAFTING_SLOT_COUNT; slot++) {
            var stack = itemInventory.getStackInSlot(slot);
            if (!stack.isEmpty()
                    && (activePattern == null
                    || !activePattern.isItemValid(slot, AEItemKey.of(stack), level))) {
                itemInventory.setItemDirect(OUTPUT_SLOT, stack);
                itemInventory.setItemDirect(slot, ItemStack.EMPTY);
                saveChanges();
                return;
            }
        }
    }

    private void recalculatePattern() {
        reboot = true;
        if (automaticJob) {
            restoreAutomaticPatternIfNeeded();
            updateSleepState();
            return;
        }

        var patternStack = patternInventory.getStackInSlot(0);
        if (!patternStack.isEmpty()
                && ItemStack.isSameItemSameComponents(patternStack, savedAutomaticPattern)) {
            updateSleepState();
            return;
        }

        progress = 0;
        activePattern = null;
        savedAutomaticPattern = ItemStack.EMPTY;
        pushDirection = null;

        var decoded = PatternDetailsHelper.decodePattern(patternStack, level);
        if (decoded instanceof IMolecularAssemblerSupportedPattern supportedPattern) {
            activePattern = supportedPattern;
            savedAutomaticPattern = patternStack.copy();
        }
        updateSleepState();
    }

    private void restoreAutomaticPatternIfNeeded() {
        if (activePattern != null || savedAutomaticPattern.isEmpty() || level == null) {
            return;
        }
        var decoded = PatternDetailsHelper.decodePattern(savedAutomaticPattern, level);
        if (decoded instanceof IMolecularAssemblerSupportedPattern supportedPattern) {
            activePattern = supportedPattern;
        } else {
            AELog.warn("Unable to restore Pigmee assembler automatic crafting pattern");
            clearAutomaticJob();
        }
        savedAutomaticPattern = ItemStack.EMPTY;
    }

    private void clearAutomaticJob() {
        automaticJob = false;
        activePattern = null;
        savedAutomaticPattern = ItemStack.EMPTY;
        pushDirection = null;
    }

    private void updateSleepState() {
        boolean wasAwake = awake;
        awake = activePattern != null && hasMaterials()
                || !itemInventory.getStackInSlot(OUTPUT_SLOT).isEmpty();
        if (wasAwake == awake) {
            return;
        }
        getMainNode().ifPresent((grid, node) -> {
            if (awake) {
                grid.getTickManager().wakeDevice(node);
            } else {
                grid.getTickManager().sleepDevice(node);
            }
        });
    }

    private void sendCraftingAnimation(IGridNode node, ItemStack output) {
        PacketDistributor.sendToPlayersNear(
                node.getLevel(),
                null,
                worldPosition.getX(),
                worldPosition.getY(),
                worldPosition.getZ(),
                32.0D,
                new PigmeeAssemblerAnimationPacket(
                        worldPosition,
                        (byte) ENERGY_PER_TICK,
                        output.copy()));
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.COVERED;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason == IGridNodeListener.State.GRID_BOOT) {
            return;
        }
        boolean newPowered = false;
        var grid = getMainNode().getGrid();
        if (grid != null) {
            newPowered = getMainNode().isPowered()
                    && grid.getEnergyService().extractAEPower(
                            1.0D,
                            Actionable.SIMULATE,
                            PowerMultiplier.CONFIG) > 0.0001D;
        }
        if (newPowered != powered) {
            powered = newPowered;
            markForUpdate();
        }
    }

    @Override
    public boolean isPowered() {
        return powered;
    }

    @Override
    public boolean isActive() {
        return powered;
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean newPowered = data.readBoolean();
        if (newPowered != powered) {
            powered = newPowered;
            changed = true;
        }
        return changed;
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(powered);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        if (automaticJob) {
            var patternStack = activePattern != null
                    ? activePattern.getDefinition().toStack()
                    : savedAutomaticPattern;
            if (!patternStack.isEmpty()) {
                data.put(TAG_AUTOMATIC_PATTERN, patternStack.save(registries));
                if (pushDirection != null) {
                    data.putInt(TAG_PUSH_DIRECTION, pushDirection.get3DDataValue());
                }
            }
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        clearAutomaticJob();
        if (data.contains(TAG_AUTOMATIC_PATTERN)) {
            var patternStack = ItemStack.parseOptional(
                    registries,
                    data.getCompound(TAG_AUTOMATIC_PATTERN));
            if (!patternStack.isEmpty()) {
                automaticJob = true;
                savedAutomaticPattern = patternStack;
                if (data.contains(TAG_PUSH_DIRECTION)) {
                    pushDirection = Direction.from3DDataValue(data.getInt(TAG_PUSH_DIRECTION));
                }
            }
        }
        recalculatePattern();
    }

    @OnlyIn(Dist.CLIENT)
    public @Nullable AssemblerAnimationStatus getAnimationStatus() {
        return animationStatus;
    }

    @OnlyIn(Dist.CLIENT)
    public void setAnimationStatus(@Nullable AssemblerAnimationStatus animationStatus) {
        this.animationStatus = animationStatus;
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModBlocks.PIGMEE_MOLECULAR_ASSEMBLER.get().asItem();
    }

    private final class CraftingGridFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(InternalInventory inventory, int slot, int amount) {
            return slot == OUTPUT_SLOT;
        }

        @Override
        public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
            return slot < CRAFTING_SLOT_COUNT
                    && !patternInventory.isEmpty()
                    && activePattern != null
                    && activePattern.isItemValid(slot, AEItemKey.of(stack), level);
        }
    }
}
