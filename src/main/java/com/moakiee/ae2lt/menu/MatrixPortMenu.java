package com.moakiee.ae2lt.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.menu.slot.AppEngSlot;
import appeng.util.inv.AppEngInternalInventory;

import com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity;

/**
 * Pattern inventory exposed by a formed matter warping matrix port.
 *
 * <p>Every physical pattern slot is kept in the menu so normal container synchronization and
 * click handling remain authoritative on the server. The screen only repositions the currently
 * visible rows when searching or scrolling.</p>
 */
public class MatrixPortMenu extends AbstractContainerMenu {
    public static final MenuType<MatrixPortMenu> TYPE =
            IMenuTypeExtension.create(MatrixPortMenu::clientCreate);

    public static final int COLUMNS = 9;
    public static final int VISIBLE_ROWS = 6;
    public static final int MAX_PATTERN_SLOTS = 50 * 72;

    public static final int PATTERN_X = 13;
    public static final int PATTERN_Y = 21;
    public static final int PLAYER_INVENTORY_X = 13;
    public static final int PLAYER_INVENTORY_Y = 140;
    public static final int PLAYER_HOTBAR_Y = 198;

    private static final int SLOT_SPACING = 18;
    private static final int OFFSCREEN_SLOT = -10_000;

    private final BlockPos blockPos;
    @Nullable
    private final MatrixPortBlockEntity host;
    private final List<MatrixPatternSlot> patternSlots = new ArrayList<>();
    private final int patternSlotCount;

    public MatrixPortMenu(int containerId, Inventory playerInventory, MatrixPortBlockEntity host) {
        this(containerId,
                playerInventory,
                host.getBlockPos(),
                host,
                host.getTerminalPatternInventory(),
                host.getTerminalPatternInventory().size());
    }

    private MatrixPortMenu(int containerId,
                           Inventory playerInventory,
                           BlockPos blockPos,
                           @Nullable MatrixPortBlockEntity host,
                           InternalInventory patternInventory,
                           int patternSlotCount) {
        super(TYPE, containerId);
        this.blockPos = blockPos;
        this.host = host;
        this.patternSlotCount = patternSlotCount;

        for (int slot = 0; slot < patternSlotCount; slot++) {
            var patternSlot = new MatrixPatternSlot(patternInventory, slot);
            patternSlot.x = OFFSCREEN_SLOT;
            patternSlot.y = OFFSCREEN_SLOT;
            patternSlots.add(patternSlot);
            addSlot(patternSlot);
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + row * COLUMNS + COLUMNS,
                        PLAYER_INVENTORY_X + column * SLOT_SPACING,
                        PLAYER_INVENTORY_Y + row * SLOT_SPACING));
            }
        }
        for (int column = 0; column < COLUMNS; column++) {
            addSlot(new Slot(
                    playerInventory,
                    column,
                    PLAYER_INVENTORY_X + column * SLOT_SPACING,
                    PLAYER_HOTBAR_Y));
        }
    }

    private static MatrixPortMenu clientCreate(int containerId,
                                               Inventory playerInventory,
                                               FriendlyByteBuf buffer) {
        BlockPos blockPos = buffer.readBlockPos();
        int slotCount = Mth.clamp(buffer.readVarInt(), 0, MAX_PATTERN_SLOTS);
        var clientInventory = new AppEngInternalInventory(slotCount);
        var blockEntity = playerInventory.player.level().getBlockEntity(blockPos);
        var host = blockEntity instanceof MatrixPortBlockEntity port ? port : null;
        return new MatrixPortMenu(
                containerId, playerInventory, blockPos, host, clientInventory, slotCount);
    }

    public static void writeExtraData(FriendlyByteBuf buffer, MatrixPortBlockEntity host) {
        buffer.writeBlockPos(host.getBlockPos());
        buffer.writeVarInt(host.getTerminalPatternInventory().size());
    }

    @Override
    public boolean stillValid(Player player) {
        if (host != null) {
            if (host.isRemoved() || host.getLevel() == null || player.level() != host.getLevel()) {
                return false;
            }
            BlockEntity current = host.getLevel().getBlockEntity(blockPos);
            if (current != host) {
                return false;
            }
        }
        return player.distanceToSqr(
                blockPos.getX() + 0.5D,
                blockPos.getY() + 0.5D,
                blockPos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot source = slots.get(index);
        if (!source.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = source.getItem();
        ItemStack original = sourceStack.copy();
        boolean moved;
        if (index < patternSlotCount) {
            moved = moveItemStackTo(sourceStack, patternSlotCount, slots.size(), true);
        } else {
            if (!PatternDetailsHelper.isEncodedPattern(sourceStack)) {
                return ItemStack.EMPTY;
            }
            moved = moveItemStackTo(sourceStack, 0, patternSlotCount, false);
        }

        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (sourceStack.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        source.onTake(player, sourceStack);
        return original;
    }

    public int getPatternSlotCount() {
        return patternSlotCount;
    }

    public List<MatrixPatternSlot> getPatternSlots() {
        return Collections.unmodifiableList(patternSlots);
    }

    public static final class MatrixPatternSlot extends AppEngSlot {
        private final int patternIndex;

        private MatrixPatternSlot(InternalInventory inventory, int patternIndex) {
            super(inventory, patternIndex);
            this.patternIndex = patternIndex;
        }

        public int getPatternIndex() {
            return patternIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return PatternDetailsHelper.isEncodedPattern(stack) && super.mayPlace(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public ItemStack getDisplayStack() {
            ItemStack pattern = super.getDisplayStack();
            if (!pattern.isEmpty() && pattern.getItem() instanceof EncodedPatternItem<?> encodedPattern) {
                ItemStack output = encodedPattern.getOutput(pattern);
                if (!output.isEmpty()) {
                    return output;
                }
            }
            return pattern;
        }
    }
}
