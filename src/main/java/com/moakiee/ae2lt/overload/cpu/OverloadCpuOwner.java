package com.moakiee.ae2lt.overload.cpu;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * Runtime handle for the crafting CPU instance that owns an overload-side
 * pending-output state.
 * <p>
 * This keeps the state model explicit about ownership without requiring any
 * changes to AE2's native job objects.
 */
public final class OverloadCpuOwner {
    private final UUID craftingId;
    private final int logicIdentity;
    private final WeakReference<Object> logicRef;

    private OverloadCpuOwner(UUID craftingId, Object logic) {
        this.craftingId = Objects.requireNonNull(craftingId, "craftingId");
        this.logicIdentity = System.identityHashCode(logic);
        this.logicRef = new WeakReference<>(logic);
    }

    public static OverloadCpuOwner from(UUID craftingId, Object logic) {
        Objects.requireNonNull(craftingId, "craftingId");
        Objects.requireNonNull(logic, "logic");
        return new OverloadCpuOwner(craftingId, logic);
    }

    public UUID craftingId() {
        return craftingId;
    }

    public int logicIdentity() {
        return logicIdentity;
    }

    public @Nullable Object logic() {
        return logicRef.get();
    }

    @Override
    public String toString() {
        return "OverloadCpuOwner[craftingId=" + craftingId + ", logicIdentity=" + logicIdentity + "]";
    }
}
