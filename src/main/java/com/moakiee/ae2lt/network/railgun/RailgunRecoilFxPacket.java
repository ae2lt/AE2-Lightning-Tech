package com.moakiee.ae2lt.network.railgun;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

import net.minecraft.network.FriendlyByteBuf;


/** Server to client: apply visual recoil after a charged shot. */
public record RailgunRecoilFxPacket(float pitchUp, int tierOrdinal) {
    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(pitchUp);
        buf.writeVarInt(tierOrdinal);
    }

    public static RailgunRecoilFxPacket decode(FriendlyByteBuf buf) {
        return new RailgunRecoilFxPacket(buf.readFloat(), buf.readVarInt());
    }

    public static void handle(RailgunRecoilFxPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> RailgunClientBridge.recoil(p));
    }
}
