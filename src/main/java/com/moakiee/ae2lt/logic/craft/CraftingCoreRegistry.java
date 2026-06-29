package com.moakiee.ae2lt.logic.craft;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

public final class CraftingCoreRegistry {
    private final Set<Sweepable> active = Collections.newSetFromMap(new IdentityHashMap<>());

    public void markActive(Sweepable sweepable) {
        active.add(sweepable);
    }

    public void markInactive(Sweepable sweepable) {
        active.remove(sweepable);
    }

    public void tickAll() {
        if (active.isEmpty()) return;
        Iterator<Sweepable> it = active.iterator();
        while (it.hasNext()) {
            var sweepable = it.next();
            try {
                if (!sweepable.sweepTick()) {
                    it.remove();
                }
            } catch (Throwable t) {
                appeng.core.AELog.warn("[ae2lt] crafting core sweep failed for %s; removing. %s", sweepable, t);
                it.remove();
            }
        }
    }

    public void clear() {
        active.clear();
    }
}
