package com.moakiee.ae2lt.logic.tianshu.terminal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Pure inventory simulation: never mutates either the supplied stacks or a live inventory. */
public final class WirelessJeiInventoryPlan {
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_COUNT = 36 * 64;

    private WirelessJeiInventoryPlan() {}

    public static List<ItemStack> copy(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /** Returns null unless every requested item fits in the ordinary 36 inventory slots. */
    public static List<ItemStack> insert(List<ItemStack> inventory, List<ItemStack> requested) {
        var result = copy(inventory);
        if (result.size() != 36 || requested.size() > MAX_ENTRIES) return null;
        int total = 0;
        for (var request : requested) {
            if (request.isEmpty() || request.getCount() > MAX_COUNT) return null;
            total += request.getCount();
            if (total > MAX_COUNT) return null;
            int left = request.getCount();
            for (int pass = 0; pass < 2 && left > 0; pass++) {
                for (int i = 0; i < result.size() && left > 0; i++) {
                    var present = result.get(i);
                    if (pass == 0 ? present.isEmpty() || !ItemStack.isSameItemSameComponents(present, request)
                            : !present.isEmpty()) continue;
                    int moved = Math.min(left, Math.min(64, request.getMaxStackSize()) - present.getCount());
                    if (moved <= 0) continue;
                    result.set(i, request.copyWithCount(present.getCount() + moved));
                    left -= moved;
                }
            }
            if (left != 0) return null;
        }
        return result;
    }
}
