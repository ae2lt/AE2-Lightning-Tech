package com.moakiee.ae2lt.menu;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.PigmeePatternProviderBlockEntity;
import com.moakiee.ae2lt.logic.PigmeePatternProviderReturnInventory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class PigmeePatternProviderMenu extends AEBaseMenu {
    public static final MenuType<PigmeePatternProviderMenu> TYPE = MenuTypeBuilder
            .create(PigmeePatternProviderMenu::new, PigmeePatternProviderBlockEntity.class)
            .withMenuTitle(host -> Component.translatable("block.ae2lt.pigmee_pattern_provider"))
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "pigmee_pattern_provider"));

    private static final int PATTERN_X = 62;
    private static final int PATTERN_Y = 49;
    private static final int RETURN_X = 8;
    private static final int RETURN_Y = 113;
    private static final int PLAYER_X = 8;
    private static final int PLAYER_Y = 147;
    private static final int HOTBAR_Y = 205;
    private static final int SLOT_SPACING = 18;

    public PigmeePatternProviderMenu(
            int id,
            Inventory playerInventory,
            PigmeePatternProviderBlockEntity host) {
        super(TYPE, id, playerInventory, host);

        var patternInventory = host.getPatternInventory();
        for (int slot = 0; slot < PigmeePatternProviderBlockEntity.PATTERN_SLOT_COUNT; slot++) {
            var patternSlot = new PatternSlot(patternInventory, slot);
            patternSlot.x = PATTERN_X + slot * SLOT_SPACING;
            patternSlot.y = PATTERN_Y;
            addSlot(patternSlot, SlotSemantics.ENCODED_PATTERN);
        }

        var returnMenuInventory = host.getReturnInventory().createMenuWrapper();
        for (int slot = 0; slot < PigmeePatternProviderReturnInventory.SLOT_COUNT; slot++) {
            var returnSlot = new AppEngSlot(returnMenuInventory, slot);
            returnSlot.x = RETURN_X + slot * SLOT_SPACING;
            returnSlot.y = RETURN_Y;
            addSlot(returnSlot, SlotSemantics.STORAGE);
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int inventorySlot = column + row * 9 + 9;
                addSlot(
                        new Slot(
                                playerInventory,
                                inventorySlot,
                                PLAYER_X + column * SLOT_SPACING,
                                PLAYER_Y + row * SLOT_SPACING),
                        SlotSemantics.PLAYER_INVENTORY);
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(
                    new Slot(
                            playerInventory,
                            column,
                            PLAYER_X + column * SLOT_SPACING,
                            HOTBAR_Y),
                    SlotSemantics.PLAYER_HOTBAR);
        }
    }

    private static final class PatternSlot extends AppEngSlot {
        private PatternSlot(appeng.api.inventories.InternalInventory inventory, int slot) {
            super(inventory, slot);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return PatternDetailsHelper.isEncodedPattern(stack) && super.mayPlace(stack);
        }
    }
}
