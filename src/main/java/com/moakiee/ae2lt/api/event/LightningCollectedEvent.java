package com.moakiee.ae2lt.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.Event;

import com.moakiee.ae2lt.api.lightning.LightningTier;

/**
 * Fired on the {@code NeoForge.EVENT_BUS} when a Lightning Collector has rolled the
 * amount of lightning to capture from a strike, but before it has been inserted
 * into the AE2 grid.
 *
 * <h2>Modifying capture</h2>
 * <ul>
 *   <li>Call {@link #setAmount(long)} to override how much energy is inserted.
 *       Negative values are clamped to 0.</li>
 *   <li>Call {@link #setCanceled(boolean) setCanceled(true)} to abort the entire
 *       capture. The collector will not insert anything, will not enter cooldown,
 *       and will not advance crystal cultivation.</li>
 * </ul>
 *
 * <h2>Notes for addon authors</h2>
 * <ul>
 *   <li>This event fires only when this mod's own
 *       {@code LightningCollectorBlockEntity#captureLightning} runs. If another mod
 *       intercepts the lightning entity at higher priority and bypasses the
 *       collector, this event will not be posted.</li>
 *   <li>One natural strike may fire this event for at most one collector. There is
 *       no "ehv + hv" split: each event represents exactly one tier and one amount.</li>
 *   <li>The collector position is the actual block position of the
 *       {@code LightningCollectorBlockEntity}, not the lightning bolt's position.</li>
 * </ul>
 */
public class LightningCollectedEvent extends Event implements ICancellableEvent {

    private final ServerLevel level;
    private final BlockPos collectorPos;
    private final LightningTier tier;
    private final boolean naturalWeather;
    private long amount;

    public LightningCollectedEvent(
            ServerLevel level,
            BlockPos collectorPos,
            LightningTier tier,
            long amount,
            boolean naturalWeather) {
        this.level = level;
        this.collectorPos = collectorPos.immutable();
        this.tier = tier;
        this.amount = Math.max(0L, amount);
        this.naturalWeather = naturalWeather;
    }

    /** The level the collector is in. */
    public ServerLevel getLevel() {
        return level;
    }

    /** Immutable block position of the collector. */
    public BlockPos getCollectorPos() {
        return collectorPos;
    }

    /** Tier of the rolled output. Determined by the strike type, not configurable. */
    public LightningTier getTier() {
        return tier;
    }

    /** Whether the strike came from a natural thunderstorm (vs. a tagged trigger). */
    public boolean isNaturalWeather() {
        return naturalWeather;
    }

    /** Current amount that will be inserted, after any prior listener edits. */
    public long getAmount() {
        return amount;
    }

    /** Override the amount. Negative values are clamped to 0. */
    public void setAmount(long amount) {
        this.amount = Math.max(0L, amount);
    }
}
