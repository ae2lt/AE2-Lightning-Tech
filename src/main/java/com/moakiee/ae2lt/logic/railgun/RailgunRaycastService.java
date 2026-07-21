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
        Vec3 blockEnd = blockHit.getType() == HitResult.Type.MISS ? to : blockHit.getLocation();
        EntityHitResult entityHit = traceEntities(
                level, shooter, from, blockEnd, queryPadding, targetInflation, filter);
        return new Result(blockHit, blockEnd, entityHit);
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
            @Nullable EntityHitResult entityHit) {}
}
