package com.moakiee.ae2lt.logic;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.function.Function;

final class ReadyDispatchQueue<T, S extends ReadyDispatchQueue.State> {

    interface State {
        boolean isQueued();

        void setQueued(boolean queued);
    }

    private final ArrayDeque<T> queue = new ArrayDeque<>();
    private final Function<T, S> stateLookup;

    ReadyDispatchQueue(Function<T, S> stateLookup) {
        this.stateLookup = stateLookup;
    }

    boolean offer(T target) {
        var state = stateLookup.apply(target);
        if (state == null || state.isQueued()) {
            return false;
        }
        state.setQueued(true);
        queue.addLast(target);
        return true;
    }

    T peek() {
        return queue.peekFirst();
    }

    T removeHead() {
        var target = queue.pollFirst();
        if (target != null) {
            var state = stateLookup.apply(target);
            if (state != null) {
                state.setQueued(false);
            }
        }
        return target;
    }

    T rotateHeadToTail() {
        var target = queue.pollFirst();
        if (target != null) {
            queue.addLast(target);
        }
        return target;
    }

    boolean remove(T target) {
        if (!queue.remove(target)) {
            return false;
        }
        var state = stateLookup.apply(target);
        if (state != null) {
            state.setQueued(false);
        }
        return true;
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }

    int size() {
        return queue.size();
    }

    void clear() {
        queue.clear();
    }

    void clear(Collection<S> knownStates) {
        queue.clear();
        for (var state : knownStates) {
            state.setQueued(false);
        }
    }
}
