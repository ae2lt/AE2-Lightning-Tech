package com.moakiee.ae2lt.logic.tianshu.terminal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Shared authoring state for wired and wireless Tianshu terminals.
 *
 * <p>Setters report changes; the owning host decides when/how to save. Reading does not notify
 * the host. AE2's pattern logic, inventories and the outer NBT container remain host-owned.
 */
public final class TianshuTerminalState {
    /** Historical schemas, not a migration: callers keep their original container and keys. */
    public enum NbtFormat {
        PART("TianshuEncodingMode", "MaintainableView", "ClosedLoopDraft", "ProcessingDraft"),
        WIRELESS("tianshuMode", "tianshuMaintainableView", "tianshuClosedLoopDraft", "tianshuProcessingDraft");

        private final String mode;
        private final String view;
        private final String closedLoop;
        private final String processing;

        NbtFormat(String mode, String view, String closedLoop, String processing) {
            this.mode = mode;
            this.view = view;
            this.closedLoop = closedLoop;
            this.processing = processing;
        }
    }

    private TianshuEncodingMode encodingMode = TianshuEncodingMode.CRAFTING;
    private boolean maintainableView;
    @Nullable
    private ClosedLoopTerminalDraft closedLoopDraft;
    @Nullable
    private ProcessingPatternTerminalDraft processingDraft;

    public TianshuEncodingMode getEncodingMode() {
        return encodingMode;
    }

    public boolean setEncodingMode(@Nullable TianshuEncodingMode mode) {
        if (mode == null || mode == encodingMode) {
            return false;
        }
        encodingMode = mode;
        return true;
    }

    public boolean isMaintainableView() {
        return maintainableView;
    }

    public boolean setMaintainableView(boolean enabled) {
        if (maintainableView == enabled) {
            return false;
        }
        maintainableView = enabled;
        return true;
    }

    @Nullable
    public ClosedLoopTerminalDraft getClosedLoopDraft() {
        return closedLoopDraft;
    }

    public boolean setClosedLoopDraft(@Nullable ClosedLoopTerminalDraft draft) {
        if (ClosedLoopTerminalDraft.sameState(closedLoopDraft, draft)) {
            return false;
        }
        closedLoopDraft = draft;
        return true;
    }

    @Nullable
    public ProcessingPatternTerminalDraft getProcessingDraft() {
        return processingDraft;
    }

    public boolean setProcessingDraft(@Nullable ProcessingPatternTerminalDraft draft) {
        if (ProcessingPatternTerminalDraft.sameState(processingDraft, draft)) {
            return false;
        }
        processingDraft = draft;
        return true;
    }

    public void read(CompoundTag data, NbtFormat format) {
        try {
            encodingMode = TianshuEncodingMode.valueOf(data.getString(format.mode));
        } catch (IllegalArgumentException ignored) {
            encodingMode = TianshuEncodingMode.CRAFTING;
        }
        maintainableView = data.getBoolean(format.view);
        closedLoopDraft = data.contains(format.closedLoop, Tag.TAG_COMPOUND)
                ? ClosedLoopTerminalDraft.read(data.getCompound(format.closedLoop))
                : null;
        processingDraft = data.contains(format.processing, Tag.TAG_COMPOUND)
                ? ProcessingPatternTerminalDraft.read(data.getCompound(format.processing))
                : null;
    }

    /** Updates only our four fields, preserving native pattern data and other integrations' keys. */
    public void write(CompoundTag data, NbtFormat format) {
        data.putString(format.mode, encodingMode.name());
        data.putBoolean(format.view, maintainableView);
        if (closedLoopDraft != null) {
            data.put(format.closedLoop, closedLoopDraft.write());
        } else {
            data.remove(format.closedLoop);
        }
        if (processingDraft != null) {
            data.put(format.processing, processingDraft.write());
        } else {
            data.remove(format.processing);
        }
    }
}
