package com.moakiee.ae2lt.logic.tianshu.loop;

import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalAction;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalCapabilities;

/** Server-authoritative upload entry point used by the future terminal packet handler. */
public final class ClosedLoopPatternUploadService {
    public static ClosedLoopPatternRepository.PutResult upload(
            TianshuSupercomputerPortBlockEntity target,
            ClosedLoopPatternPayload payload) {
        if (target == null || payload == null) return ClosedLoopPatternRepository.PutResult.INVALID;
        var capabilities = TianshuTerminalCapabilities.forTianshu(
                target.isFormed(), target.getFunctionProfile());
        if (!capabilities.allows(TianshuTerminalAction.UPLOAD_CLOSED_LOOP_PATTERN)) {
            return ClosedLoopPatternRepository.PutResult.UNAVAILABLE;
        }
        if (target.getLevel() == null
                || !ClosedLoopPatternValidator.validate(payload, target.getLevel()).valid()) {
            return ClosedLoopPatternRepository.PutResult.INVALID;
        }
        var result = target.getClosedLoopPatternRepository().put(payload);
        if (result == ClosedLoopPatternRepository.PutResult.ADDED
                || result == ClosedLoopPatternRepository.PutResult.UPDATED) {
            target.closedLoopPatternsChanged();
        }
        return result;
    }

    private ClosedLoopPatternUploadService() {
    }
}
