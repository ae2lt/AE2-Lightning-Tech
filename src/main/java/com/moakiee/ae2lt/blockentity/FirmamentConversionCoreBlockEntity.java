package com.moakiee.ae2lt.blockentity;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.machine.firmament.FirmamentConversionAutomationInventory;
import com.moakiee.ae2lt.machine.firmament.FirmamentConversionInventory;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionLockedRecipe;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionRecipeCandidate;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionRecipeService;
import com.moakiee.ae2lt.registry.ModBlockEntities;

public class FirmamentConversionCoreBlockEntity extends BlockEntity {
    private static final ResourceLocation FIRMAMENT_STARSHIP_ID =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "firmament_starship");
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_LOCKED_RECIPE = "LockedRecipe";
    private static final String TAG_PROGRESS = "Progress";

    private final FirmamentConversionInventory inventory =
            new FirmamentConversionInventory(this::onInventoryChanged);
    private final FirmamentConversionAutomationInventory automationInventory =
            new FirmamentConversionAutomationInventory(inventory);
    private FirmamentConversionLockedRecipe lockedRecipe;
    private int progress;
    // Structure membership is fixed for a placed block; cache after first lookup.
    private Boolean insideStarship;

    public FirmamentConversionCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FIRMAMENT_CONVERSION_CORE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FirmamentConversionCoreBlockEntity be) {
        if (!level.isClientSide()) {
            be.tickServer();
        }
    }

    public FirmamentConversionInventory getInventory() {
        return inventory;
    }

    public IItemHandlerModifiable getAutomationInventory() {
        return automationInventory;
    }

    public int getProgress() {
        return progress;
    }

    public int getProcessTime() {
        return lockedRecipe != null ? lockedRecipe.processTime() : 0;
    }

    public boolean insertHeldItem(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            return false;
        }

        ItemStack attempted = held.copy();
        ItemStack remainder = automationInventory.insertItem(attempted, false);
        int inserted = held.getCount() - remainder.getCount();
        if (inserted <= 0) {
            return false;
        }

        if (!player.getAbilities().instabuild) {
            held.shrink(inserted);
        }
        setChanged();
        return true;
    }

    public boolean extractToPlayer(Player player) {
        ItemStack extracted = ItemStack.EMPTY;
        for (int slot = FirmamentConversionInventory.SLOT_OUTPUT_0;
             slot <= FirmamentConversionInventory.SLOT_OUTPUT_3;
             slot++) {
            extracted = inventory.extractItem(slot, FirmamentConversionInventory.SLOT_LIMIT, false);
            if (!extracted.isEmpty()) {
                break;
            }
        }
        if (extracted.isEmpty()) {
            for (int slot = FirmamentConversionInventory.SLOT_INPUT_0;
                 slot <= FirmamentConversionInventory.SLOT_INPUT_2;
                 slot++) {
                extracted = inventory.extractItem(slot, FirmamentConversionInventory.SLOT_LIMIT, false);
                if (!extracted.isEmpty()) {
                    break;
                }
            }
        }
        if (extracted.isEmpty()) {
            return false;
        }

        ItemStack remainder = extracted.copy();
        player.addItem(remainder);
        if (!remainder.isEmpty() && level != null) {
            Block.popResource(level, worldPosition, remainder);
        }
        setChanged();
        return true;
    }

    public void addDrops(List<ItemStack> drops) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack.copy());
            }
        }
        inventory.clear();
    }

    private void tickServer() {
        if (!canProcessHere()) {
            if (lockedRecipe != null || progress != 0) {
                abortProcessing();
            }
            return;
        }

        if (lockedRecipe == null) {
            lockCurrentRecipe();
            return;
        }

        Optional<FirmamentConversionRecipeCandidate> candidate =
                FirmamentConversionRecipeService.findLockedRecipeMatch(level, inventory, lockedRecipe);
        if (candidate.isEmpty()) {
            abortProcessing();
            return;
        }

        progress++;
        if (progress >= lockedRecipe.processTime()) {
            if (!completeLockedRecipe(lockedRecipe, candidate.get())) {
                abortProcessing();
            }
        } else {
            setChanged();
        }
    }

    private Optional<FirmamentConversionLockedRecipe> lockCurrentRecipe() {
        Optional<FirmamentConversionRecipeCandidate> candidate =
                FirmamentConversionRecipeService.findFirstProcessable(level, inventory);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        lockedRecipe = FirmamentConversionLockedRecipe.fromCandidate(candidate.get());
        progress = 0;
        setChanged();
        return Optional.of(lockedRecipe);
    }

    private boolean canProcessHere() {
        if (insideStarship != null) {
            return insideStarship;
        }
        insideStarship = computeInsideStarship();
        return insideStarship;
    }

    private boolean computeInsideStarship() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        Structure structure = serverLevel.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .get(FIRMAMENT_STARSHIP_ID);
        if (structure == null) {
            return false;
        }

        return serverLevel.structureManager()
                .getStructureWithPieceAt(worldPosition, structure)
                .isValid();
    }

    public boolean completeLockedRecipe(
            FirmamentConversionLockedRecipe lockedRecipe,
            FirmamentConversionRecipeCandidate candidate) {
        List<ItemStack> results = lockedRecipe.results();
        if (!inventory.canAcceptRecipeOutputs(results)) {
            return false;
        }

        ItemStack[] extractedInputs = new ItemStack[FirmamentConversionInventory.SLOT_INPUT_2 + 1];
        for (int slot = FirmamentConversionInventory.SLOT_INPUT_0;
             slot <= FirmamentConversionInventory.SLOT_INPUT_2;
             slot++) {
            int toConsume = candidate.match().getConsumptionForSlot(slot);
            if (toConsume <= 0) {
                continue;
            }
            ItemStack extracted = inventory.extractItem(slot, toConsume, false);
            if (extracted.getCount() != toConsume) {
                rollbackInputs(extractedInputs);
                if (!extracted.isEmpty()) {
                    inventory.insertItem(slot, extracted, false);
                }
                return false;
            }
            extractedInputs[slot] = extracted;
        }

        if (!inventory.insertRecipeOutputs(results)) {
            rollbackInputs(extractedInputs);
            return false;
        }

        clearLockedRecipe();
        progress = 0;
        setChanged();
        return true;
    }

    private void rollbackInputs(ItemStack[] extractedInputs) {
        for (int slot = FirmamentConversionInventory.SLOT_INPUT_0;
             slot <= FirmamentConversionInventory.SLOT_INPUT_2;
             slot++) {
            ItemStack extracted = extractedInputs[slot];
            if (extracted != null && !extracted.isEmpty()) {
                inventory.insertItem(slot, extracted, false);
            }
        }
    }

    private void abortProcessing() {
        clearLockedRecipe();
        progress = 0;
        setChanged();
    }

    private void clearLockedRecipe() {
        lockedRecipe = null;
    }

    private void onInventoryChanged() {
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        inventory.saveToTag(tag, TAG_INVENTORY, registries);
        tag.putInt(TAG_PROGRESS, progress);
        if (lockedRecipe != null) {
            tag.put(TAG_LOCKED_RECIPE, lockedRecipe.toTag(registries));
        } else {
            tag.remove(TAG_LOCKED_RECIPE);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.loadFromTag(tag, TAG_INVENTORY, registries);
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        if (tag.contains(TAG_LOCKED_RECIPE, Tag.TAG_COMPOUND)) {
            lockedRecipe = FirmamentConversionLockedRecipe.fromTag(tag.getCompound(TAG_LOCKED_RECIPE), registries);
        } else {
            lockedRecipe = null;
        }
        if (lockedRecipe == null) {
            progress = 0;
        } else {
            progress = Math.min(progress, lockedRecipe.processTime());
        }
    }

    public void clearContent() {
        inventory.clear();
        clearLockedRecipe();
        progress = 0;
    }
}
