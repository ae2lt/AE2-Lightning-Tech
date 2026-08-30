package com.moakiee.ae2lt.logic.tianshu.terminal;

import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
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
 * Legacy AE2-only wireless host, retained for source and binary compatibility.
 *
 * <p>This branch does not register an AE2-only wireless item or menu. Its registered wireless
 * terminal uses {@code com.moakiee.ae2lt.integration.ae2wtlib.TianshuWTMenuHost} and requires
 * AE2WTLib. This class is not a runtime fallback and must not be wired into that registration.
 *
 * <p>AE2's pattern logic and Tianshu's authoring state are stored in the terminal item's NBT
 * (under {@value #TAG_PATTERN_LOGIC}), so closing and reopening the wireless item keeps the
 * same draft as the part version. Its legacy nested view-cell storage is intentionally distinct
 * from AE2WTLib's root view-cell storage.</p>
 *
 * @deprecated No registered terminal uses this host. Retained for existing external callers.
 */
@Deprecated(forRemoval = false)
public class TianshuWirelessPatternEncodingTermMenuHost extends WirelessTerminalMenuHost
        implements TianshuPatternTerminalHost, IPatternTerminalLogicHost, IViewCellStorage,
        InternalInventoryHost {
    private static final String TAG_PATTERN_LOGIC = "patternEncodingLogic";
    private static final String TAG_VIEW_CELLS = "viewcells";

    private final PatternEncodingLogic logic = new PatternEncodingLogic(this);
    private final AppEngInternalInventory viewCells = new AppEngInternalInventory(this, 5);
    private final TianshuTerminalState terminalState = new TianshuTerminalState();

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
            terminalState.read(data, TianshuTerminalState.NbtFormat.WIRELESS);
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
        terminalState.write(data, TianshuTerminalState.NbtFormat.WIRELESS);
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
        return terminalState.getEncodingMode();
    }

    @Override
    public void setTianshuEncodingMode(TianshuEncodingMode mode) {
        if (terminalState.setEncodingMode(mode)) {
            markForSave();
        }
    }

    @Override
    public boolean isMaintainableView() {
        return terminalState.isMaintainableView();
    }

    @Override
    public void setMaintainableView(boolean enabled) {
        if (terminalState.setMaintainableView(enabled)) {
            markForSave();
        }
    }

    @Override
    public ClosedLoopTerminalDraft getClosedLoopTerminalDraft() {
        return terminalState.getClosedLoopDraft();
    }

    @Override
    public void setClosedLoopTerminalDraft(@Nullable ClosedLoopTerminalDraft draft) {
        if (terminalState.setClosedLoopDraft(draft)) {
            markForSave();
        }
    }

    @Override
    public ProcessingPatternTerminalDraft getProcessingPatternTerminalDraft() {
        return terminalState.getProcessingDraft();
    }

    @Override
    public void setProcessingPatternTerminalDraft(@Nullable ProcessingPatternTerminalDraft draft) {
        if (terminalState.setProcessingDraft(draft)) {
            markForSave();
        }
    }
}
