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
        String boundDim,
        long storedFe,
        long capacityFe,
        int dynamicLoad,
        int overloadCap,
        int lockState,
        int lockValue,
        String debtReason,
        boolean hasCore,
        boolean powered,
        boolean gridReachable,
        boolean appFluxOnline,
        int moduleSlotCount,
        boolean terrainDestruction,
        boolean pvpLock,
        boolean terrainDestructionAllowed,
        List<String> recentLoadIds,
        List<Integer> recentLoadAmounts,
        List<String> moduleIds,
        List<String> moduleNameKeys,
        List<Integer> moduleCounts,
        List<Boolean> moduleEnabled,
        List<Boolean> moduleActive,
        List<Integer> moduleLoads,
        List<Integer> moduleCooldowns,
        int selectedModuleIndex,
        List<String> moduleConfigKeys,
        List<String> moduleConfigLabels,
        List<String> moduleConfigValues,
        List<String> moduleConfigKinds,
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
        String boundDim = buf.readUtf(256);
        long storedFe = buf.readLong();
        long capacityFe = buf.readLong();
        int dynamicLoad = buf.readVarInt();
        int overloadCap = buf.readVarInt();
        int lockState = buf.readVarInt();
        int lockValue = buf.readVarInt();
        String debtReason = buf.readUtf(64);
        boolean hasCore = buf.readBoolean();
        boolean powered = buf.readBoolean();
        boolean gridReachable = buf.readBoolean();
        boolean appFluxOnline = buf.readBoolean();
        int moduleSlotCount = buf.readVarInt();
        boolean terrainDestruction = buf.readBoolean();
        boolean pvpLock = buf.readBoolean();
        boolean terrainDestructionAllowed = buf.readBoolean();
        int recentCount = buf.readVarInt();
        List<String> recentLoadIds = new ArrayList<>(recentCount);
        List<Integer> recentLoadAmounts = new ArrayList<>(recentCount);
        for (int i = 0; i < recentCount; i++) {
            recentLoadIds.add(buf.readUtf(256));
            recentLoadAmounts.add(buf.readVarInt());
        }
        int count = buf.readVarInt();
        List<String> ids = new ArrayList<>(count);
        List<String> nameKeys = new ArrayList<>(count);
        List<Integer> counts = new ArrayList<>(count);
        List<Boolean> enabled = new ArrayList<>(count);
        List<Boolean> active = new ArrayList<>(count);
        List<Integer> loads = new ArrayList<>(count);
        List<Integer> cooldowns = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buf.readUtf(256));
            nameKeys.add(buf.readUtf(256));
            counts.add(buf.readVarInt());
            enabled.add(buf.readBoolean());
            active.add(buf.readBoolean());
            loads.add(buf.readVarInt());
            cooldowns.add(buf.readVarInt());
        }
        int selectedModuleIndex = buf.readVarInt();
        int configCount = buf.readVarInt();
        List<String> moduleConfigKeys = new ArrayList<>(configCount);
        List<String> moduleConfigLabels = new ArrayList<>(configCount);
        List<String> moduleConfigValues = new ArrayList<>(configCount);
        List<String> moduleConfigKinds = new ArrayList<>(configCount);
        List<Boolean> moduleConfigEditable = new ArrayList<>(configCount);
        for (int i = 0; i < configCount; i++) {
            moduleConfigKeys.add(buf.readUtf(128));
            moduleConfigLabels.add(buf.readUtf(256));
            moduleConfigValues.add(buf.readUtf(256));
            moduleConfigKinds.add(buf.readUtf(64));
            moduleConfigEditable.add(buf.readBoolean());
        }
        return new DeviceHubSyncPacket(
                containerId,
                deviceName,
                boundDim,
                storedFe,
                capacityFe,
                dynamicLoad,
                overloadCap,
                lockState,
                lockValue,
                debtReason,
                hasCore,
                powered,
                gridReachable,
                appFluxOnline,
                moduleSlotCount,
                terrainDestruction,
                pvpLock,
                terrainDestructionAllowed,
                recentLoadIds,
                recentLoadAmounts,
                ids,
                nameKeys,
                counts,
                enabled,
                active,
                loads,
                cooldowns,
                selectedModuleIndex,
                moduleConfigKeys,
                moduleConfigLabels,
                moduleConfigValues,
                moduleConfigKinds,
                moduleConfigEditable);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeUtf(deviceName, 256);
        buf.writeUtf(boundDim, 256);
        buf.writeLong(storedFe);
        buf.writeLong(capacityFe);
        buf.writeVarInt(dynamicLoad);
        buf.writeVarInt(overloadCap);
        buf.writeVarInt(lockState);
        buf.writeVarInt(lockValue);
        buf.writeUtf(debtReason, 64);
        buf.writeBoolean(hasCore);
        buf.writeBoolean(powered);
        buf.writeBoolean(gridReachable);
        buf.writeBoolean(appFluxOnline);
        buf.writeVarInt(moduleSlotCount);
        buf.writeBoolean(terrainDestruction);
        buf.writeBoolean(pvpLock);
        buf.writeBoolean(terrainDestructionAllowed);
        int recentCount = Math.min(recentLoadIds.size(), recentLoadAmounts.size());
        buf.writeVarInt(recentCount);
        for (int i = 0; i < recentCount; i++) {
            buf.writeUtf(recentLoadIds.get(i), 256);
            buf.writeVarInt(recentLoadAmounts.get(i));
        }
        int count = Math.min(
                Math.min(Math.min(moduleIds.size(), moduleNameKeys.size()), moduleCounts.size()),
                Math.min(Math.min(Math.min(moduleEnabled.size(), moduleActive.size()), moduleLoads.size()), moduleCooldowns.size()));
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUtf(moduleIds.get(i), 256);
            buf.writeUtf(moduleNameKeys.get(i), 256);
            buf.writeVarInt(moduleCounts.get(i));
            buf.writeBoolean(moduleEnabled.get(i));
            buf.writeBoolean(moduleActive.get(i));
            buf.writeVarInt(moduleLoads.get(i));
            buf.writeVarInt(moduleCooldowns.get(i));
        }
        buf.writeVarInt(selectedModuleIndex);
        int configCount = Math.min(
                Math.min(Math.min(moduleConfigKeys.size(), moduleConfigLabels.size()), moduleConfigValues.size()),
                Math.min(moduleConfigKinds.size(), moduleConfigEditable.size()));
        buf.writeVarInt(configCount);
        for (int i = 0; i < configCount; i++) {
            buf.writeUtf(moduleConfigKeys.get(i), 128);
            buf.writeUtf(moduleConfigLabels.get(i), 256);
            buf.writeUtf(moduleConfigValues.get(i), 256);
            buf.writeUtf(moduleConfigKinds.get(i), 64);
            buf.writeBoolean(moduleConfigEditable.get(i));
        }
    }

    public static void handle(DeviceHubSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof DeviceHubMenu menu
                    && menu.containerId == pkt.containerId()) {
                menu.receiveSync(
                        pkt.deviceName(),
                        pkt.boundDim(),
                        pkt.storedFe(),
                        pkt.capacityFe(),
                        pkt.dynamicLoad(),
                        pkt.overloadCap(),
                        pkt.lockState(),
                        pkt.lockValue(),
                        pkt.debtReason(),
                        pkt.hasCore(),
                        pkt.powered(),
                        pkt.gridReachable(),
                        pkt.appFluxOnline(),
                        pkt.moduleSlotCount(),
                        pkt.terrainDestruction(),
                        pkt.pvpLock(),
                        pkt.terrainDestructionAllowed(),
                        pkt.recentLoadIds(),
                        pkt.recentLoadAmounts(),
                        pkt.moduleIds(),
                        pkt.moduleNameKeys(),
                        pkt.moduleCounts(),
                        pkt.moduleEnabled(),
                        pkt.moduleActive(),
                        pkt.moduleLoads(),
                        pkt.moduleCooldowns(),
                        pkt.selectedModuleIndex(),
                        pkt.moduleConfigKeys(),
                        pkt.moduleConfigLabels(),
                        pkt.moduleConfigValues(),
                        pkt.moduleConfigKinds(),
                        pkt.moduleConfigEditable());
            }
        });
    }
}
