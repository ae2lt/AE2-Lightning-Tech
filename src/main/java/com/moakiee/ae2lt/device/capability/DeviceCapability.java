package com.moakiee.ae2lt.device.capability;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;

import com.moakiee.ae2lt.celestweave.ArmorPart;

/**
 * Capability records emitted by device modules and consumed by device services.
 * Mid-grained: ~10 records + 1 escape hatch. Each device only reads the variants
 * it understands; unrecognized variants are ignored.
 */
public sealed interface DeviceCapability {

    // --- shared (read by multiple device kinds) ---

    /** Railgun: -charge interval. Armor: +movement speed. */
    record AccelerationFactor(double factor) implements DeviceCapability {}

    /** Structural energy module capacity contribution in FE. */
    record EnergyCapacity(long fe) implements DeviceCapability {}

    /** Passive FE drain declared by a module. */
    record PassiveDrain(long fePerTick) implements DeviceCapability {}

    /** Active FE cost declared by a triggerable module behavior. */
    record ActiveCost(String trigger, long fe) implements DeviceCapability {}

    // --- railgun only ---

    /** Lightning chain segments / forks / hard-cap bonus, packed. */
    record ChainTuning(int extraSegments, int extraForks, int hardCapBonus) implements DeviceCapability {}

    /** EMP pulse radius + damage multiplier. */
    record PulseTuning(double radiusMul, double dmgMul) implements DeviceCapability {}

    /** Overload execution: cumulative damage tracker + forced kill sequence. */
    record OverloadExecutionTuning(double decayRate, int decayDelayTicks, int maxTrackedTargets) implements DeviceCapability {}

    // --- armor only ---

    /** Stage-aware mitigation applied by chestplate modules. */
    record StagedMitigation(String stage) implements DeviceCapability {}

    /** Reflects a fraction of post-armor damage back to attackers. */
    record ReflectTuning(double reflectPct, long fePerDamage) implements DeviceCapability {}

    /** Last-stand fatal damage interception. */
    record LastStandTuning(long feCost, int comboWindowTicks) implements DeviceCapability {}

    /** Periodic status-effect purification. */
    record PurificationTuning(int periodTicks, int strength) implements DeviceCapability {}

    /** Dash impulse + cooldown ticks. */
    record DashEffect(double impulse, int cooldownTicks) implements DeviceCapability {}

    /** Flight mode. */
    record FlightMode(FlightKind kind) implements DeviceCapability {}

    /** Generic status effect grant (night vision / water breathing / ...). */
    record StatusEffectGrant(Holder<MobEffect> effect, int amplifier) implements DeviceCapability {}

    /** Periodic hunger and saturation sustain targets. */
    record FoodSustain(int targetFood, float targetSaturation, int checkIntervalTicks) implements DeviceCapability {}

    /** Digging speed compensation in difficult environments. */
    record DigAffinity(String env, double speedMul) implements DeviceCapability {}

    /** Configurable block and entity interaction reach. */
    record InteractionRange() implements DeviceCapability {}

    /** Fall damage reduction. */
    record FallProtection(double damageReduction) implements DeviceCapability {}

    /** Jump boost amplifier. */
    record JumpBoost(int amplifier) implements DeviceCapability {}

    /** Alternate vision mode. */
    record Vision(String kind, int amplifier) implements DeviceCapability {}

    /** Passive energy consumption multiplier. */
    record EnergyEfficiency(double drainMul) implements DeviceCapability {}

    /** Declares which armor part an armor module belongs to. */
    record ArmorPartTag(ArmorPart part) implements DeviceCapability {}

    // --- escape hatch ---

    /** Future-proof channel; routed by id. */
    record DeviceSpecific(String id, CompoundTag data) implements DeviceCapability {}
}
