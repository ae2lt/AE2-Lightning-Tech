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
import com.moakiee.ae2lt.network.tianshu.OpenMaintenanceEditorPacket;
import com.moakiee.ae2lt.network.tianshu.MaintenanceEditorSyncPacket;
import com.moakiee.ae2lt.network.tianshu.SaveMaintenanceRulePacket;
import com.moakiee.ae2lt.network.tianshu.MaintenanceSummarySyncPacket;
import com.moakiee.ae2lt.network.tianshu.SaveGlobalReservePacket;
import com.moakiee.ae2lt.network.tianshu.RequestUploadTargetsPacket;
import com.moakiee.ae2lt.network.tianshu.UploadPatternToTargetPacket;
import com.moakiee.ae2lt.network.tianshu.UploadTargetsSyncPacket;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class NetworkInit {
    private NetworkInit() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(AE2LightningTech.MODID);

        registrar.playToServer(
                WirelessConnectorUsePacket.TYPE,
                WirelessConnectorUsePacket.STREAM_CODEC,
                WirelessConnectorUsePacket::handle);
        registrar.playToServer(
                FrequencyCardUsePacket.TYPE,
                FrequencyCardUsePacket.STREAM_CODEC,
                FrequencyCardUsePacket::handle);
        registrar.playToServer(
                ToggleFrequencyCardAutoConnectPacket.TYPE,
                ToggleFrequencyCardAutoConnectPacket.STREAM_CODEC,
                ToggleFrequencyCardAutoConnectPacket::handle);
        registrar.playToServer(
                OpenFrequencyMenuPacket.TYPE,
                OpenFrequencyMenuPacket.STREAM_CODEC,
                OpenFrequencyMenuPacket::handle);
        registrar.playToServer(
                MatrixControllerActionPacket.TYPE,
                MatrixControllerActionPacket.STREAM_CODEC,
                MatrixControllerActionPacket::handle);
        registrar.playToServer(
                TianshuControllerActionPacket.TYPE,
                TianshuControllerActionPacket.STREAM_CODEC,
                TianshuControllerActionPacket::handle);

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
        registrar.playToClient(
                CelestweaveSubmoduleActivePacket.TYPE,
                CelestweaveSubmoduleActivePacket.STREAM_CODEC,
                CelestweaveSubmoduleActivePacket::handle);
        registrar.playToClient(
                FlightInertiaSyncPacket.TYPE,
                FlightInertiaSyncPacket.STREAM_CODEC,
                FlightInertiaSyncPacket::handle);
        registrar.playToClient(
                ShieldHitFeedbackSuppressionPacket.TYPE,
                ShieldHitFeedbackSuppressionPacket.STREAM_CODEC,
                ShieldHitFeedbackSuppressionPacket::handle);

        // Railgun: C→S (RailgunOpenGuiPacket removed — replaced by OpenDeviceHubPacket)
        registrar.playToServer(
                RailgunBeamTogglePacket.TYPE,
                RailgunBeamTogglePacket.STREAM_CODEC,
                RailgunBeamTogglePacket::handle);
        // Railgun: S→C
        registrar.playToClient(
                RailgunFirePacket.TYPE,
                RailgunFirePacket.STREAM_CODEC,
                RailgunFirePacket::handle);
        registrar.playToClient(
                RailgunBeamUpdatePacket.TYPE,
                RailgunBeamUpdatePacket.STREAM_CODEC,
                RailgunBeamUpdatePacket::handle);
        registrar.playToClient(
                RailgunBeamChainFxPacket.TYPE,
                RailgunBeamChainFxPacket.STREAM_CODEC,
                RailgunBeamChainFxPacket::handle);
        registrar.playToClient(
                RailgunRecoilFxPacket.TYPE,
                RailgunRecoilFxPacket.STREAM_CODEC,
                RailgunRecoilFxPacket::handle);

        registrar.playToServer(
                DashPacket.TYPE,
                DashPacket.STREAM_CODEC,
                DashPacket::handle);

        // Device Hub: C→S
        registrar.playToServer(
                OpenDeviceHubPacket.TYPE,
                OpenDeviceHubPacket.STREAM_CODEC,
                OpenDeviceHubPacket::handle);
        registrar.playToServer(
                DeviceHubActionPacket.TYPE,
                DeviceHubActionPacket.STREAM_CODEC,
                DeviceHubActionPacket::handle);
        // Device Hub: S→C
        registrar.playToClient(
                DeviceHubSyncPacket.TYPE,
                DeviceHubSyncPacket.STREAM_CODEC,
                DeviceHubSyncPacket::handle);

        registrar.playToServer(
                OpenMaintenanceEditorPacket.TYPE,
                OpenMaintenanceEditorPacket.STREAM_CODEC,
                OpenMaintenanceEditorPacket::handle);
        registrar.playToServer(
                SaveMaintenanceRulePacket.TYPE,
                SaveMaintenanceRulePacket.STREAM_CODEC,
                SaveMaintenanceRulePacket::handle);
        registrar.playToClient(
                MaintenanceEditorSyncPacket.TYPE,
                MaintenanceEditorSyncPacket.STREAM_CODEC,
                MaintenanceEditorSyncPacket::handle);
        registrar.playToClient(
                MaintenanceSummarySyncPacket.TYPE,
                MaintenanceSummarySyncPacket.STREAM_CODEC,
                MaintenanceSummarySyncPacket::handle);
        registrar.playToServer(
                SaveGlobalReservePacket.TYPE,
                SaveGlobalReservePacket.STREAM_CODEC,
                SaveGlobalReservePacket::handle);
        registrar.playToServer(
                RequestUploadTargetsPacket.TYPE,
                RequestUploadTargetsPacket.STREAM_CODEC,
                RequestUploadTargetsPacket::handle);
        registrar.playToServer(
                UploadPatternToTargetPacket.TYPE,
                UploadPatternToTargetPacket.STREAM_CODEC,
                UploadPatternToTargetPacket::handle);
        registrar.playToClient(
                UploadTargetsSyncPacket.TYPE,
                UploadTargetsSyncPacket.STREAM_CODEC,
                UploadTargetsSyncPacket::handle);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, path);
    }
}
