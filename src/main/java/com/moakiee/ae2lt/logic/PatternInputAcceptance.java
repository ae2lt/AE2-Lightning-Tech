package com.moakiee.ae2lt.logic;

/** Input-capacity contract used for one provider dispatch attempt. */
public enum PatternInputAcceptance {
    /**
     * AE2 single-copy semantics: every input must accept at least one unit, and
     * any unaccepted remainder stays owned by the provider for later delivery.
     */
    VANILLA_SINGLE_COPY,

    /**
     * Adaptive aggregate semantics: every scaled input must fit in full before
     * ownership of the complete batch can leave the crafting CPU.
     */
    COMPLETE_BATCH
}
