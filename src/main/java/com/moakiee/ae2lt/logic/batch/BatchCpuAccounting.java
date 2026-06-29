package com.moakiee.ae2lt.logic.batch;

public final class BatchCpuAccounting {
    private BatchCpuAccounting() {
    }

    public static int maxCopiesForCpuOps(int cpuOps) {
        if (cpuOps <= 0) return 0;
        long maxCopies = (long) cpuOps * cpuOps;
        return maxCopies >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) maxCopies;
    }

    public static int cpuOpsForCopies(int copies) {
        if (copies <= 0) return 0;

        int root = (int) Math.sqrt(copies);
        while ((long) root * root < copies) {
            root++;
        }
        while (root > 0 && (long) (root - 1) * (root - 1) >= copies) {
            root--;
        }
        return root;
    }
}
