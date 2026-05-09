package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.AE2LightningTech;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class NetworkInit {
    private NetworkInit() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(AE2LightningTech.MODID);

        registrar.playToServer(
                OpenFrequencyMenuPacket.TYPE,
                OpenFrequencyMenuPacket.STREAM_CODEC,
                OpenFrequencyMenuPacket::handle);

        // frequency system: C→S
        registrar.playToServer(
                CreateFrequencyPacket.TYPE,
                CreateFrequencyPacket.STREAM_CODEC,
                CreateFrequencyPacket::handle);
        registrar.playToServer(
                DeleteFrequencyPacket.TYPE,
                DeleteFrequencyPacket.STREAM_CODEC,
                DeleteFrequencyPacket::handle);
        registrar.playToServer(
                EditFrequencyPacket.TYPE,
                EditFrequencyPacket.STREAM_CODEC,
                EditFrequencyPacket::handle);
        registrar.playToServer(
                SelectFrequencyPacket.TYPE,
                SelectFrequencyPacket.STREAM_CODEC,
                SelectFrequencyPacket::handle);
        registrar.playToServer(
                ChangeMemberPacket.TYPE,
                ChangeMemberPacket.STREAM_CODEC,
                ChangeMemberPacket::handle);
        registrar.playToServer(
                WirelessConnectorUsePacket.TYPE,
                WirelessConnectorUsePacket.STREAM_CODEC,
                WirelessConnectorUsePacket::handle);

        // S→C
        registrar.playToClient(
                EasterEggPacket.TYPE,
                EasterEggPacket.STREAM_CODEC,
                EasterEggPacket::handle);
        registrar.playToClient(
                SyncFrequencyListPacket.TYPE,
                SyncFrequencyListPacket.STREAM_CODEC,
                SyncFrequencyListPacket::handle);
        registrar.playToClient(
                SyncFrequencyDetailPacket.TYPE,
                SyncFrequencyDetailPacket.STREAM_CODEC,
                SyncFrequencyDetailPacket::handle);
        registrar.playToClient(
                UpdateFrequencyBasicPacket.TYPE,
                UpdateFrequencyBasicPacket.STREAM_CODEC,
                UpdateFrequencyBasicPacket::handle);
        registrar.playToClient(
                FrequencyResponsePacket.TYPE,
                FrequencyResponsePacket.STREAM_CODEC,
                FrequencyResponsePacket::handle);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, path);
    }
}
