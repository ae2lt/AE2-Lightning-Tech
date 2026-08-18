package com.moakiee.ae2lt.client.railgun;

import com.moakiee.ae2lt.network.railgun.RailgunBeamChainFxPacket;
import com.moakiee.ae2lt.network.railgun.RailgunBeamUpdatePacket;
import com.moakiee.ae2lt.network.railgun.RailgunClientBridge;
import com.moakiee.ae2lt.network.railgun.RailgunFirePacket;
import com.moakiee.ae2lt.network.railgun.RailgunRecoilFxPacket;

public final class RailgunClientBootstrap {
    private static boolean installed;

    private RailgunClientBootstrap() {
    }

    public static void install() {
        if (installed) {
            return;
        }
        RailgunClientBridge.install(new RailgunClientBridge.Hooks() {
            @Override
            public void handleFire(RailgunFirePacket packet) {
                RailgunClientFx.playCharged(packet);
            }

            @Override
            public void handleBeamUpdate(RailgunBeamUpdatePacket packet) {
                RailgunBeamRenderClient.applyUpdate(packet);
            }

            @Override
            public void handleBeamChainFx(RailgunBeamChainFxPacket packet) {
                RailgunBeamChainFx.play(packet);
            }

            @Override
            public void handleRecoil(RailgunRecoilFxPacket packet) {
                RailgunCameraShake.applyRecoil(packet.pitchUp(), packet.tierOrdinal());
            }
        });
        installed = true;
    }
}
