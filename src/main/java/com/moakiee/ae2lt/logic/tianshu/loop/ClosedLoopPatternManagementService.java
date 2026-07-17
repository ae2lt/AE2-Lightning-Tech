package com.moakiee.ae2lt.logic.tianshu.loop;

import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalAction;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalCapabilities;
import java.util.UUID;

/** Server-authoritative non-UI operations for already uploaded closed-loop patterns. */
public final class ClosedLoopPatternManagementService {
    public static ClosedLoopPatternRepository.PutResult setEnabled(
            TianshuSupercomputerPortBlockEntity target, UUID patternId, boolean enabled) {
        var payload = editable(target, patternId);
        if (payload == null) return ClosedLoopPatternRepository.PutResult.UNAVAILABLE;
        if (payload.enabled() == enabled) return ClosedLoopPatternRepository.PutResult.UPDATED;
        if (enabled && (target.getLevel() == null
                || !ClosedLoopPatternValidator.validate(payload, target.getLevel()).valid())) {
            return ClosedLoopPatternRepository.PutResult.INVALID;
        }
        return store(target, payload.withEnabled(enabled));
    }

    public static ClosedLoopPatternRepository.PutResult setExecutionSeedMultiplier(
            TianshuSupercomputerPortBlockEntity target, UUID patternId, int executionSeedMultiplier) {
        var payload = editable(target, patternId);
        return updateSeedMultipliers(target, payload,
                executionSeedMultiplier,
                payload != null ? payload.storedTaskMultiplier() : 1);
    }

    /** Legacy name; stored-task capacity remains an independent setting. */
    @Deprecated
    public static ClosedLoopPatternRepository.PutResult setSeedMultiplier(
            TianshuSupercomputerPortBlockEntity target, UUID patternId, int seedMultiplier) {
        return setExecutionSeedMultiplier(target, patternId, seedMultiplier);
    }

    public static ClosedLoopPatternRepository.PutResult setStoredTaskMultiplier(
            TianshuSupercomputerPortBlockEntity target, UUID patternId, int storedTaskMultiplier) {
        var payload = editable(target, patternId);
        return updateSeedMultipliers(target, payload,
                payload != null ? payload.executionSeedMultiplier() : 1,
                storedTaskMultiplier);
    }

    public static ClosedLoopPatternRepository.PutResult setSeedMultipliers(
            TianshuSupercomputerPortBlockEntity target,
            UUID patternId,
            int executionSeedMultiplier,
            int storedTaskMultiplier) {
        return updateSeedMultipliers(target, editable(target, patternId),
                executionSeedMultiplier, storedTaskMultiplier);
    }

    public static boolean remove(
            TianshuSupercomputerPortBlockEntity target, UUID patternId) {
        if (!canManage(target) || patternId == null) return false;
        var repository = target.getClosedLoopPatternRepository();
        if (repository == null) return false;
        boolean removed = repository.remove(patternId);
        if (removed) target.closedLoopPatternsChanged();
        return removed;
    }

    public static ClosedLoopValidationResult revalidate(
            TianshuSupercomputerPortBlockEntity target, UUID patternId) {
        var repository = target != null ? target.getClosedLoopPatternRepository() : null;
        var payload = repository != null ? repository.get(patternId) : null;
        if (payload == null || target.getLevel() == null) {
            return new ClosedLoopValidationResult(
                    ClosedLoopValidationResult.Status.MEMBER_UNDECODABLE, null);
        }
        return ClosedLoopPatternValidator.validate(payload, target.getLevel());
    }

    private static ClosedLoopPatternPayload editable(
            TianshuSupercomputerPortBlockEntity target, UUID patternId) {
        var repository = canManage(target) ? target.getClosedLoopPatternRepository() : null;
        return repository != null && patternId != null ? repository.get(patternId) : null;
    }

    private static boolean canManage(TianshuSupercomputerPortBlockEntity target) {
        if (target == null) return false;
        return TianshuTerminalCapabilities.forTianshu(
                target.isFormed(), target.getFunctionProfile())
                .allows(TianshuTerminalAction.UPLOAD_CLOSED_LOOP_PATTERN);
    }

    private static ClosedLoopPatternRepository.PutResult updateSeedMultipliers(
            TianshuSupercomputerPortBlockEntity target,
            ClosedLoopPatternPayload payload,
            int executionSeedMultiplier,
            int storedTaskMultiplier) {
        if (executionSeedMultiplier < 1 || storedTaskMultiplier < 1) {
            return ClosedLoopPatternRepository.PutResult.INVALID;
        }
        if (payload == null) return ClosedLoopPatternRepository.PutResult.UNAVAILABLE;
        if (target.getLevel() == null
                || !ClosedLoopPatternValidator.validate(payload, target.getLevel()).valid()) {
            return ClosedLoopPatternRepository.PutResult.INVALID;
        }
        if (payload.executionSeedMultiplier() == executionSeedMultiplier
                && payload.storedTaskMultiplier() == storedTaskMultiplier) {
            return ClosedLoopPatternRepository.PutResult.UPDATED;
        }
        return store(target, payload.withSeedMultipliers(
                executionSeedMultiplier, storedTaskMultiplier));
    }

    private static ClosedLoopPatternRepository.PutResult store(
            TianshuSupercomputerPortBlockEntity target, ClosedLoopPatternPayload payload) {
        var repository = target.getClosedLoopPatternRepository();
        if (repository == null) return ClosedLoopPatternRepository.PutResult.UNAVAILABLE;
        var result = repository.put(payload);
        if (result == ClosedLoopPatternRepository.PutResult.ADDED
                || result == ClosedLoopPatternRepository.PutResult.UPDATED) {
            target.closedLoopPatternsChanged();
        }
        return result;
    }

    private ClosedLoopPatternManagementService() { }
}
