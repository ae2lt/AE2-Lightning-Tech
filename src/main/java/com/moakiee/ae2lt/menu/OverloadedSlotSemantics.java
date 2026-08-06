package com.moakiee.ae2lt.menu;

import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;

public final class OverloadedSlotSemantics {

    public static final SlotSemantic OL_1 = SlotSemantics.register("OL_1", false);
    public static final SlotSemantic OL_2 = SlotSemantics.register("OL_2", false);
    public static final SlotSemantic OL_3 = SlotSemantics.register("OL_3", false);
    public static final SlotSemantic OL_4 = SlotSemantics.register("OL_4", false);
    public static final SlotSemantic OL_5 = SlotSemantics.register("OL_5", false);
    public static final SlotSemantic OL_6 = SlotSemantics.register("OL_6", false);
    public static final SlotSemantic OL_7 = SlotSemantics.register("OL_7", false);
    public static final SlotSemantic OL_8 = SlotSemantics.register("OL_8", false);

    public static final SlotSemantic[] CONFIG_PATTERN = { OL_1, OL_3, OL_5, OL_7 };
    public static final SlotSemantic[] STORAGE_PATTERN = { OL_2, OL_4, OL_6, OL_8 };

    private OverloadedSlotSemantics() {}

    public static void init() {}
}
