package com.moakiee.ae2lt.logic.tianshu.terminal;

import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import appeng.api.implementations.blockentities.IViewCellStorage;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.util.inv.AppEngInternalInventory;

import de.mari_023.ae2wtlib.api.AE2wtlibComponents;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;

/**
 * Wireless host for the Tianshu terminal.
 *
 * <p>AE2's pattern logic and Tianshu's authoring state are stored in the terminal's
 * {@code PATTERN_ENCODING_LOGIC} component, so closing and reopening the wireless item keeps the
 * same draft as the part version.</p>
 */
public final class TianshuWirelessPatternEncodingTermMenuHost extends WTMenuHost
        implements TianshuPatternTerminalHost, IPatternTerminalLogicHost, IViewCellStorage {
    private static final String TAG_TIANSHU_MODE = "tianshuMode";
    private static final String TAG_CLOSED_LOOP_DRAFT = "tianshuClosedLoopDraft";
    private static final String TAG_PROCESSING_DRAFT = "tianshuProcessingDraft";

    private final PatternEncodingLogic logic = new PatternEncodingLogic(this);
    private TianshuEncodingMode tianshuMode = TianshuEncodingMode.CRAFTING;
    @Nullable
    private ClosedLoopTerminalDraft closedLoopDraft;
    @Nullable
    private ProcessingPatternTerminalDraft processingDraft;

    public TianshuWirelessPatternEncodingTermMenuHost(
            ItemWT item,
            Player player,
            ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);

        CompoundTag data = getItemStack().getOrDefault(
                AE2wtlibComponents.PATTERN_ENCODING_LOGIC, new CompoundTag());
        logic.readFromNBT(data, player.registryAccess());
        readTianshuState(data, player.registryAccess());

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
        CompoundTag data = getItemStack().getOrDefault(
                AE2wtlibComponents.PATTERN_ENCODING_LOGIC, new CompoundTag());
        HolderLookup.Provider registries = getPlayer().registryAccess();
        logic.writeToNBT(data, registries);
        data.putString(TAG_TIANSHU_MODE, tianshuMode.name());
        if (closedLoopDraft != null) {
            data.put(TAG_CLOSED_LOOP_DRAFT, closedLoopDraft.write(registries));
        } else {
            data.remove(TAG_CLOSED_LOOP_DRAFT);
        }
        if (processingDraft != null) {
            data.put(TAG_PROCESSING_DRAFT, processingDraft.write(registries));
        } else {
            data.remove(TAG_PROCESSING_DRAFT);
        }
        getItemStack().set(AE2wtlibComponents.PATTERN_ENCODING_LOGIC, data);
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

    private void readTianshuState(CompoundTag data, HolderLookup.Provider registries) {
        try {
            tianshuMode = TianshuEncodingMode.valueOf(data.getString(TAG_TIANSHU_MODE));
        } catch (IllegalArgumentException ignored) {
            tianshuMode = TianshuEncodingMode.CRAFTING;
        }
        closedLoopDraft = data.contains(TAG_CLOSED_LOOP_DRAFT, Tag.TAG_COMPOUND)
                ? ClosedLoopTerminalDraft.read(data.getCompound(TAG_CLOSED_LOOP_DRAFT), registries)
                : null;
        processingDraft = data.contains(TAG_PROCESSING_DRAFT, Tag.TAG_COMPOUND)
                ? ProcessingPatternTerminalDraft.read(data.getCompound(TAG_PROCESSING_DRAFT), registries)
                : null;
    }
}
