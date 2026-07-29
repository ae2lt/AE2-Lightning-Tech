package com.moakiee.ae2lt.grid.wirelesslink;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persisted description of one frequency-linked physical cluster. The cluster
 * may use several transient GridConnection entrances to satisfy channel demand;
 * those runtime paths are intentionally not part of this value.
 */
public record WirelessLink(
        UUID linkId,
        int frequencyId,
        String dimensionId,
        long posLong,
        WirelessLinkMode mode,
        String sideName,
        String blockId,
        String blockEntityTypeId,
        String partId,
        String partClassName,
        UUID ownerUuid,
        WirelessLinkState state,
        boolean enabled,
        long createdTime,
        long updatedTime,
        long firstInvalidTime,
        long lastCheckedTime,
        int invalidCheckCount
) {
    public WirelessLink {
        Objects.requireNonNull(linkId, "linkId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(blockEntityTypeId, "blockEntityTypeId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(state, "state");
        sideName = sideName == null ? "" : sideName;
        partId = partId == null ? "" : partId;
        partClassName = partClassName == null ? "" : partClassName;
    }

    public boolean canBeRemovedBy(UUID actorUuid, boolean actorIsFrequencyManager) {
        return ownerUuid.equals(actorUuid) || actorIsFrequencyManager;
    }

    public boolean ownerCanUseFrequency(boolean ownerCanUseFrequency) {
        return ownerCanUseFrequency;
    }

    public static WirelessLink createDevice(
            UUID linkId,
            int frequencyId,
            String dimensionId,
            long posLong,
            String blockId,
            String blockEntityTypeId,
            UUID ownerUuid,
            long now) {
        return new WirelessLink(
                linkId,
                frequencyId,
                dimensionId,
                posLong,
                WirelessLinkMode.DEVICE,
                "",
                blockId,
                blockEntityTypeId,
                "",
                "",
                ownerUuid,
                WirelessLinkState.DISCONNECTED,
                true,
                now,
                now,
                0L,
                0L,
                0);
    }

    public static WirelessLink createPart(
            UUID linkId,
            int frequencyId,
            String dimensionId,
            long posLong,
            String sideName,
            String cableBlockId,
            String cableBlockEntityTypeId,
            String partId,
            String partClassName,
            UUID ownerUuid,
            long now) {
        return new WirelessLink(
                linkId,
                frequencyId,
                dimensionId,
                posLong,
                WirelessLinkMode.PART,
                sideName,
                cableBlockId,
                cableBlockEntityTypeId,
                partId,
                partClassName,
                ownerUuid,
                WirelessLinkState.DISCONNECTED,
                true,
                now,
                now,
                0L,
                0L,
                0);
    }

    public WirelessLink withState(WirelessLinkState newState, long now) {
        return new WirelessLink(
                linkId,
                frequencyId,
                dimensionId,
                posLong,
                mode,
                sideName,
                blockId,
                blockEntityTypeId,
                partId,
                partClassName,
                ownerUuid,
                newState,
                enabled,
                createdTime,
                now,
                firstInvalidTime,
                lastCheckedTime,
                invalidCheckCount);
    }

    public WirelessLink withEnabled(boolean newEnabled, long now) {
        return new WirelessLink(
                linkId,
                frequencyId,
                dimensionId,
                posLong,
                mode,
                sideName,
                blockId,
                blockEntityTypeId,
                partId,
                partClassName,
                ownerUuid,
                state,
                newEnabled,
                createdTime,
                now,
                firstInvalidTime,
                lastCheckedTime,
                invalidCheckCount);
    }

    public WirelessLink withInvalidTracking(long firstInvalid, long lastChecked, int checkCount) {
        return new WirelessLink(
                linkId,
                frequencyId,
                dimensionId,
                posLong,
                mode,
                sideName,
                blockId,
                blockEntityTypeId,
                partId,
                partClassName,
                ownerUuid,
                state,
                enabled,
                createdTime,
                updatedTime,
                firstInvalid,
                lastChecked,
                checkCount);
    }

    public WirelessLink clearInvalidTracking(long now) {
        return withInvalidTracking(0L, now, 0);
    }

    public Map<String, String> toPersistentSnapshot() {
        var out = new LinkedHashMap<String, String>();
        out.put("LinkId", linkId.toString());
        out.put("FrequencyId", Integer.toString(frequencyId));
        out.put("Dimension", dimensionId);
        out.put("Pos", Long.toString(posLong));
        out.put("Mode", mode.name());
        out.put("Side", sideName);
        out.put("BlockId", blockId);
        out.put("BlockEntityType", blockEntityTypeId);
        out.put("PartId", partId);
        out.put("PartClass", partClassName);
        out.put("OwnerUuid", ownerUuid.toString());
        out.put("State", state.name());
        out.put("Enabled", Boolean.toString(enabled));
        out.put("CreatedTime", Long.toString(createdTime));
        out.put("UpdatedTime", Long.toString(updatedTime));
        out.put("FirstInvalidTime", Long.toString(firstInvalidTime));
        out.put("LastCheckedTime", Long.toString(lastCheckedTime));
        out.put("InvalidCheckCount", Integer.toString(invalidCheckCount));
        return out;
    }

    public static Optional<WirelessLink> fromPersistentSnapshot(Map<String, String> snapshot) {
        try {
            return Optional.of(new WirelessLink(
                    UUID.fromString(snapshot.get("LinkId")),
                    Integer.parseInt(snapshot.get("FrequencyId")),
                    snapshot.get("Dimension"),
                    Long.parseLong(snapshot.get("Pos")),
                    WirelessLinkMode.valueOf(snapshot.getOrDefault("Mode", WirelessLinkMode.DEVICE.name())),
                    snapshot.getOrDefault("Side", ""),
                    snapshot.getOrDefault("BlockId", ""),
                    snapshot.getOrDefault("BlockEntityType", ""),
                    snapshot.getOrDefault("PartId", ""),
                    snapshot.getOrDefault("PartClass", ""),
                    UUID.fromString(snapshot.get("OwnerUuid")),
                    WirelessLinkState.valueOf(snapshot.getOrDefault("State", WirelessLinkState.DISCONNECTED.name())),
                    Boolean.parseBoolean(snapshot.getOrDefault("Enabled", "true")),
                    Long.parseLong(snapshot.getOrDefault("CreatedTime", "0")),
                    Long.parseLong(snapshot.getOrDefault("UpdatedTime", "0")),
                    Long.parseLong(snapshot.getOrDefault("FirstInvalidTime", "0")),
                    Long.parseLong(snapshot.getOrDefault("LastCheckedTime", "0")),
                    Integer.parseInt(snapshot.getOrDefault("InvalidCheckCount", "0"))));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
