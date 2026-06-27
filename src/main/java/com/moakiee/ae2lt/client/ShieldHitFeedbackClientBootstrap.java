package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.network.ShieldHitFeedbackClientBridge;

public final class ShieldHitFeedbackClientBootstrap {
    private static boolean installed;

    private ShieldHitFeedbackClientBootstrap() {
    }

    public static void install() {
        if (installed) {
            return;
        }
        ShieldHitFeedbackClientBridge.install(new ShieldHitFeedbackClientBridge.Hooks() {
            @Override
            public void suppress(int entityId) {
                ShieldHitFeedbackClientState.suppress(entityId);
            }
        });
        installed = true;
    }
}
