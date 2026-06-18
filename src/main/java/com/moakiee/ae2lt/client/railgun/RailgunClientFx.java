package com.moakiee.ae2lt.client.railgun;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.network.railgun.RailgunFirePacket;
import com.moakiee.ae2lt.registry.ModSounds;

/**
 * Plays charged-fire client effects:
 *  - real electric arcs along each chain segment (via {@link RailgunArcRenderer})
 *  - a ground-aligned expanding shockwave + flash core at the impact point (via
 *    {@link RailgunShockwaveRenderer})
 *  - radial mini-bolts crackling outward from the impact
 *  - layered vanilla particles (FLASH, ELECTRIC_SPARK, LARGE_SMOKE) for grit
 *  - tier-scaled thunder sound
 *
 * <p>All lifetimes are tuned to feel deliberate (≈1.5–2.5 s on the heavier
 * effects). The plasma trail's origin is the shooter's gun barrel (resolved via
 * {@link RailgunVisuals}), not the screen-center / eye, so the visual reads as
 * "fired from the weapon".
 */
public final class RailgunClientFx {

    private RailgunClientFx() {}

    public static void playCharged(RailgunFirePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 hit = p.firstHit();
        boolean isMax = p.isMax();
        int tier = Math.max(1, p.tier());

        // Resolve shooter once; fall back to server-supplied eye/hit if the player
        // isn't loaded clientside.
        float partialTick = RailgunVisuals.currentPartialTick();
        Player shooter = mc.level.getPlayerByUUID(p.shooterId());
        Vec3 plasmaOrigin = shooter == null
                ? p.from()
                : RailgunVisuals.computeBarrelOrigin(shooter, partialTick);
        Vec3 plasmaEnd = shooter == null
                ? hit
                : RailgunVisuals.computeBarrelEndpoint(shooter, plasmaOrigin, p.from(), hit, partialTick);

        // 1. Shockwave + flash core at the impact point (slow, deliberate ease-out).
        float radius = p.impactRadius();
        if (radius > 0.0F) {
            int waveLife = isMax ? 80 : 56 + tier * 6;
            RailgunShockwaveRenderer.spawn(hit, radius, waveLife);
            // Faster inner ring for a layered "double pop" feel.
            RailgunShockwaveRenderer.spawn(hit, radius * 0.50F, Math.max(35, waveLife - 15));
        }

        // 2. Chain arcs — render real lightning along each (from -> to) pair.
        var path = p.chainPath();
        for (int i = 0; i + 1 < path.size(); i += 2) {
            Vec3 a = path.get(i);
            Vec3 b = path.get(i + 1);
            int chainLife = isMax ? 40 : 32;
            RailgunArcRenderer.spawnChain(a, b, chainLife);
            // Endpoint sparks for each chain target.
            for (int s = 0; s < 4; s++) {
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z,
                        (mc.level.random.nextDouble() - 0.5) * 0.4,
                        (mc.level.random.nextDouble() - 0.5) * 0.4,
                        (mc.level.random.nextDouble() - 0.5) * 0.4);
            }
        }

        // 3. Plasma trail from gun barrel to impact — the headline "shot" effect.
        // Slightly thinner spread + longer lifetime than the chain arcs so it
        // reads as the main projectile path.
        RailgunArcRenderer.spawnPlasma(plasmaOrigin, plasmaEnd,
                Math.max(10, (int) Math.round(plasmaOrigin.distanceTo(plasmaEnd) * 1.2D)),
                isMax ? 0.30F : 0.18F,
                isMax ? 36 : 30);

        // 4. Radial mini-bolts crackling outward from the impact (longer-lived now).
        if (radius > 0.0F) {
            int bolts = isMax ? 18 : 8 + tier * 2;
            var rng = mc.level.random;
            for (int i = 0; i < bolts; i++) {
                double yaw = rng.nextDouble() * Math.PI * 2.0D;
                double pitch = (rng.nextDouble() - 0.3D) * Math.PI;       // bias upward
                double r = radius * (0.6F + rng.nextFloat() * 0.6F);
                double dx = Math.cos(yaw) * Math.cos(pitch) * r;
                double dy = Math.sin(pitch) * r;
                double dz = Math.sin(yaw) * Math.cos(pitch) * r;
                Vec3 end = hit.add(dx, dy, dz);
                RailgunArcRenderer.spawnImpactSpark(hit, end, 22 + rng.nextInt(10));
            }
        }

        // 5. Layered vanilla particles for grit + dust.
        // Scale spread/counts by radius — the central FLASH/smoke/spark cloud
        // has fixed offsets historically, which now reads small inside the bigger
        // shockwave ring. radiusScale = radius / 7 (the old tier-3 default).
        float radiusScale = Math.max(1.0F, radius / 7.0F);
        mc.level.addParticle(ParticleTypes.FLASH, hit.x, hit.y, hit.z, 0, 0, 0);
        if (isMax) {
            int flashCount = (int) (4 * radiusScale);
            float flashSpread = 0.7F * radiusScale;
            for (int i = 0; i < flashCount; i++) {
                double ox = (mc.level.random.nextDouble() - 0.5D) * flashSpread;
                double oy = (mc.level.random.nextDouble() - 0.5D) * flashSpread;
                double oz = (mc.level.random.nextDouble() - 0.5D) * flashSpread;
                mc.level.addParticle(ParticleTypes.FLASH, hit.x + ox, hit.y + oy, hit.z + oz, 0, 0, 0);
            }
            int smokeCount = (int) (48 * radiusScale);
            double smokeMotion = 0.30D * radiusScale;
            double smokeUp = 0.25D * radiusScale;
            for (int i = 0; i < smokeCount; i++) {
                mc.level.addParticle(ParticleTypes.LARGE_SMOKE,
                        hit.x, hit.y, hit.z,
                        (mc.level.random.nextDouble() - 0.5D) * smokeMotion,
                        (mc.level.random.nextDouble() - 0.2D) * smokeUp,
                        (mc.level.random.nextDouble() - 0.5D) * smokeMotion);
            }
        }
        int sparkCount = (int) ((14 + tier * 8) * radiusScale);
        double sparkVel = (0.4D + tier * 0.18D) * radiusScale;
        for (int i = 0; i < sparkCount; i++) {
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    hit.x, hit.y, hit.z,
                    (mc.level.random.nextDouble() - 0.5D) * sparkVel,
                    (mc.level.random.nextDouble() - 0.5D) * sparkVel,
                    (mc.level.random.nextDouble() - 0.5D) * sparkVel);
        }

        // 6. Sound: the muzzle report belongs near the shooter, not the impact
        // point. Otherwise a long-range shot can look correct but be inaudible
        // for the player who fired it.
        if (p.soundEnabled()) {
            var sound = isMax ? ModSounds.RAILGUN_FIRE_MAX.get() : ModSounds.RAILGUN_FIRE_CHARGED.get();
            mc.level.playLocalSound(plasmaOrigin.x, plasmaOrigin.y, plasmaOrigin.z, sound, SoundSource.PLAYERS,
                    isMax ? 1.7f : 0.9f + 0.15f * tier, 1.0f, false);
            if (isMax) {
                mc.level.playLocalSound(hit.x, hit.y, hit.z, ModSounds.RAILGUN_FIRE_IMPACT.get(),
                        SoundSource.PLAYERS, 1.4f, 0.7f, false);
            }
        }
    }
}
