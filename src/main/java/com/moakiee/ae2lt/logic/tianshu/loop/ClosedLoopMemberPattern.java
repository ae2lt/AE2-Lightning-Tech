package com.moakiee.ae2lt.logic.tianshu.loop;

import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import java.util.Objects;

/**
 * One flattened member of a closed-loop macro.
 *
 * <p>{@code copiesPerCycle} is one member's coefficient in the primitive integer ratio.
 * Parallel waves are configured separately by the payload's execution-seed multiplier.
 */
public record ClosedLoopMemberPattern(
        SourcePatternSnapshot pattern,
        long copiesPerCycle) {
    public ClosedLoopMemberPattern {
        pattern = Objects.requireNonNull(pattern, "pattern");
        if (copiesPerCycle < 1) throw new IllegalArgumentException("member copies per cycle must be positive");
    }
}
