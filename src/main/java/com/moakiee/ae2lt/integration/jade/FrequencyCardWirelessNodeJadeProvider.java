package com.moakiee.ae2lt.integration.jade;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.api.frequency.FrequencyBindingHost;
import com.moakiee.ae2lt.grid.FrequencyDisplayName;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.grid.wirelesslink.WirelessLinkRegistry;
import com.moakiee.ae2lt.grid.wirelesslink.WirelessLinkState;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Shows frequency-card virtual entrances on regular grid blocks and on the
 * precisely targeted part of an AE2 cable bus.
 */
public final class FrequencyCardWirelessNodeJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final ResourceLocation UID =
            new ResourceLocation(AE2LightningTech.MODID, "frequency_card_wireless_node");
    private static final String TAG_LINK = "AE2LTFrequencyCardWirelessNode";
    private static final String TAG_STATE = "State";
    private static final String TAG_FREQUENCY_NAME = "FrequencyName";

    private enum DisplayState {
        CONNECTED,
        PENDING,
        CONFLICT,
        INACTIVE
    }

    private record DisplayData(DisplayState state, List<Integer> frequencyIds) {
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) {
            return;
        }

        DisplayData displayData = inspectNativeHost(accessor);
        if (displayData == null) {
            var hitResult = accessor.getHitResult();
            var inspection = WirelessLinkRegistry.get(level.getServer()).inspectTarget(
                    level,
                    accessor.getPosition(),
                    accessor.getSide(),
                    hitResult == null ? null : hitResult.getLocation());
            displayData = displayData(inspection);
        }
        if (displayData == null) {
            return;
        }

        var linkData = new CompoundTag();
        linkData.putString(TAG_STATE, displayData.state().name());
        linkData.putString(TAG_FREQUENCY_NAME, resolveFrequencyNames(displayData.frequencyIds()));
        data.put(TAG_LINK, linkData);
    }

    private static DisplayData inspectNativeHost(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof FrequencyBindingHost host)
                || host.getFrequencyId() <= 0) {
            return null;
        }
        return new DisplayData(
                host.isFrequencyConnected() ? DisplayState.CONNECTED : DisplayState.PENDING,
                List.of(host.getFrequencyId()));
    }

    private static DisplayData displayData(WirelessLinkRegistry.TargetLinkInspection inspection) {
        if (!inspection.isPresent()) {
            return null;
        }

        boolean connected = inspection.liveVirtualEntrance();
        boolean pending = false;
        boolean conflict = false;
        var frequencyIds = new LinkedHashSet<Integer>();
        for (var link : inspection.links()) {
            frequencyIds.add(link.frequencyId());
            connected |= link.state() == WirelessLinkState.CONNECTED;
            pending |= isPending(link.state());
            conflict |= link.state() == WirelessLinkState.CLUSTER_FREQUENCY_CONFLICT;
        }
        DisplayState state = conflict
                ? DisplayState.CONFLICT
                : connected
                        ? DisplayState.CONNECTED
                        : pending ? DisplayState.PENDING : DisplayState.INACTIVE;
        return new DisplayData(state, List.copyOf(frequencyIds));
    }

    private static boolean isPending(WirelessLinkState state) {
        return state == WirelessLinkState.PENDING_TARGET_CHUNK
                || state == WirelessLinkState.PENDING_TRANSMITTER
                || state == WirelessLinkState.TARGET_NOT_READY;
    }

    private static String resolveFrequencyNames(List<Integer> frequencyIds) {
        var manager = WirelessFrequencyManager.get();
        var names = new java.util.ArrayList<String>(frequencyIds.size());
        for (int frequencyId : frequencyIds) {
            var frequency = manager == null ? null : manager.getFrequency(frequencyId);
            String name = frequency == null ? "" : frequency.getName();
            names.add(FrequencyDisplayName.of(frequencyId, name));
        }
        return String.join(" / ", names);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(TAG_LINK, Tag.TAG_COMPOUND)) {
            return;
        }

        String stateName = serverData.getCompound(TAG_LINK).getString(TAG_STATE);
        DisplayState state;
        try {
            state = DisplayState.valueOf(stateName);
        } catch (IllegalArgumentException ignored) {
            return;
        }

        String frequencyName = serverData.getCompound(TAG_LINK).getString(TAG_FREQUENCY_NAME);

        Component status = switch (state) {
            case CONNECTED -> Component.translatable("jade.ae2lt.frequency_card_wireless_node.state.connected")
                    .withStyle(ChatFormatting.GREEN);
            case PENDING -> Component.translatable("jade.ae2lt.frequency_card_wireless_node.state.pending")
                    .withStyle(ChatFormatting.YELLOW);
            case CONFLICT -> Component.translatable("jade.ae2lt.frequency_card_wireless_node.state.conflict")
                    .withStyle(ChatFormatting.RED);
            case INACTIVE -> Component.translatable("jade.ae2lt.frequency_card_wireless_node.state.inactive")
                    .withStyle(ChatFormatting.GRAY);
        };
        tooltip.add(Component.translatable("jade.ae2lt.frequency_card_wireless_node", frequencyName, status)
                .withStyle(ChatFormatting.AQUA));
    }
}
