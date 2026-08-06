package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.stacks.GenericStack;
import appeng.util.ConfigInventory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Atomic ratio-preserving scaling for processing-pattern inputs and outputs. */
public final class ProcessingPatternMultiplier {
    public static boolean apply(ConfigInventory inputs, ConfigInventory outputs, int factor) {
        if (inputs == null || outputs == null || factor == 0 || factor == 1 || factor == -1) return false;
        var inputResult = scaled(snapshot(inputs), factor);
        var outputResult = scaled(snapshot(outputs), factor);
        if (inputResult == null || outputResult == null) return false;
        write(inputs, inputResult);
        write(outputs, outputResult);
        return true;
    }

    public static List<GenericStack> scaled(List<GenericStack> stacks, int factor) {
        if (stacks == null || factor == 0 || factor == 1 || factor == -1) return null;
        boolean divide = factor < 0;
        long amountFactor = Math.abs((long) factor);
        var result = new ArrayList<GenericStack>(stacks.size());
        for (var stack : stacks) {
            if (stack == null) {
                result.add(null);
                continue;
            }
            long amount = stack.amount();
            long scaled;
            if (divide) {
                if (amount <= 0 || amount % amountFactor != 0) return null;
                scaled = amount / amountFactor;
            } else {
                if (amount <= 0 || amount > Long.MAX_VALUE / amountFactor) return null;
                scaled = amount * amountFactor;
            }
            if (scaled <= 0) return null;
            result.add(new GenericStack(stack.what(), scaled));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<GenericStack> snapshot(ConfigInventory inventory) {
        var result = new ArrayList<GenericStack>(inventory.size());
        for (int i = 0; i < inventory.size(); i++) result.add(inventory.getStack(i));
        return result;
    }

    private static void write(ConfigInventory inventory, List<GenericStack> values) {
        inventory.beginBatch();
        try {
            for (int i = 0; i < values.size(); i++) inventory.setStack(i, values.get(i));
        } finally {
            inventory.endBatch();
        }
    }

    private ProcessingPatternMultiplier() { }
}
