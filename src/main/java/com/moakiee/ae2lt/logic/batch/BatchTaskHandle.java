package com.moakiee.ae2lt.logic.batch;

import appeng.api.crafting.IPatternDetails;

public interface BatchTaskHandle {
    IPatternDetails details();

    long getValue();

    void setValue(long value);
}
