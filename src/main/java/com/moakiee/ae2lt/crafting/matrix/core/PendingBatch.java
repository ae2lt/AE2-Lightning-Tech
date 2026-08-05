package com.moakiee.ae2lt.crafting.matrix.core;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

final class PendingBatch {
    final Object2LongOpenHashMap<AEKey> outputs = new Object2LongOpenHashMap<>();
    long copies;
}
