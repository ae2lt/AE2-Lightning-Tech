package com.moakiee.ae2lt.logic.tianshu.loop;

/**
 * Marks a single-member, single-seed closed-loop dispatch that may use the matrix main core's
 * built-in closed-loop batch path.
 * Providers without that processor must expose zero batch capacity so AE2 uses its normal
 * single-pattern dispatch path.
 */
public interface ClosedLoopBatchPatternDetails {
}
