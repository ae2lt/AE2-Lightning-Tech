package com.moakiee.ae2lt.celestweave.service;

import java.util.ArrayList;
import java.util.List;

final class ArmorEnergySpendPlan {
    private final boolean canPay;
    private final List<Debit> debits;

    private ArmorEnergySpendPlan(boolean canPay, List<Debit> debits) {
        this.canPay = canPay;
        this.debits = List.copyOf(debits);
    }

    static ArmorEnergySpendPlan create(long amount, List<Source> sources) {
        if (amount <= 0L) {
            return new ArmorEnergySpendPlan(true, List.of());
        }
        long remaining = amount;
        var debits = new ArrayList<Debit>();
        for (Source source : sources) {
            long available = Math.max(0L, source.stored());
            if (available <= 0L) {
                continue;
            }
            long consumed = Math.min(available, remaining);
            debits.add(new Debit(source.index(), consumed));
            remaining -= consumed;
            if (remaining <= 0L) {
                return new ArmorEnergySpendPlan(true, debits);
            }
        }
        return new ArmorEnergySpendPlan(false, List.of());
    }

    boolean canPay() {
        return canPay;
    }

    List<Debit> debits() {
        return debits;
    }

    record Source(int index, long stored) {
    }

    record Debit(int sourceIndex, long amount) {
    }
}
