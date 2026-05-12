package com.moakiee.ae2lt.device.capability;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;

/**
 * Capability records emitted by device modules and consumed by device services.
 * Mid-grained: ~10 records + 1 escape hatch. Each device only reads the variants
 * it understands; unrecognized variants are ignored.
 */
public sealed interface DeviceCapability {

    // --- shared (read by multiple device kinds) ---

    /** Railgun: -charge interval. Armor: +movement speed. */
    record AccelerationFactor(double factor) implements DeviceCapability {}

    /** Multiplier on energy consumption + optional flat capacity addition. */
    record EnergyTuning(double consumeMul, long capacityAdd) implements DeviceCapability {}

    /** Overload budget tuning (armor live, railgun reserved). */
    record OverloadTuning(int budgetCap, double dissipationRate) implements DeviceCapability {}

    // --- railgun only ---

    /** Lightning chain segments / forks / hard-cap bonus, packed. */
    record ChainTuning(int extraSegments, int extraForks, int hardCapBonus) implements DeviceCapability {}

    /** EMP pulse radius + damage multiplier. */
    record PulseTuning(double radiusMul, double dmgMul) implements DeviceCapability {}

    /** Overload execution: cumulative damage tracker + forced kill sequence. */
    record OverloadExecutionTuning(double decayRate, int decayDelayTicks, int maxTrackedTargets) implements DeviceCapability {}

    // --- armor only ---

    /** Damage mitigation: reflect ratio + post-armor resist ratio. */
    record DamageMitigation(double reflectPct, double resistPct) implements DeviceCapability {}

    /** Dash impulse + cooldown ticks. */
    record DashEffect(double impulse, int cooldownTicks) implements DeviceCapability {}

    /** Flight mode. */
    record FlightMode(FlightKind kind) implements DeviceCapability {}

    /** Generic status effect grant (night vision / water breathing / ...). */
    record StatusEffectGrant(Holder<MobEffect> effect, int amplifier) implements DeviceCapability {}

    // --- escape hatch ---

    /** Future-proof channel; routed by id. */
    record DeviceSpecific(String id, CompoundTag data) implements DeviceCapability {}
}
