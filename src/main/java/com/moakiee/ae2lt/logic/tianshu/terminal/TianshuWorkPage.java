package com.moakiee.ae2lt.logic.tianshu.terminal;

public enum TianshuWorkPage {
    CRAFTING, SMITHING, ANVIL, STONECUTTING, CELL;

    public String translationKey() {
        return "ae2lt.tianshu.work_page." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
