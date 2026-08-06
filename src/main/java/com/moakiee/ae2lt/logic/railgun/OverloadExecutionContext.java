package com.moakiee.ae2lt.logic.railgun;

import java.util.IdentityHashMap;
import java.util.Objects;

import net.minecraft.world.entity.LivingEntity;

/**
 * Synchronous scope for overload-execution death callbacks.
 *
 * <p>The scope is intentionally thread-local and identity-based. A persistent entity flag
 * could survive an exception or save/reload and incorrectly turn a later ordinary death into
 * an execution. Counts preserve correctness if a death callback re-enters execution for the
 * same target.
 */
final class OverloadExecutionContext {
    private static final ThreadLocal<IdentityHashMap<LivingEntity, Integer>> ACTIVE = new ThreadLocal<>();

    private OverloadExecutionContext() {
    }

    static Scope enter(LivingEntity target) {
        Objects.requireNonNull(target, "target");
        var active = ACTIVE.get();
        if (active == null) {
            active = new IdentityHashMap<>();
            ACTIVE.set(active);
        }
        active.merge(target, 1, Integer::sum);
        return new Scope(target);
    }

    static boolean contains(LivingEntity target) {
        var active = ACTIVE.get();
        return active != null && active.containsKey(target);
    }

    private static void exit(LivingEntity target) {
        var active = ACTIVE.get();
        if (active == null) {
            throw new IllegalStateException("Overload execution scope closed on the wrong thread");
        }
        Integer count = active.get(target);
        if (count == null) {
            throw new IllegalStateException("Overload execution scope already closed");
        }
        if (count == 1) {
            active.remove(target);
        } else {
            active.put(target, count - 1);
        }
        if (active.isEmpty()) {
            ACTIVE.remove();
        }
    }

    static final class Scope implements AutoCloseable {
        private final LivingEntity target;
        private boolean closed;

        private Scope(LivingEntity target) {
            this.target = target;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                exit(target);
            }
        }
    }
}
