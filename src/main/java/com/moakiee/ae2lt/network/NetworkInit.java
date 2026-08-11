package com.moakiee.ae2lt.network;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.network.hub.DeviceHubActionPacket;
import com.moakiee.ae2lt.network.hub.DeviceHubSyncPacket;
import com.moakiee.ae2lt.network.hub.OpenDeviceHubPacket;
import com.moakiee.ae2lt.network.railgun.RailgunBeamChainFxPacket;
import com.moakiee.ae2lt.network.railgun.RailgunBeamTogglePacket;
import com.moakiee.ae2lt.network.railgun.RailgunBeamUpdatePacket;
import com.moakiee.ae2lt.network.railgun.RailgunFirePacket;
import com.moakiee.ae2lt.network.railgun.RailgunRecoilFxPacket;
import com.moakiee.ae2lt.network.tianshu.MaintenanceEditorSyncPacket;
import com.moakiee.ae2lt.network.tianshu.MaintenanceSummarySyncPacket;
import com.moakiee.ae2lt.network.tianshu.ClosedLoopResultPagePacket;
import com.moakiee.ae2lt.network.tianshu.OpenMaintenanceEditorPacket;
import com.moakiee.ae2lt.network.tianshu.RequestClosedLoopResultPagePacket;
import com.moakiee.ae2lt.network.tianshu.RequestUploadTargetsPacket;
import com.moakiee.ae2lt.network.tianshu.SaveGlobalReservePacket;
import com.moakiee.ae2lt.network.tianshu.SaveMaintenanceRulePacket;
import com.moakiee.ae2lt.network.tianshu.UploadPatternToTargetPacket;
import com.moakiee.ae2lt.network.tianshu.UploadTargetsSyncPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class NetworkInit {
    // Version 2 adds paged closed-loop result request/response messages.
    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextPacketId;
    private static boolean registered;

    private NetworkInit() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        CHANNEL.registerMessage(
                nextPacketId++,
                WirelessConnectorUsePacket.class,
                WirelessConnectorUsePacket::encode,
                WirelessConnectorUsePacket::decode,
                WirelessConnectorUsePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // frequency card: C→S (write-style codec)
        CHANNEL.registerMessage(
                nextPacketId++,
                FrequencyCardUsePacket.class,
                (pkt, buf) -> pkt.write(buf),
                FrequencyCardUsePacket::decode,
                FrequencyCardUsePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                nextPacketId++,
                ToggleFrequencyCardAutoConnectPacket.class,
                (pkt, buf) -> pkt.write(buf),
                ToggleFrequencyCardAutoConnectPacket::decode,
                ToggleFrequencyCardAutoConnectPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // controller UI: C→S
        CHANNEL.registerMessage(
                nextPacketId++,
                MatrixControllerActionPacket.class,
                MatrixControllerActionPacket::encode,
                MatrixControllerActionPacket::decode,
                MatrixControllerActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                nextPacketId++,
                TianshuControllerActionPacket.class,
                TianshuControllerActionPacket::encode,
                TianshuControllerActionPacket::decode,
                TianshuControllerActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenFrequencyMenuPacket.class,
                OpenFrequencyMenuPacket::encode,
                OpenFrequencyMenuPacket::decode,
                OpenFrequencyMenuPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // frequency system: C→S
        CHANNEL.registerMessage(
                nextPacketId++,
                CreateFrequencyPacket.class,
                CreateFrequencyPacket::encode,
                CreateFrequencyPacket::decode,
                CreateFrequencyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                nextPacketId++,
                DeleteFrequencyPacket.class,
                DeleteFrequencyPacket::encode,
                DeleteFrequencyPacket::decode,
                DeleteFrequencyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                nextPacketId++,
                EditFrequencyPacket.class,
                EditFrequencyPacket::encode,
                EditFrequencyPacket::decode,
                EditFrequencyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                nextPacketId++,
                SelectFrequencyPacket.class,
                SelectFrequencyPacket::encode,
                SelectFrequencyPacket::decode,
                SelectFrequencyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                nextPacketId++,
                ChangeMemberPacket.class,
                ChangeMemberPacket::encode,
                ChangeMemberPacket::decode,
                ChangeMemberPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // S→C
        CHANNEL.registerMessage(
                nextPacketId++,
                EasterEggPacket.class,
                EasterEggPacket::encode,
                EasterEggPacket::decode,
                EasterEggPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                SyncFrequencyListPacket.class,
                SyncFrequencyListPacket::encode,
                SyncFrequencyListPacket::decode,
                SyncFrequencyListPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                SyncFrequencyDetailPacket.class,
                SyncFrequencyDetailPacket::encode,
                SyncFrequencyDetailPacket::decode,
                SyncFrequencyDetailPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                UpdateFrequencyBasicPacket.class,
                UpdateFrequencyBasicPacket::encode,
                UpdateFrequencyBasicPacket::decode,
                UpdateFrequencyBasicPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                FrequencyResponsePacket.class,
                FrequencyResponsePacket::encode,
                FrequencyResponsePacket::decode,
                FrequencyResponsePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                PigmeeAssemblerAnimationPacket.class,
                (pkt, buf) -> pkt.write(buf),
                PigmeeAssemblerAnimationPacket::decode,
                PigmeeAssemblerAnimationPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                RitualItemBurstPacket.class,
                (pkt, buf) -> pkt.write(buf),
                RitualItemBurstPacket::decode,
                RitualItemBurstPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                CelestweaveSubmoduleActivePacket.class,
                (pkt, buf) -> pkt.write(buf),
                CelestweaveSubmoduleActivePacket::decode,
                CelestweaveSubmoduleActivePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                FlightInertiaSyncPacket.class,
                (pkt, buf) -> pkt.write(buf),
                FlightInertiaSyncPacket::decode,
                FlightInertiaSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                PhaseLockProtectionSyncPacket.class,
                (pkt, buf) -> pkt.write(buf),
                PhaseLockProtectionSyncPacket::decode,
                PhaseLockProtectionSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                ShieldHitFeedbackSuppressionPacket.class,
                (pkt, buf) -> pkt.write(buf),
                ShieldHitFeedbackSuppressionPacket::decode,
                ShieldHitFeedbackSuppressionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // railgun: C→S
        CHANNEL.registerMessage(
                nextPacketId++,
                RailgunBeamTogglePacket.class,
                (pkt, buf) -> pkt.write(buf),
                RailgunBeamTogglePacket::decode,
                RailgunBeamTogglePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // railgun: S→C
        CHANNEL.registerMessage(
                nextPacketId++,
                RailgunFirePacket.class,
                (pkt, buf) -> pkt.write(buf),
                RailgunFirePacket::decode,
                RailgunFirePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                RailgunBeamUpdatePacket.class,
                (pkt, buf) -> pkt.write(buf),
                RailgunBeamUpdatePacket::decode,
                RailgunBeamUpdatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                RailgunBeamChainFxPacket.class,
                (pkt, buf) -> pkt.write(buf),
                RailgunBeamChainFxPacket::decode,
                RailgunBeamChainFxPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                RailgunRecoilFxPacket.class,
                (pkt, buf) -> pkt.write(buf),
                RailgunRecoilFxPacket::decode,
                RailgunRecoilFxPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // dash (Celestweave legs module): C→S
        CHANNEL.registerMessage(
                nextPacketId++,
                DashPacket.class,
                (pkt, buf) -> pkt.write(buf),
                DashPacket::decode,
                DashPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        // Device Hub: C→S
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenDeviceHubPacket.class,
                (pkt, buf) -> pkt.write(buf),
                OpenDeviceHubPacket::decode,
                OpenDeviceHubPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                nextPacketId++,
                DeviceHubActionPacket.class,
                (pkt, buf) -> pkt.write(buf),
                DeviceHubActionPacket::decode,
                DeviceHubActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // Device Hub: S→C
        CHANNEL.registerMessage(
                nextPacketId++,
                DeviceHubSyncPacket.class,
                (pkt, buf) -> pkt.write(buf),
                DeviceHubSyncPacket::decode,
                DeviceHubSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Tianshu pattern terminal maintenance/upload: C→S
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenMaintenanceEditorPacket.class,
                (pkt, buf) -> pkt.write(buf),
                OpenMaintenanceEditorPacket::decode,
                OpenMaintenanceEditorPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                nextPacketId++,
                SaveMaintenanceRulePacket.class,
                (pkt, buf) -> pkt.write(buf),
                SaveMaintenanceRulePacket::decode,
                SaveMaintenanceRulePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                nextPacketId++,
                SaveGlobalReservePacket.class,
                (pkt, buf) -> pkt.write(buf),
                SaveGlobalReservePacket::decode,
                SaveGlobalReservePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                nextPacketId++,
                RequestUploadTargetsPacket.class,
                (pkt, buf) -> pkt.write(buf),
                RequestUploadTargetsPacket::decode,
                RequestUploadTargetsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                nextPacketId++,
                RequestClosedLoopResultPagePacket.class,
                (pkt, buf) -> pkt.write(buf),
                RequestClosedLoopResultPagePacket::decode,
                RequestClosedLoopResultPagePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                nextPacketId++,
                UploadPatternToTargetPacket.class,
                (pkt, buf) -> pkt.write(buf),
                UploadPatternToTargetPacket::decode,
                UploadPatternToTargetPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // Tianshu pattern terminal: S→C
        CHANNEL.registerMessage(
                nextPacketId++,
                MaintenanceEditorSyncPacket.class,
                (pkt, buf) -> pkt.write(buf),
                MaintenanceEditorSyncPacket::decode,
                MaintenanceEditorSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                MaintenanceSummarySyncPacket.class,
                (pkt, buf) -> pkt.write(buf),
                MaintenanceSummarySyncPacket::decode,
                MaintenanceSummarySyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                UploadTargetsSyncPacket.class,
                (pkt, buf) -> pkt.write(buf),
                UploadTargetsSyncPacket::decode,
                UploadTargetsSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                nextPacketId++,
                ClosedLoopResultPagePacket.class,
                (pkt, buf) -> pkt.write(buf),
                ClosedLoopResultPagePacket::decode,
                ClosedLoopResultPagePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(AE2LightningTech.MODID, path);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.sendTo(message, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendToPlayersNear(
            ServerLevel level, double x, double y, double z, double radius, Object message) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                x, y, z, radius, level.dimension())), message);
    }
}
