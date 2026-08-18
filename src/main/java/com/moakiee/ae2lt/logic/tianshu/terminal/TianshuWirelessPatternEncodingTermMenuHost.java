package com.moakiee.ae2lt.logic.tianshu.terminal;

import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.implementations.blockentities.IViewCellStorage;
import appeng.api.inventories.InternalInventory;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.ISubMenu;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

/**
 * Wireless host for the Tianshu terminal.
 *
 * <p>Built on AE2's own {@link WirelessTerminalMenuHost} so the host works
 * without ae2wtlib. When ae2wtlib is loaded, the registered item dispatches to
 * {@code com.moakiee.ae2lt.integration.ae2wtlib.TianshuWTMenuHost} instead, which
 * extends ae2wtlib's {@code WTMenuHost} and carries the same state logic for the
 * frequency-card remote link.
 *
 * <p>AE2's pattern logic and Tianshu's authoring state are stored in the terminal item's NBT
 * (under {@value #TAG_PATTERN_LOGIC}), so closing and reopening the wireless item keeps the
 * same draft as the part version.</p>
 */
public class TianshuWirelessPatternEncodingTermMenuHost extends WirelessTerminalMenuHost
        implements TianshuPatternTerminalHost, IPatternTerminalLogicHost, IViewCellStorage,
        InternalInventoryHost {
    private static final String TAG_PATTERN_LOGIC = "patternEncodingLogic";
    private static final String TAG_TIANSHU_MODE = "tianshuMode";
    private static final String TAG_MAINTAINABLE_VIEW = "tianshuMaintainableView";
    private static final String TAG_CLOSED_LOOP_DRAFT = "tianshuClosedLoopDraft";
    private static final String TAG_PROCESSING_DRAFT = "tianshuProcessingDraft";
    private static final String TAG_VIEW_CELLS = "viewcells";

    private final PatternEncodingLogic logic = new PatternEncodingLogic(this);
    private final AppEngInternalInventory viewCells = new AppEngInternalInventory(this, 5);
    private TianshuEncodingMode tianshuMode = TianshuEncodingMode.CRAFTING;
    private boolean maintainableView;
    @Nullable
    private ClosedLoopTerminalDraft closedLoopDraft;
    @Nullable
    private ProcessingPatternTerminalDraft processingDraft;

    public TianshuWirelessPatternEncodingTermMenuHost(
            Player player,
            int inventorySlot,
            ItemStack stack,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, inventorySlot, stack, returnToMainMenu);

        CompoundTag data = getItemStack().getTagElement(TAG_PATTERN_LOGIC);
        if (data != null) {
            logic.readFromNBT(data);
            viewCells.readFromNBT(data, TAG_VIEW_CELLS);
            readTianshuState(data);
        }

        // Tianshu pulls blank patterns from ME storage and stages only the pattern being encoded.
        // Keep the inherited physical blank-pattern slot unavailable, just like the wired part.
        if (logic.getBlankPatternInv() instanceof AppEngInternalInventory inventory) {
            inventory.setMaxStackSize(0, 0);
        }
    }

    @Override
    public PatternEncodingLogic getLogic() {
        return logic;
    }

    @Override
    public Level getLevel() {
        return getPlayer().level();
    }

    @Override
    public void markForSave() {
        CompoundTag data = getItemStack().getOrCreateTagElement(TAG_PATTERN_LOGIC);
        logic.writeToNBT(data);
        viewCells.writeToNBT(data, TAG_VIEW_CELLS);
        data.putString(TAG_TIANSHU_MODE, tianshuMode.name());
        data.putBoolean(TAG_MAINTAINABLE_VIEW, maintainableView);
        if (closedLoopDraft != null) {
            data.put(TAG_CLOSED_LOOP_DRAFT, closedLoopDraft.write());
        } else {
            data.remove(TAG_CLOSED_LOOP_DRAFT);
        }
        if (processingDraft != null) {
            data.put(TAG_PROCESSING_DRAFT, processingDraft.write());
        } else {
            data.remove(TAG_PROCESSING_DRAFT);
        }
    }

    @Override
    public void saveChanges() {
        markForSave();
    }

    @Override
    public void onChangeInventory(InternalInventory inventory, int slot) {
        markForSave();
    }

    @Override
    public AppEngInternalInventory getViewCellStorage() {
        return viewCells;
    }

    @Override
    public TianshuEncodingMode getTianshuEncodingMode() {
        return tianshuMode;
    }

    @Override
    public void setTianshuEncodingMode(TianshuEncodingMode mode) {
        if (mode != null && mode != tianshuMode) {
            tianshuMode = mode;
            markForSave();
        }
    }

    @Override
    public boolean isMaintainableView() {
        return maintainableView;
    }

    @Override
    public void setMaintainableView(boolean enabled) {
        if (maintainableView != enabled) {
            maintainableView = enabled;
            markForSave();
        }
    }

    @Override
    public ClosedLoopTerminalDraft getClosedLoopTerminalDraft() {
        return closedLoopDraft;
    }

    @Override
    public void setClosedLoopTerminalDraft(@Nullable ClosedLoopTerminalDraft draft) {
        if (ClosedLoopTerminalDraft.sameState(closedLoopDraft, draft)) {
            return;
        }
        closedLoopDraft = draft;
        markForSave();
    }

    @Override
    public ProcessingPatternTerminalDraft getProcessingPatternTerminalDraft() {
        return processingDraft;
    }

    @Override
    public void setProcessingPatternTerminalDraft(@Nullable ProcessingPatternTerminalDraft draft) {
        if (ProcessingPatternTerminalDraft.sameState(processingDraft, draft)) {
            return;
        }
        processingDraft = draft;
        markForSave();
    }

    private void readTianshuState(CompoundTag data) {
        try {
            tianshuMode = TianshuEncodingMode.valueOf(data.getString(TAG_TIANSHU_MODE));
        } catch (IllegalArgumentException ignored) {
            tianshuMode = TianshuEncodingMode.CRAFTING;
        }
        maintainableView = data.getBoolean(TAG_MAINTAINABLE_VIEW);
        closedLoopDraft = data.contains(TAG_CLOSED_LOOP_DRAFT, Tag.TAG_COMPOUND)
                ? ClosedLoopTerminalDraft.read(data.getCompound(TAG_CLOSED_LOOP_DRAFT))
                : null;
        processingDraft = data.contains(TAG_PROCESSING_DRAFT, Tag.TAG_COMPOUND)
                ? ProcessingPatternTerminalDraft.read(data.getCompound(TAG_PROCESSING_DRAFT))
                : null;
    }
}
