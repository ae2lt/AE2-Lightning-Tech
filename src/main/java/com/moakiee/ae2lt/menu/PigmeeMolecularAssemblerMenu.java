package com.moakiee.ae2lt.menu;

import appeng.api.stacks.AEItemKey;
import appeng.client.Point;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.interfaces.IProgressProvider;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.IOptionalSlot;
import appeng.menu.slot.OutputSlot;
import appeng.menu.slot.RestrictedInputSlot;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.PigmeeMolecularAssemblerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class PigmeeMolecularAssemblerMenu
        extends AEBaseMenu
        implements IProgressProvider {
    public static final MenuType<PigmeeMolecularAssemblerMenu> TYPE = MenuTypeBuilder
            .create(PigmeeMolecularAssemblerMenu::new, PigmeeMolecularAssemblerBlockEntity.class)
            .withMenuTitle(host -> Component.translatable(
                    "block.ae2lt.pigmee_molecular_assembler"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "pigmee_molecular_assembler"));

    private static final int MAX_CRAFT_PROGRESS = 100;

    private final PigmeeMolecularAssemblerBlockEntity molecularAssembler;
    private Slot encodedPatternSlot;

    @GuiSync(4)
    public int craftProgress;

    public PigmeeMolecularAssemblerMenu(
            int id,
            Inventory playerInventory,
            PigmeeMolecularAssemblerBlockEntity blockEntity) {
        super(TYPE, id, playerInventory, blockEntity);
        this.molecularAssembler = blockEntity;
        setupMachineSlots();
        createPlayerInventorySlots(playerInventory);
    }

    private void setupMachineSlots() {
        var inventory = molecularAssembler.getSubInventory(
                PigmeeMolecularAssemblerBlockEntity.INV_MAIN);

        for (int slot = 0; slot < 9; slot++) {
            addSlot(
                    new CraftingGridSlot(inventory, slot),
                    SlotSemantics.MACHINE_CRAFTING_GRID);
        }

        var patternSlot = new RestrictedInputSlot(
                RestrictedInputSlot.PlacableItemType.MOLECULAR_ASSEMBLER_PATTERN,
                inventory,
                10);
        patternSlot.setIcon(null);
        encodedPatternSlot = addSlot(patternSlot, SlotSemantics.ENCODED_PATTERN);
        addSlot(new OutputSlot(inventory, 9, null), SlotSemantics.MACHINE_OUTPUT);
    }

    public boolean isValidItemForSlot(int slotIndex, ItemStack stack) {
        var pattern = molecularAssembler.getCurrentPattern();
        return pattern != null
                && pattern.isItemValid(
                        slotIndex,
                        AEItemKey.of(stack),
                        molecularAssembler.getLevel());
    }

    @Override
    public void broadcastChanges() {
        craftProgress = molecularAssembler.getCraftingProgress();
        super.broadcastChanges();
    }

    @Override
    public int getCurrentProgress() {
        return craftProgress;
    }

    @Override
    public int getMaxProgress() {
        return MAX_CRAFT_PROGRESS;
    }

    @Override
    public void onSlotChange(Slot changedSlot) {
        if (changedSlot == encodedPatternSlot) {
            for (var slot : slots) {
                if (slot != changedSlot && slot instanceof AppEngSlot appEngSlot) {
                    appEngSlot.resetCachedValidation();
                }
            }
        }
    }

    private final class CraftingGridSlot extends AppEngSlot implements IOptionalSlot {
        private CraftingGridSlot(
                appeng.api.inventories.InternalInventory inventory,
                int slot) {
            super(inventory, slot);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return super.mayPlace(stack)
                    && isValidItemForSlot(getSlotIndex(), stack);
        }

        @Override
        protected boolean getCurrentValidationState() {
            var stack = getItem();
            return stack.isEmpty() || mayPlace(stack);
        }

        @Override
        public boolean isRenderDisabled() {
            return false;
        }

        @Override
        public boolean isSlotEnabled() {
            int slotIndex = getSlotIndex();
            if (!getInventory().getStackInSlot(slotIndex).isEmpty()) {
                return true;
            }
            var pattern = molecularAssembler.getCurrentPattern();
            return slotIndex >= 0
                    && slotIndex < 9
                    && pattern != null
                    && pattern.isSlotEnabled(slotIndex);
        }

        @Override
        public Point getBackgroundPos() {
            return new Point(x - 1, y - 1);
        }
    }
}
