package com.moakiee.ae2lt.integration.ae2wtlib;

import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.implementations.blockentities.IViewCellStorage;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.menu.ISubMenu;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.util.inv.AppEngInternalInventory;

import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.wut.ItemWUT;

import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopTerminalDraft;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternTerminalDraft;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternTerminalHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessPatternEncodingTermMenuHost;

/**
 * ae2wtlib menu-host variant of the wireless Tianshu terminal.
 *
 * <p>Extends ae2wtlib's {@link WTMenuHost} so the frequency-card remote link
 * (redirected by {@code WTMenuHostMixin}) works. Only loaded when ae2wtlib is
 * present at runtime; all callers guard with an ae2wtlib presence check, so
 * this class never hits the JVM class loader without ae2wtlib on the
 * classpath. State logic mirrors
 * {@link TianshuWirelessPatternEncodingTermMenuHost} (the AE2-only variant)
 * exactly.</p>
 */
public final class TianshuWTMenuHost extends WTMenuHost
        implements TianshuPatternTerminalHost, IPatternTerminalLogicHost, IViewCellStorage {
    private static final String TAG_PATTERN_LOGIC = "patternEncodingLogic";
    private static final String TAG_TIANSHU_MODE = "tianshuMode";
    private static final String TAG_MAINTAINABLE_VIEW = "tianshuMaintainableView";
    private static final String TAG_CLOSED_LOOP_DRAFT = "tianshuClosedLoopDraft";
    private static final String TAG_PROCESSING_DRAFT = "tianshuProcessingDraft";

    private final PatternEncodingLogic logic = new PatternEncodingLogic(this);
    private TianshuEncodingMode tianshuMode = TianshuEncodingMode.CRAFTING;
    private boolean maintainableView;
    @Nullable
    private ClosedLoopTerminalDraft closedLoopDraft;
    @Nullable
    private ProcessingPatternTerminalDraft processingDraft;

    public TianshuWTMenuHost(
            Player player,
            @Nullable Integer inventorySlot,
            ItemStack stack,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(player, inventorySlot, stack, returnToMainMenu);
        readFromNbt();

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
    public boolean isUniversalWirelessTerminal() {
        return getItemStack().getItem() instanceof ItemWUT;
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(Ae2wtlibIntegration.terminal());
    }

    @Override
    protected void readFromNbt() {
        super.readFromNbt();
        CompoundTag data = getItemStack().getTagElement(TAG_PATTERN_LOGIC);
        if (data != null) {
            logic.readFromNBT(data);
            readTianshuState(data);
        }
    }

    @Override
    public void saveChanges() {
        super.saveChanges();
        CompoundTag data = getItemStack().getOrCreateTagElement(TAG_PATTERN_LOGIC);
        logic.writeToNBT(data);
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
    public void markForSave() {
        saveChanges();
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
