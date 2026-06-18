package com.moakiee.ae2lt.celestweave.module;

public final class UndyingSubmodule extends AbstractCelestweaveArmorSubmodule {

    public static final UndyingSubmodule INSTANCE = new UndyingSubmodule();

    private UndyingSubmodule() {
    }

    @Override
    public String id() {
        return "undying";
    }

    @Override
    public String nameKey() {
        return "ae2lt.celestweave.feature.undying.name";
    }

    @Override
    public String descriptionKey() {
        return "ae2lt.celestweave.feature.undying.desc";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int getMaxInstallAmount() {
        return 1;
    }

}
