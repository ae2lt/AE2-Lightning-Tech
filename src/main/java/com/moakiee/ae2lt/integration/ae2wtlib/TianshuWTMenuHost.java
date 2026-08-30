package com.moakiee.ae2lt.integration.ae2wtlib;

import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
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
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalState;

/**
 * ae2wtlib menu-host variant of the wireless Tianshu terminal.
 *
 * <p>Extends ae2wtlib's {@link WTMenuHost} so the frequency-card remote link
 * (redirected by {@code WTMenuHostMixin}) works. Only loaded when ae2wtlib is
 * present at runtime; all callers guard with an ae2wtlib presence check, so
 * this class never hits the JVM class loader without ae2wtlib on the
 * classpath. Tianshu authoring state is shared with the wired part; native
 * view-cell and singularity inventories remain owned by {@code WTMenuHost}.</p>
 */
public final class TianshuWTMenuHost extends WTMenuHost
        implements TianshuPatternTerminalHost, IPatternTerminalLogicHost, IViewCellStorage {
    private static final String TAG_PATTERN_LOGIC = "patternEncodingLogic";

    private final PatternEncodingLogic logic = new PatternEncodingLogic(this);
    private final TianshuTerminalState terminalState = new TianshuTerminalState();

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
            terminalState.read(data, TianshuTerminalState.NbtFormat.WIRELESS);
        }
    }

    @Override
    public void saveChanges() {
        super.saveChanges();
        CompoundTag data = getItemStack().getOrCreateTagElement(TAG_PATTERN_LOGIC);
        logic.writeToNBT(data);
        terminalState.write(data, TianshuTerminalState.NbtFormat.WIRELESS);
    }

    @Override
    public void markForSave() {
        saveChanges();
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
