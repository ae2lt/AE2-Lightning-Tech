package com.moakiee.ae2lt.network.hub;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.network.NetworkInit;

/** Server -> Client: sync full hub display state that cannot safely fit in menu data slots. */
public record DeviceHubSyncPacket(
        int containerId,
        String deviceName,
        boolean hasCore,
        boolean powered,
        boolean terrainDestruction,
        boolean pvp,
        boolean soundEnabled,
        boolean chainDamage,
        boolean forceOverloadRemoval,
        boolean chargedSplash,
        List<String> moduleNameKeys,
        List<Integer> moduleCounts,
        List<Boolean> moduleEnabled,
        int selectedModuleIndex,
        List<String> moduleConfigKeys,
        List<String> moduleConfigLabels,
        List<String> moduleConfigValues,
        List<Boolean> moduleConfigEditable
) implements CustomPacketPayload {

    public static final Type<DeviceHubSyncPacket> TYPE =
            new Type<>(NetworkInit.id("device_hub_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeviceHubSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(DeviceHubSyncPacket::write, DeviceHubSyncPacket::decode);

    @Override
    public Type<DeviceHubSyncPacket> type() {
        return TYPE;
    }

    public static DeviceHubSyncPacket decode(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        String deviceName = buf.readUtf(256);
        boolean hasCore = buf.readBoolean();
        boolean powered = buf.readBoolean();
        boolean terrainDestruction = buf.readBoolean();
        boolean pvp = buf.readBoolean();
        boolean soundEnabled = buf.readBoolean();
        boolean chainDamage = buf.readBoolean();
        boolean forceOverloadRemoval = buf.readBoolean();
        boolean chargedSplash = buf.readBoolean();
        int count = buf.readVarInt();
        List<String> nameKeys = new ArrayList<>(count);
        List<Integer> counts = new ArrayList<>(count);
        List<Boolean> enabled = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            nameKeys.add(buf.readUtf(256));
            counts.add(buf.readVarInt());
            enabled.add(buf.readBoolean());
        }
        int selectedModuleIndex = buf.readVarInt();
        int configCount = buf.readVarInt();
        List<String> moduleConfigKeys = new ArrayList<>(configCount);
        List<String> moduleConfigLabels = new ArrayList<>(configCount);
        List<String> moduleConfigValues = new ArrayList<>(configCount);
        List<Boolean> moduleConfigEditable = new ArrayList<>(configCount);
        for (int i = 0; i < configCount; i++) {
            moduleConfigKeys.add(buf.readUtf(128));
            moduleConfigLabels.add(buf.readUtf(256));
            moduleConfigValues.add(buf.readUtf(256));
            moduleConfigEditable.add(buf.readBoolean());
        }
        return new DeviceHubSyncPacket(
                containerId,
                deviceName,
                hasCore,
                powered,
                terrainDestruction,
                pvp,
                soundEnabled,
                chainDamage,
                forceOverloadRemoval,
                chargedSplash,
                nameKeys,
                counts,
                enabled,
                selectedModuleIndex,
                moduleConfigKeys,
                moduleConfigLabels,
                moduleConfigValues,
                moduleConfigEditable);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeUtf(deviceName, 256);
        buf.writeBoolean(hasCore);
        buf.writeBoolean(powered);
        buf.writeBoolean(terrainDestruction);
        buf.writeBoolean(pvp);
        buf.writeBoolean(soundEnabled);
        buf.writeBoolean(chainDamage);
        buf.writeBoolean(forceOverloadRemoval);
        buf.writeBoolean(chargedSplash);
        int count = Math.min(Math.min(moduleNameKeys.size(), moduleCounts.size()), moduleEnabled.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUtf(moduleNameKeys.get(i), 256);
            buf.writeVarInt(moduleCounts.get(i));
            buf.writeBoolean(moduleEnabled.get(i));
        }
        buf.writeVarInt(selectedModuleIndex);
        int configCount = Math.min(
                Math.min(Math.min(moduleConfigKeys.size(), moduleConfigLabels.size()), moduleConfigValues.size()),
                moduleConfigEditable.size());
        buf.writeVarInt(configCount);
        for (int i = 0; i < configCount; i++) {
            buf.writeUtf(moduleConfigKeys.get(i), 128);
            buf.writeUtf(moduleConfigLabels.get(i), 256);
            buf.writeUtf(moduleConfigValues.get(i), 256);
            buf.writeBoolean(moduleConfigEditable.get(i));
        }
    }

    public static void handle(DeviceHubSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof DeviceHubMenu menu
                    && menu.containerId == pkt.containerId()) {
                menu.receiveSync(
                        pkt.deviceName(),
                        pkt.hasCore(),
                        pkt.powered(),
                        pkt.terrainDestruction(),
                        pkt.pvp(),
                        pkt.soundEnabled(),
                        pkt.chainDamage(),
                        pkt.forceOverloadRemoval(),
                        pkt.chargedSplash(),
                        pkt.moduleNameKeys(),
                        pkt.moduleCounts(),
                        pkt.moduleEnabled(),
                        pkt.selectedModuleIndex(),
                        pkt.moduleConfigKeys(),
                        pkt.moduleConfigLabels(),
                        pkt.moduleConfigValues(),
                        pkt.moduleConfigEditable());
            }
        });
    }
}
