package com.moakiee.ae2lt.logic;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class OverloadBlastFakePlayer extends FakePlayer {
    private static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("8b36a3f7-5b4d-4a2e-9b7c-ae2ae2ae2ae2"),
            "[ae2lt-overload-tnt]");

    private OverloadBlastFakePlayer(ServerLevel level) {
        super(level, PROFILE);
    }

    public static OverloadBlastFakePlayer get(ServerLevel level) {
        return new OverloadBlastFakePlayer(level);
    }
}
