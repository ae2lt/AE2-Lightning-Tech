package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.helpers.IPatternTerminalMenuHost;
import appeng.api.networking.security.IActionHost;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public interface TianshuPatternTerminalHost extends IPatternTerminalMenuHost, TianshuTerminalHost {
    TianshuEncodingMode getTianshuEncodingMode();
    void setTianshuEncodingMode(TianshuEncodingMode mode);

    @Nullable
    default ClosedLoopTerminalDraft getClosedLoopTerminalDraft() {
        return null;
    }

    default void setClosedLoopTerminalDraft(@Nullable ClosedLoopTerminalDraft draft) {
    }

    @Nullable
    default ProcessingPatternTerminalDraft getProcessingPatternTerminalDraft() {
        return null;
    }

    default void setProcessingPatternTerminalDraft(
            @Nullable ProcessingPatternTerminalDraft draft) {
    }

}
