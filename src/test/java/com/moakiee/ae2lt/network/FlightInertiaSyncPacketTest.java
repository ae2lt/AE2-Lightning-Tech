package com.moakiee.ae2lt.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import com.moakiee.ae2lt.celestweave.module.PhaseFlightMode;

class FlightInertiaSyncPacketTest {
    @Test
    void allModesRoundTripWithoutChangingOtherFlightSettings() {
        for (PhaseFlightMode mode : PhaseFlightMode.values()) {
            for (int flags = 0; flags < 16; flags++) {
                var packet = new FlightInertiaSyncPacket(UUID.randomUUID(),
                        (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, mode, (flags & 8) != 0);
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    packet.write(buffer);
                    assertEquals(21, buffer.readableBytes());
                    assertEquals(packet, FlightInertiaSyncPacket.decode(buffer));
                    assertFalse(buffer.isReadable());
                } finally {
                    buffer.release();
                }
            }
        }
    }

    @Test
    void legacyBooleanPacketsKeepOffAndAllInsteadOfEnumOrdinals() {
        for (boolean enabled : new boolean[] {false, true}) {
            var armorId = UUID.randomUUID();
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeUUID(armorId);
                buffer.writeBoolean(true);
                buffer.writeBoolean(true);
                buffer.writeBoolean(false);
                buffer.writeBoolean(enabled);
                buffer.writeBoolean(true);
                assertEquals(new FlightInertiaSyncPacket(armorId, true, true, false,
                        enabled ? PhaseFlightMode.ALL : PhaseFlightMode.OFF, true),
                        FlightInertiaSyncPacket.decode(buffer));
                assertFalse(buffer.isReadable());
            } finally {
                buffer.release();
            }
        }
    }
}
