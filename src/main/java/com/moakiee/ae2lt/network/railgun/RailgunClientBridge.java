package com.moakiee.ae2lt.network.railgun;

import java.util.Objects;

public final class RailgunClientBridge {
    private static Hooks hooks = Hooks.NOOP;

    private RailgunClientBridge() {
    }

    public static void install(Hooks hooks) {
        RailgunClientBridge.hooks = Objects.requireNonNull(hooks);
    }

    public static void fire(RailgunFirePacket packet) {
        hooks.handleFire(packet);
    }

    public static void beamUpdate(RailgunBeamUpdatePacket packet) {
        hooks.handleBeamUpdate(packet);
    }

    public static void beamChainFx(RailgunBeamChainFxPacket packet) {
        hooks.handleBeamChainFx(packet);
    }

    public static void recoil(RailgunRecoilFxPacket packet) {
        hooks.handleRecoil(packet);
    }

    public interface Hooks {
        Hooks NOOP = new Hooks() {
        };

        default void handleFire(RailgunFirePacket packet) {
        }

        default void handleBeamUpdate(RailgunBeamUpdatePacket packet) {
        }

        default void handleBeamChainFx(RailgunBeamChainFxPacket packet) {
        }

        default void handleRecoil(RailgunRecoilFxPacket packet) {
        }
    }
}
