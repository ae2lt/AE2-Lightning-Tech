package com.moakiee.ae2lt.logic.railgun;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Single hit on a target with a final damage scalar; optional flag for penetration. */
public final class RailgunChainResolver {

    /**
     * A single hit on a target.
     *
     * @param chainStartAt If non-null, the visual chain renderer should reset its "previous
     *                     position" to this Vec3 before drawing the segment to this hit. Used
     *                     for the splash-triggered chain so the arc visually starts from the
     *                     impact center rather than continuing from the primary chain's tail.
     */
    public record Hit(LivingEntity target, double damage, boolean penetration, boolean pulse,
                      @Nullable Vec3 chainStartAt) {
        /** Convenience: standard 4-arg constructor with no chain-start override. */
        public Hit(LivingEntity target, double damage, boolean penetration, boolean pulse) {
            this(target, damage, penetration, pulse, null);
        }
    }

    private RailgunChainResolver() {}

    /** Search out from {@code start} in BFS-like fashion, jumping along the chain. */
    public static List<Hit> resolveChain(
            ServerLevel level,
            ServerPlayer source,
            LivingEntity start,
            DamageContext ctx) {
        return resolveChainFrom(level, source, start, ctx, Set.of(), null);
    }

    /**
     * 起点分叉版本：从 {@code start} 同时拉出最多 {@code ctx.chainForkCount()} 条
     * 独立单线链。所有分支共享同一个 visited 集合，避免抢同一目标。
     * 当 forkCount &lt;= 1 时退化为 {@link #resolveChainFrom}，行为完全一致。
     */
    public static List<Hit> resolveChainForkedFrom(
            ServerLevel level,
            ServerPlayer source,
            LivingEntity start,
            DamageContext ctx,
            Set<Integer> initiallyVisited,
            @Nullable Vec3 firstSegmentVisualStart) {
        int forks = Math.max(1, ctx.chainForkCount());
        if (forks <= 1) {
            return resolveChainFrom(level, source, start, ctx, initiallyVisited, firstSegmentVisualStart);
        }
        List<Hit> out = new ArrayList<>();
        if (ctx.chainSegments() <= 0) return out;

        // 共享 visited：所有分支看到的"已命中名单"是同一份。
        Set<Integer> visited = new HashSet<>(initiallyVisited);
        visited.add(start.getId());

        List<LivingEntity> seeds = findNearestK(
                level, source, start, ctx.chainRadius(), visited, ctx.pvpLock(), forks);
        if (seeds.isEmpty()) return out;

        // 分支视觉起点：飞溅二次链场景沿用 firstSegmentVisualStart；主链场景用 start 中心。
        Vec3 branchVisualStart = (firstSegmentVisualStart != null)
                ? firstSegmentVisualStart
                : start.position().add(0.0D, start.getBbHeight() / 2.0D, 0.0D);

        for (LivingEntity seed : seeds) {
            visited.add(seed.getId());
            out.addAll(walkBranch(level, source, seed, ctx, visited, branchVisualStart));
        }
        return out;
    }

    /** 单条分支推进：seed 作为深度 1，往下走 chainSegments-1 跳。 */
    private static List<Hit> walkBranch(
            ServerLevel level,
            ServerPlayer source,
            LivingEntity seed,
            DamageContext ctx,
            Set<Integer> visited,
            Vec3 branchVisualStart) {
        List<Hit> out = new ArrayList<>();
        // 首跳衰减：最大档第一跳免衰减，沿用 resolveChainFrom 的语义。
        double damage = ctx.firstDamage();
        if (!ctx.isMaxCharged()) {
            damage *= ctx.chainDecay();
        }
        out.add(new Hit(seed, damage, false, false, branchVisualStart));
        LivingEntity prev = seed;
        for (int i = 1; i < ctx.chainSegments(); i++) {
            damage *= ctx.chainDecay();
            LivingEntity next = findNearest(level, source, prev, ctx.chainRadius(), visited, ctx.pvpLock());
            if (next == null) break;
            visited.add(next.getId());
            out.add(new Hit(next, damage, false, false, null));
            prev = next;
        }
        return out;
    }

    /**
     * Same as {@link #resolveChain} but with two extra inputs:
     *
     * @param initiallyVisited   entity IDs that the chain MUST NOT jump to (e.g. already-hit
     *                           splash victims and the primary's chain). The {@code start}
     *                           entity itself is also added automatically.
     * @param firstSegmentVisualStart If non-null, marks the first emitted hit with a
     *                                {@link Hit#chainStartAt} override so the renderer draws
     *                                the first arc from that point instead of continuing
     *                                visually from the previous chain tail.
     */
    public static List<Hit> resolveChainFrom(
            ServerLevel level,
            ServerPlayer source,
            LivingEntity start,
            DamageContext ctx,
            Set<Integer> initiallyVisited,
            @Nullable Vec3 firstSegmentVisualStart) {
        List<Hit> out = new ArrayList<>();
        if (ctx.chainSegments() <= 0) return out;

        Set<Integer> visited = new HashSet<>(initiallyVisited);
        visited.add(start.getId());

        LivingEntity prev = start;
        double damage = ctx.firstDamage();
        boolean firstEmitted = false;
        for (int i = 0; i < ctx.chainSegments(); i++) {
            // Max-tier charged: first chain segment doesn't decay, after that decay each step.
            if (!ctx.isMaxCharged() || i > 0) {
                damage *= ctx.chainDecay();
            }
            LivingEntity next = findNearest(level, source, prev, ctx.chainRadius(), visited, ctx.pvpLock());
            if (next == null) break;
            visited.add(next.getId());
            Vec3 visualStart = (!firstEmitted) ? firstSegmentVisualStart : null;
            out.add(new Hit(next, damage, false, false, visualStart));
            firstEmitted = true;
            prev = next;
        }
        return out;
    }

    /** Penetration: extend a straight line from source eye through firstHit's position. */
    public static List<Hit> resolvePenetration(
            ServerLevel level,
            ServerPlayer source,
            LivingEntity firstHit,
            DamageContext ctx,
            int maxTargets) {
        List<Hit> out = new ArrayList<>();
        if (!ctx.isMaxCharged() || maxTargets <= 0) return out;

        Vec3 from = source.getEyePosition();
        Vec3 dir = firstHit.position().subtract(from).normalize();
        double maxLen = 32.0D;
        Vec3 to = from.add(dir.scale(maxLen));

        AABB box = new AABB(from, to).inflate(1.5D);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != source && e != firstHit && shouldTarget(e, ctx.pvpLock()));
        // Sort by projected distance along dir
        candidates.sort((a, b) -> {
            double da = a.position().subtract(from).dot(dir);
            double db = b.position().subtract(from).dot(dir);
            return Double.compare(da, db);
        });
        int taken = 0;
        for (LivingEntity ent : candidates) {
            Vec3 v = ent.position().subtract(from);
            double along = v.dot(dir);
            if (along < 0.0D || along > maxLen) continue;
            // Perpendicular distance from beam axis must be small (within hitbox-ish).
            double perp = v.subtract(dir.scale(along)).length();
            if (perp > 1.5D) continue;
            out.add(new Hit(ent, ctx.firstDamage(), true, false));
            if (++taken >= maxTargets) break;
        }
        return out;
    }

    /** EMP pulse: radial AOE around the first-hit position. */
    public static List<Hit> resolvePulse(
            ServerLevel level,
            ServerPlayer source,
            Vec3 center,
            double radius,
            double damageRatio,
            DamageContext ctx,
            int excludeId) {
        List<Hit> out = new ArrayList<>();
        if (!ctx.isMaxCharged() || radius <= 0) return out;
        AABB box = new AABB(center, center).inflate(radius);
        double r2 = radius * radius;
        for (LivingEntity ent : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != source && e.getId() != excludeId && shouldTarget(e, ctx.pvpLock()))) {
            if (ent.position().distanceToSqr(center) > r2) continue;
            double dmg = ctx.firstDamage() * damageRatio;
            if (ent instanceof Player) {
                dmg *= 0.5D; // halved for players to avoid wiping teammates
            }
            out.add(new Hit(ent, dmg, false, true));
        }
        return out;
    }

    /**
     * Impact splash damage at the shot landing point, regardless of whether a direct entity
     * hit occurred. Fires on every charged tier (smaller AoE for lower tiers). Damage falls
     * off linearly from {@code damageRatio} at the center to ~{@code damageRatio * 0.4} at
     * the edge, and players get a 50% reduction.
     *
     * @param primaryId   id of the directly-hit entity to skip (so it doesn't double-take
     *                    splash on top of the direct hit). Pass {@code -1} on miss.
     */
    public static List<Hit> resolveImpactSplash(
            ServerLevel level,
            ServerPlayer source,
            Vec3 center,
            double radius,
            double damageRatio,
            int primaryId,
            DamageContext ctx) {
        List<Hit> out = new ArrayList<>();
        if (radius <= 0 || damageRatio <= 0) return out;
        AABB box = new AABB(center, center).inflate(radius);
        double r2 = radius * radius;
        for (LivingEntity ent : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != source && shouldTarget(e, ctx.pvpLock()))) {
            if (ent.getId() == primaryId) continue;
            double d2 = ent.position().distanceToSqr(center);
            if (d2 > r2) continue;
            double d = Math.sqrt(d2);
            // Linear falloff: 1.0 at center → 0.4 at edge
            double falloff = 1.0D - (d / radius) * 0.6D;
            double dmg = ctx.firstDamage() * damageRatio * falloff;
            if (ent instanceof Player) {
                dmg *= 0.5D;
            }
            // Reuse pulse flag so applyAll's "pulse skips pvpLock" branch works
            out.add(new Hit(ent, dmg, false, true));
        }
        return out;
    }

    private static LivingEntity findNearest(
            ServerLevel level,
            ServerPlayer source,
            Entity from,
            double radius,
            Set<Integer> visited,
            boolean pvpLock) {
        Vec3 c = from.position();
        AABB box = new AABB(c, c).inflate(radius);
        LivingEntity best = null;
        double r2 = radius * radius;
        double bestDistSqr = Double.MAX_VALUE;
        for (LivingEntity ent : level.getEntitiesOfClass(LivingEntity.class, box, e -> shouldTarget(e, pvpLock))) {
            if (ent == source) continue;
            if (visited.contains(ent.getId())) continue;
            double d2 = ent.position().distanceToSqr(c);
            if (d2 > r2) continue;
            if (d2 < bestDistSqr) {
                best = ent;
                bestDistSqr = d2;
            }
        }
        return best;
    }

    /** 与 {@link #findNearest} 同语义，但返回最多 K 个最近未命中目标，按距离升序。 */
    private static List<LivingEntity> findNearestK(
            ServerLevel level,
            ServerPlayer source,
            Entity from,
            double radius,
            Set<Integer> visited,
            boolean pvpLock,
            int k) {
        if (k <= 0) return List.of();
        Vec3 c = from.position();
        AABB box = new AABB(c, c).inflate(radius);
        double r2 = radius * radius;
        List<LivingEntity> all = new ArrayList<>();
        for (LivingEntity ent : level.getEntitiesOfClass(LivingEntity.class, box, e -> shouldTarget(e, pvpLock))) {
            if (ent == source) continue;
            if (visited.contains(ent.getId())) continue;
            if (ent.position().distanceToSqr(c) > r2) continue;
            all.add(ent);
        }
        all.sort((a, b) -> Double.compare(
                a.position().distanceToSqr(c),
                b.position().distanceToSqr(c)));
        if (all.size() > k) {
            return new ArrayList<>(all.subList(0, k));
        }
        return all;
    }

    private static boolean shouldTarget(LivingEntity entity, boolean pvpLock) {
        if (!entity.isAlive()) return false;
        if (entity.isInvulnerable()) return false;
        if (entity instanceof Player p) {
            if (pvpLock) return false;
            return !p.isCreative() && !p.isSpectator();
        }
        // Mobs: include hostile/neutral/passive; design says all enemies inc. neutral.
        return entity instanceof Mob || entity instanceof LivingEntity;
    }
}
