package com.moakiee.ae2lt.crafting.runtime;

import appeng.api.crafting.IPatternDetails;
import java.util.Map;

/**
 * Optional macro-pattern contract. The fast planner models the macro as one contracted graph node,
 * then replaces its firing count with the concrete member-pattern firing counts in the AE2 plan.
 */
public interface PatternFiringExpander {
    Map<IPatternDetails, Long> expandPatternFirings(long macroFirings);
}
