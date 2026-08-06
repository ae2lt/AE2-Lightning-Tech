package com.moakiee.ae2lt.overload.cpu;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

/**
 * Holds contextual information during a single {@code CraftingCpuLogic.insert} call,
 * allowing later injection points to access captured state from earlier ones.
 * <p>
 * Extracted from the Mixin class to avoid IllegalClassLoadError — inner classes
 * defined inside a Mixin class reside in the mixin package and cannot be
 * referenced directly by the Mixin framework.
 */
public final class InsertContext {
    private final AEKey key;
    private final long requestedAmount;
    private final Actionable type;
    private long strictMatched;

    public InsertContext(AEKey key, long requestedAmount, Actionable type) {
        this.key = key;
        this.requestedAmount = requestedAmount;
        this.type = type;
    }

    public AEKey getKey() {
        return key;
    }

    public long getRequestedAmount() {
        return requestedAmount;
    }

    public Actionable getType() {
        return type;
    }

    public long getStrictMatched() {
        return strictMatched;
    }

    public void setStrictMatched(long strictMatched) {
        this.strictMatched = strictMatched;
    }
}
