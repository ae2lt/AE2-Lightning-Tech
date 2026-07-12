package com.moakiee.ae2lt.logic.tianshu.loop;

import com.moakiee.thunderbolt.ae2.overload.pattern.SourcePatternSnapshot;
import java.util.Objects;

public record ClosedLoopMemberPattern(SourcePatternSnapshot pattern, long copiesPerCycle) {
    public ClosedLoopMemberPattern {
        pattern = Objects.requireNonNull(pattern, "pattern");
        if (copiesPerCycle < 1) throw new IllegalArgumentException("member copies per cycle must be positive");
    }
}
