package com.moakiee.ae2lt.logic.railgun;

import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.moakiee.ae2lt.config.RailgunDefaults;

/** Shared block and entity raycast for continuous beams and charged shots. */
public final class RailgunRaycastService {

    /** Allows for ordinary voxel-hit rounding without accepting unrelated coordinates. */
    private static final double BLOCK_HIT_RAY_TOLERANCE_SQR = 1.0D;

    private RailgunRaycastService() {}

    /**
     * Traces blocks with vanilla's voxel DDA and entities in short, front-to-back segments.
     *
     * <p>{@link ProjectileUtil} expects its caller to provide a broad-phase AABB. Vanilla
     * projectiles normally pass only one tick of movement, but a hitscan weapon passing the
     * complete ray creates a large axis-aligned box. A horizontal diagonal ray can therefore
     * scan an area proportional to range squared, and a three-axis diagonal can approach range
     * cubed in a dense world. Fixed-length segments keep each broad phase local, making total
     * work scale approximately with ray length plus entities near the ray.</p>
     */
    public static Result traceFirst(
            ServerLevel level,
            Entity shooter,
            Vec3 from,
            Vec3 to,
            double queryPadding,
            float targetInflation,
            Predicate<Entity> filter) {
        BlockHitResult blockHit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 rawBlockEnd = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();
        Vec3 blockEnd = sanitizeBlockEnd(from, to, rawBlockEnd);
        EntityHitResult entityHit = traceEntities(
                level, shooter, from, blockEnd, queryPadding, targetInflation, filter);
        // Keep a finite raw endpoint for block mutation. Some level implementations store
        // interactive blocks in another coordinate space while returning their hit through
        // the normal clip API. That coordinate must never leak into entity queries or FX,
        // but it may still be the correct coordinate for the owning level's block access.
        Vec3 terrainEnd = isFinite(rawBlockEnd) ? rawBlockEnd : blockEnd;
        return new Result(blockHit, blockEnd, terrainEnd, entityHit);
    }

    /**
     * Accepts normal hit locations unchanged and rejects coordinates that do not describe a
     * point on the requested ray segment. This is deliberately implementation-agnostic: any
     * level, mixin, or raycast provider can return an alternate coordinate space.
     */
    static Vec3 sanitizeBlockEnd(Vec3 from, Vec3 to, Vec3 candidate) {
        if (!isFinite(from) || !isFinite(to) || !isFinite(candidate)) {
            return isFinite(to) ? to : Vec3.ZERO;
        }

        Vec3 ray = to.subtract(from);
        double rayLengthSqr = ray.lengthSqr();
        if (!Double.isFinite(rayLengthSqr) || rayLengthSqr <= 1.0E-12D) {
            return from;
        }

        Vec3 offset = candidate.subtract(from);
        double progress = offset.dot(ray) / rayLengthSqr;
        if (!Double.isFinite(progress) || progress < 0.0D || progress > 1.0D) {
            return to;
        }

        Vec3 nearest = from.add(ray.scale(progress));
        double offRayDistanceSqr = candidate.distanceToSqr(nearest);
        if (!Double.isFinite(offRayDistanceSqr) || offRayDistanceSqr > BLOCK_HIT_RAY_TOLERANCE_SQR) {
            return to;
        }
        return candidate;
    }

    private static boolean isFinite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    @Nullable
    private static EntityHitResult traceEntities(
            ServerLevel level,
            Entity shooter,
            Vec3 from,
            Vec3 to,
            double queryPadding,
            float targetInflation,
            Predicate<Entity> filter) {
        Vec3 ray = to.subtract(from);
        double distance = ray.length();
        int segments = segmentCount(distance);
        if (segments == 0) {
            return null;
        }

        Vec3 step = ray.scale(1.0D / segments);
        Vec3 segmentStart = from;
        double padding = Math.max(0.0D, queryPadding);
        float inflation = Math.max(0.0F, targetInflation);

        for (int segment = 1; segment <= segments; segment++) {
            Vec3 segmentEnd = segment == segments ? to : from.add(step.scale(segment));
            AABB candidates = new AABB(segmentStart, segmentEnd).inflate(padding);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                    level, shooter, segmentStart, segmentEnd, candidates, filter, inflation);
            if (hit != null) {
                return hit;
            }
            segmentStart = segmentEnd;
        }
        return null;
    }

    static int segmentCount(double distance) {
        if (!Double.isFinite(distance) || distance <= 0.0D) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(distance / RailgunDefaults.ENTITY_QUERY_SEGMENT_LENGTH));
    }

    public record Result(
            BlockHitResult blockHit,
            Vec3 blockEnd,
            Vec3 terrainEnd,
            @Nullable EntityHitResult entityHit) {}
}
