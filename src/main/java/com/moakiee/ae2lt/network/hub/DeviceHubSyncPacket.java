package com.moakiee.ae2lt.network.hub;

import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.List;

import com.moakiee.ae2lt.client.ClientNetworkPacketHandlers;
import com.moakiee.ae2lt.item.railgun.RailgunExecutionMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

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
        RailgunExecutionMode executionMode,
        boolean chargedSplash,
        List<String> moduleNameKeys,
        List<Integer> moduleCounts,
        List<Boolean> moduleEnabled,
        int selectedModuleIndex,
        List<String> moduleConfigKeys,
        List<String> moduleConfigLabels,
        List<String> moduleConfigValues,
        List<Boolean> moduleConfigEditable
) {
    public static DeviceHubSyncPacket decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        String deviceName = buf.readUtf(256);
        boolean hasCore = buf.readBoolean();
        boolean powered = buf.readBoolean();
        boolean terrainDestruction = buf.readBoolean();
        boolean pvp = buf.readBoolean();
        boolean soundEnabled = buf.readBoolean();
        boolean chainDamage = buf.readBoolean();
        RailgunExecutionMode executionMode = buf.readEnum(RailgunExecutionMode.class);
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
                executionMode,
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

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeUtf(deviceName, 256);
        buf.writeBoolean(hasCore);
        buf.writeBoolean(powered);
        buf.writeBoolean(terrainDestruction);
        buf.writeBoolean(pvp);
        buf.writeBoolean(soundEnabled);
        buf.writeBoolean(chainDamage);
        buf.writeEnum(executionMode);
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

    public static void handle(DeviceHubSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientNetworkPacketHandlers.handleDeviceHubSync(pkt)));
        ctx.setPacketHandled(true);
    }
}
