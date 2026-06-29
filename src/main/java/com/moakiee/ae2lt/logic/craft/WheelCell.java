package com.moakiee.ae2lt.logic.craft;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

final class WheelCell {
    final Object2LongOpenHashMap<AEKey> outputs = new Object2LongOpenHashMap<>();
    int copies;
}
