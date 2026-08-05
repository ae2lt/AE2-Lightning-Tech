package com.moakiee.ae2lt.crafting.runtime.api;

/** Stable comparator shared by TimeWheel schedulers and host-side tests. */
public final class CraftingTaskPriorities {
    public static int compare(Object left, Object right) {
        int leftPriority = left instanceof IPrioritizedCraftingTask prioritized
                ? prioritized.dispatchPriority() : 0;
        int rightPriority = right instanceof IPrioritizedCraftingTask prioritized
                ? prioritized.dispatchPriority() : 0;
        int compared = Integer.compare(rightPriority, leftPriority);
        if (compared != 0) return compared;
        int leftOrder = left instanceof IPrioritizedCraftingTask prioritized
                ? prioritized.dispatchOrder() : 0;
        int rightOrder = right instanceof IPrioritizedCraftingTask prioritized
                ? prioritized.dispatchOrder() : 0;
        return Integer.compare(leftOrder, rightOrder);
    }

    /**
     * Uses the currently active task as an identity-based tie breaker without overriding an
     * explicit priority or order. This gives the time-wheel CPU vanilla-style task-major dispatch
     * while still allowing higher-priority closed-loop work to preempt ordinary recipes.
     */
    public static int compare(Object left, Object right, Object preferred) {
        int compared = compare(left, right);
        if (compared != 0 || left == right) return compared;
        if (left == preferred) return -1;
        if (right == preferred) return 1;
        return 0;
    }

    private CraftingTaskPriorities() { }
}
