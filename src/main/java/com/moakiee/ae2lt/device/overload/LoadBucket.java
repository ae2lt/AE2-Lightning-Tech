package com.moakiee.ae2lt.device.overload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class LoadBucket {
    public static final double DEFAULT_DECAY = 0.8D;
    public static final int DEFAULT_MAX_TICKS = 10;
    public static final double DEFAULT_EPSILON = 0.5D;

    private final double defaultDecay;
    private final int defaultMaxTicks;
    private final double epsilon;
    private final Map<String, Integer> states = new HashMap<>();
    private final Map<String, List<Pulse>> pulses = new HashMap<>();
    private final Map<String, Integer> currentByKey = new HashMap<>();
    private int current;

    public LoadBucket() {
        this(DEFAULT_DECAY, DEFAULT_MAX_TICKS, DEFAULT_EPSILON);
    }

    public LoadBucket(double defaultDecay, int defaultMaxTicks, double epsilon) {
        this.defaultDecay = defaultDecay;
        this.defaultMaxTicks = Math.max(1, defaultMaxTicks);
        this.epsilon = Math.max(0.0D, epsilon);
    }

    public void setState(String key, int loadPerTick) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (loadPerTick <= 0) {
            clearState(key);
            return;
        }
        states.put(key, loadPerTick);
    }

    public void clearState(String key) {
        if (key != null) {
            states.remove(key);
            currentByKey.remove(key);
        }
    }

    public void clear(String key) {
        if (key != null) {
            states.remove(key);
            pulses.remove(key);
            currentByKey.remove(key);
        }
    }

    public void addPulse(String key, int base, double decay, int maxTicks) {
        if (key == null || key.isBlank()) {
            addPulse(base, decay, maxTicks);
            return;
        }
        if (base <= 0 || maxTicks <= 0) {
            return;
        }
        pulses.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Pulse(base, decay, maxTicks));
    }

    public void addPulse(String key, int base) {
        addPulse(key, base, defaultDecay, defaultMaxTicks);
    }

    public void addPulse(int base, double decay, int maxTicks) {
        if (base <= 0 || maxTicks <= 0) {
            return;
        }
        pulses.computeIfAbsent("", ignored -> new ArrayList<>()).add(new Pulse(base, decay, maxTicks));
    }

    public void addPulse(int base) {
        addPulse(base, defaultDecay, defaultMaxTicks);
    }

    public int tick() {
        int total = 0;
        currentByKey.clear();
        for (var entry : states.entrySet()) {
            int stateLoad = Math.max(0, entry.getValue());
            total += stateLoad;
            currentByKey.merge(entry.getKey(), stateLoad, Integer::sum);
        }
        Iterator<Map.Entry<String, List<Pulse>>> groupIt = pulses.entrySet().iterator();
        while (groupIt.hasNext()) {
            var group = groupIt.next();
            int groupTotal = 0;
            Iterator<Pulse> it = group.getValue().iterator();
            while (it.hasNext()) {
                Pulse pulse = it.next();
                int load = Math.max(0, (int) Math.round(pulse.value));
                total += load;
                groupTotal += load;
                pulse.ticksRemaining--;
                pulse.value *= pulse.decay;
                if (pulse.ticksRemaining <= 0 || pulse.value < epsilon) {
                    it.remove();
                }
            }
            if (groupTotal > 0 && !group.getKey().isBlank()) {
                currentByKey.merge(group.getKey(), groupTotal, Integer::sum);
            }
            if (group.getValue().isEmpty()) {
                groupIt.remove();
            }
        }
        current = total;
        return current;
    }

    public int current() {
        return current;
    }

    public int currentFor(String key) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        return currentByKey.getOrDefault(key, 0);
    }

    public void clear() {
        states.clear();
        pulses.clear();
        current = 0;
    }

    private static final class Pulse {
        private double value;
        private final double decay;
        private int ticksRemaining;

        private Pulse(int base, double decay, int ticksRemaining) {
            this.value = base;
            this.decay = decay;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
