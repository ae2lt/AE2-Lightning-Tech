package com.moakiee.ae2lt.item;

import java.util.Optional;
import java.util.UUID;

/**
 * Pure value object for the frequency card's persisted state. Minecraft NBT
 * adapters live in the item class; this object keeps the card rules testable.
 */
public record OverloadedFrequencyCardData(
        int frequencyId,
        boolean autoConnect,
        Optional<String> boundControllerDimension,
        Optional<Long> boundControllerPos,
        Optional<UUID> ownerUuid
) {
    public static final int NO_FREQUENCY = -1;

    public OverloadedFrequencyCardData {
        boundControllerDimension = boundControllerDimension == null
                ? Optional.empty()
                : boundControllerDimension;
        boundControllerPos = boundControllerPos == null ? Optional.empty() : boundControllerPos;
        ownerUuid = ownerUuid == null ? Optional.empty() : ownerUuid;
    }

    public static OverloadedFrequencyCardData empty() {
        return new OverloadedFrequencyCardData(
                NO_FREQUENCY,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public boolean isBound() {
        return frequencyId > 0;
    }

    public boolean canBeUsedBy(UUID playerUuid) {
        return ownerUuid.isPresent() && ownerUuid.get().equals(playerUuid);
    }

    public OverloadedFrequencyCardData bindFrequency(
            int newFrequencyId,
            String controllerDimension,
            long controllerPos,
            UUID owner) {
        if (newFrequencyId <= 0) {
            return this;
        }
        return new OverloadedFrequencyCardData(
                newFrequencyId,
                autoConnect,
                Optional.ofNullable(controllerDimension),
                Optional.of(controllerPos),
                Optional.ofNullable(owner));
    }

    public OverloadedFrequencyCardData clearFrequency() {
        return new OverloadedFrequencyCardData(
                NO_FREQUENCY,
                autoConnect,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public OverloadedFrequencyCardData withAutoConnect(boolean enabled) {
        return new OverloadedFrequencyCardData(
                frequencyId,
                enabled,
                boundControllerDimension,
                boundControllerPos,
                ownerUuid);
    }

    public OverloadedFrequencyCardData toggleAutoConnect() {
        return withAutoConnect(!autoConnect);
    }
}
