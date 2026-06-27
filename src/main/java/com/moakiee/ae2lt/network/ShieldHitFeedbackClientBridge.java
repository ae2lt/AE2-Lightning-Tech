package com.moakiee.ae2lt.network;

import java.util.Objects;

public final class ShieldHitFeedbackClientBridge {
    private static Hooks hooks = Hooks.NOOP;

    private ShieldHitFeedbackClientBridge() {
    }

    public static void install(Hooks hooks) {
        ShieldHitFeedbackClientBridge.hooks = Objects.requireNonNull(hooks);
    }

    public static void suppress(ShieldHitFeedbackSuppressionPacket packet) {
        hooks.suppress(packet.entityId());
    }

    public interface Hooks {
        Hooks NOOP = new Hooks() {
        };

        default void suppress(int entityId) {
        }
    }
}
