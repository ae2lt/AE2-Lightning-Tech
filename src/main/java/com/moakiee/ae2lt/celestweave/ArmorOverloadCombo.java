package com.moakiee.ae2lt.celestweave;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.module.CelestweaveArmorSubmodule;

public final class ArmorOverloadCombo {
    private static final String TAG_COMBO_UNTIL = "ComboUntil";
    private static final String TAG_COMBO_COUNT = "ComboCount";

    private ArmorOverloadCombo() {
    }

    public static int nextComboIndex(State state, long gameTime) {
        State safeState = state == null ? State.EMPTY : state;
        if (safeState.comboUntil() >= gameTime) {
            return saturatingIncrement(Math.max(0, safeState.comboCount()));
        }
        return 1;
    }

    public static State recordTrigger(State state, long gameTime, int comboWindowTicks, int comboIndex) {
        return new State(
                saturatingAdd(gameTime, Math.max(1L, comboWindowTicks)),
                Math.max(1, comboIndex));
    }

    public static long scaledCost(long baseCost, int comboIndex) {
        int safeCombo = Math.max(1, comboIndex);
        if (baseCost <= 0L) {
            return 0L;
        }
        if (baseCost > Long.MAX_VALUE / safeCombo) {
            return Long.MAX_VALUE;
        }
        return baseCost * safeCombo;
    }

    public static int nextComboIndex(ItemStack armor, CelestweaveArmorSubmodule submodule, long gameTime) {
        return nextComboIndex(readState(CelestweaveArmorState.getSubmoduleData(armor, submodule)), gameTime);
    }

    public static void recordTrigger(
            ItemStack armor,
            CelestweaveArmorSubmodule submodule,
            long gameTime,
            int comboWindowTicks,
            int comboIndex) {
        CompoundTag data = CelestweaveArmorState.getSubmoduleData(armor, submodule);
        writeState(data, recordTrigger(readState(data), gameTime, comboWindowTicks, comboIndex));
        CelestweaveArmorState.setSubmoduleData(armor, submodule, data);
    }

    private static State readState(CompoundTag data) {
        if (data == null) {
            return State.EMPTY;
        }
        long comboUntil = data.contains(TAG_COMBO_UNTIL, Tag.TAG_LONG) ? data.getLong(TAG_COMBO_UNTIL) : -1L;
        int comboCount = data.contains(TAG_COMBO_COUNT, Tag.TAG_INT) ? data.getInt(TAG_COMBO_COUNT) : 0;
        return new State(comboUntil, comboCount);
    }

    private static void writeState(CompoundTag data, State state) {
        data.putLong(TAG_COMBO_UNTIL, state.comboUntil());
        data.putInt(TAG_COMBO_COUNT, state.comboCount());
    }

    private static int saturatingIncrement(int value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : value + 1;
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    public record State(long comboUntil, int comboCount) {
        public static final State EMPTY = new State(-1L, 0);

        public State {
            comboCount = Math.max(0, comboCount);
        }
    }
}
