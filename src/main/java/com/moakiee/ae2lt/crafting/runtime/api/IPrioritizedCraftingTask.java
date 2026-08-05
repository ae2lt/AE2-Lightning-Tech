package com.moakiee.ae2lt.crafting.runtime.api;

/**
 * Optional TimeWheel scheduling contract for patterns whose safe execution depends on ordering.
 *
 * <p>Higher priorities are attempted before lower priorities whenever both are due. Within one
 * priority, lower order values are attempted first. A task that cannot currently extract its inputs
 * is still parked normally, allowing producers and unrelated work to make progress.
 */
public interface IPrioritizedCraftingTask {
    int dispatchPriority();

    int dispatchOrder();
}
