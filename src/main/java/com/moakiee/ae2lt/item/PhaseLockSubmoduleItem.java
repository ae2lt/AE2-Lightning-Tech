package com.moakiee.ae2lt.item;

import java.util.List;

import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.module.PhaseLockSubmodule;

public final class PhaseLockSubmoduleItem extends AbstractSingleArmorSubmoduleItem {
    public PhaseLockSubmoduleItem(Properties properties) {
        super(properties, ArmorPart.CHEST, PhaseLockSubmodule.INSTANCE, stack -> List.of());
    }
}
