package com.moakiee.ae2lt.logic.railgun;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class RailgunRaycastServiceTest {

    @Test
    void splitsLongRaysIntoFixedLengthQueries() {
        assertEquals(0, RailgunRaycastService.segmentCount(0.0D));
        assertEquals(1, RailgunRaycastService.segmentCount(1.0D));
        assertEquals(1, RailgunRaycastService.segmentCount(16.0D));
        assertEquals(2, RailgunRaycastService.segmentCount(16.0001D));
        assertEquals(4, RailgunRaycastService.segmentCount(64.0D));
        assertEquals(16, RailgunRaycastService.segmentCount(256.0D));
        assertEquals(0, RailgunRaycastService.segmentCount(Double.NaN));
    }

    @Test
    void preservesOrdinaryBlockHitsOnTheRequestedRay() {
        Vec3 from = new Vec3(10.0D, 20.0D, 30.0D);
        Vec3 to = new Vec3(10.0D, 20.0D, 94.0D);
        Vec3 hit = new Vec3(10.0D, 20.0D, 47.25D);

        assertEquals(hit, RailgunRaycastService.sanitizeBlockEnd(from, to, hit));
    }

    @Test
    void rejectsAlternateCoordinateSpacesWithoutNamingTheirProvider() {
        Vec3 from = new Vec3(10.0D, 20.0D, 30.0D);
        Vec3 to = new Vec3(10.0D, 20.0D, 94.0D);

        assertEquals(to, RailgunRaycastService.sanitizeBlockEnd(
                from, to, new Vec3(160_000.0D, 70.0D, 161_000.0D)));
        assertEquals(to, RailgunRaycastService.sanitizeBlockEnd(
                from, to, new Vec3(Double.NaN, 20.0D, 40.0D)));
    }
}
