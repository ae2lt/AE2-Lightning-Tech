package com.moakiee.ae2lt.logic.tianshu.loop;

import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import java.util.Objects;

/**
 * One flattened member of a closed-loop macro.
 *
 * <p>{@code copiesPerCycle} is the total physical work represented by one macro firing.
 * {@code seedWaveCopies} is the smaller atomic routing schedule whose returned seeds may be
 * reused by a later wave. Ordinary, non-flattened patterns keep both values equal.
 */
public record ClosedLoopMemberPattern(
        SourcePatternSnapshot pattern,
        long copiesPerCycle,
        long seedWaveCopies) {
    public ClosedLoopMemberPattern(SourcePatternSnapshot pattern, long copiesPerCycle) {
        this(pattern, copiesPerCycle, copiesPerCycle);
    }

    public ClosedLoopMemberPattern {
        pattern = Objects.requireNonNull(pattern, "pattern");
        if (copiesPerCycle < 1) throw new IllegalArgumentException("member copies per cycle must be positive");
        if (seedWaveCopies < 1) {
            throw new IllegalArgumentException("member seed-wave copies must be positive");
        }
        if (seedWaveCopies > copiesPerCycle || copiesPerCycle % seedWaveCopies != 0L) {
            throw new IllegalArgumentException(
                    "member copies must be an integer multiple of seed-wave copies");
        }
    }

    /** Number of identical seed waves represented by this member's total copies. */
    public long seedWaveRepetitions() {
        return copiesPerCycle / seedWaveCopies;
    }
}
