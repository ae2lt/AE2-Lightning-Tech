package com.moakiee.ae2lt.config;

/**
 * Frozen design constants for the electromagnetic railgun.
 *
 * <p>These were previously per-value entries in {@link AE2LTCommonConfig}.
 * They were demoted to compile-time constants because they describe mechanic
 * shape (chain decay, splash falloff, pulse radius, recoil curve, charge
 * cadence, etc.) rather than balance dials. Tuning them at runtime via
 * config produced too many knobs that pack authors don't want to touch and
 * that, when touched, tend to break the design rather than rebalance it.
 *
 * <p>Things that <em>are</em> still in {@link AE2LTCommonConfig} (and should
 * stay there): per-tier base damage, per-tier FE/EHV cost, beam settle
 * damage / FE / HV cost, PvP toggles, terrain drop toggle, terrain
 * blocks-per-tick budget. Those are the dials a server actually wants.
 */
public final class RailgunDefaults {

    private RailgunDefaults() {}

    // ── Beam mechanics ────────────────────────────────────────────────────
    /** Beam settlement interval in ticks (acceleration module reduces, min 1). */
    public static final int BEAM_SETTLE_INTERVAL_TICKS = 2;
    /** Maximum beam range in blocks. */
    public static final int BEAM_RANGE = 64;
    /** Min ticks between chain-jump triggers on beam path (5 = 4 chains/sec). */
    public static final int BEAM_CHAIN_THROTTLE_TICKS = 5;
    /** Maximum concurrent beam users on the server. */
    public static final int BEAM_MAX_CONCURRENT = 16;

    // ── Chain mechanics ───────────────────────────────────────────────────
    /** Per-segment damage decay multiplier (0.92 = 8% loss). */
    public static final double CHAIN_DECAY = 0.92D;
    /** Base chain segments for left-beam (compute module +2 each). */
    public static final int CHAIN_BASE = 3;
    /** Hard cap on chain segments per fire event. */
    public static final int CHAIN_HARD_CAP = 50;
    /** Chain-jump search radius in blocks for the left-beam. */
    public static final double CHAIN_RADIUS = 16.0D;

    // ── Chain fork mechanics (charged-shot only) ──────────────────────────
    /** Base fork branches from the primary target by charge tier. */
    public static final int CHAIN_FORK_BASE_EHV1 = 2;
    public static final int CHAIN_FORK_BASE_EHV2 = 3;
    public static final int CHAIN_FORK_BASE_EHV3 = 4;
    /** Each compute module adds this many additional forks. */
    public static final int CHAIN_FORK_PER_COMPUTE = 1;
    /** Storm grants this many additional forks. */
    public static final int CHAIN_FORK_STORM_BONUS = 1;
    /** Hard cap on fork branches per fire (prevents trivial mob-rooms from overflowing). */
    public static final int CHAIN_FORK_HARD_CAP = 8;

    // ── Charged-shot mechanics ────────────────────────────────────────────
    /** Charged-fire raycast range in blocks. */
    public static final int CHARGED_RANGE = 64;
    /** Max-tier EMP pulse radius in blocks. */
    public static final int PULSE_RADIUS = 10;
    /** Max-tier EMP pulse damage ratio of main hit. */
    public static final double PULSE_DAMAGE_RATIO = 0.6D;
    /** Max-tier penetration: max targets along beam path. */
    public static final int PENETRATION_MAX_TARGETS = 5;

    // ── Impact splash (always-on AoE around landing point) ────────────────
    public static final double IMPACT_RADIUS_TIER1 = 5.5D;
    public static final double IMPACT_RADIUS_TIER2 = 8.0D;
    public static final double IMPACT_RADIUS_TIER3 = 12.0D;
    /** Center damage ratio (of base). Falls off to 40% at edge. */
    public static final double IMPACT_DAMAGE_RATIO_TIER1 = 0.45D;
    public static final double IMPACT_DAMAGE_RATIO_TIER2 = 0.55D;
    public static final double IMPACT_DAMAGE_RATIO_TIER3 = 0.65D;

    // ── Charge timing (ticks) ─────────────────────────────────────────────
    public static final int CHARGE_TICKS_TIER1 = 10;  // 0.5s
    public static final int CHARGE_TICKS_TIER2 = 24;  // 1.2s
    public static final int CHARGE_TICKS_TIER3 = 40;  // 2.0s

    // ── Armor bypass ──────────────────────────────────────────────────────
    public static final double ARMOR_BYPASS_BEAM = 0.30D;
    public static final double ARMOR_BYPASS_TIER1 = 0.40D;
    public static final double ARMOR_BYPASS_TIER2 = 0.60D;
    public static final double ARMOR_BYPASS_TIER3 = 0.80D;

    // ── Storm bonus ───────────────────────────────────────────────────────
    /** Damage multiplier when current dimension is thundering. */
    public static final double STORM_DAMAGE_MUL = 1.25D;
    /** Extra chain segments granted by thunderstorm. */
    public static final int STORM_CHAIN_BONUS = 2;
    /** Extra chain radius granted by thunderstorm (blocks). */
    public static final double STORM_CHAIN_RADIUS_BONUS = 4.0D;

    // ── Paralysis effect ──────────────────────────────────────────────────
    /** Duration of electromagnetic paralysis effect on hit (20 = 1s). */
    public static final int PARALYSIS_DURATION_TICKS = 40;

    // ── Recoil ────────────────────────────────────────────────────────────
    public static final double RECOIL_SPEED_TIER1 = 0.6D;
    public static final double RECOIL_SPEED_TIER2 = 1.2D;
    public static final double RECOIL_SPEED_TIER3 = 2.0D;
    public static final double RECOIL_CROUCH_MUL = 0.5D;
    /** Recoil multiplier when airborne (electromagnetic recoil jump). */
    public static final double RECOIL_AIRBORNE_MUL = 1.5D;

    // ── Terrain destruction ───────────────────────────────────────────────
    public static final double TERRAIN_RADIUS_TIER1 = 4.0D;
    public static final double TERRAIN_RADIUS_TIER2 = 6.0D;
    public static final double TERRAIN_RADIUS_TIER3 = 10.0D;
    public static final double TERRAIN_HARDNESS_TIER1 = 5.0D;
    public static final double TERRAIN_HARDNESS_TIER2 = 25.0D;
    public static final double TERRAIN_HARDNESS_TIER3 = 50.0D;
    /** Sphere radius at each penetration point (max-tier). */
    public static final double PENETRATION_DESTROY_RADIUS = 3.0D;
    /** Max breakable hardness for penetration tunnel. */
    public static final double PENETRATION_DESTROY_HARDNESS = 25.0D;

    // ── EHV → HV compensation (with core module) ──────────────────────────
    /** With core module installed: 1 EHV = N HV when EHV is depleted. */
    public static final int COMPENSATION_RATIO = 16;
}
