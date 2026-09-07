package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import com.moakiee.ae2lt.client.armor.CelestweaveArmorGeometry;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

final class CelestweaveFieldGeometryTest {
    @Test
    void everySlotHasFiniteTriangleSurfacesAndUnitNormalsForBothSkinWidths() {
        for (boolean slim : new boolean[] {false, true}) {
            for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                var surfaces = CelestweaveArmorGeometry.surfaces(slot, slim);
                assertFalse(surfaces.isEmpty());
                for (var surface : surfaces) {
                    assertEquals(0, surface.vertices().size() % 3);
                    for (var v : surface.vertices()) {
                        assertTrue(Float.isFinite(v.x()) && Float.isFinite(v.y()) && Float.isFinite(v.z()));
                        assertEquals(1, v.nx()*v.nx() + v.ny()*v.ny() + v.nz()*v.nz(), 0.0001);
                    }
                }
            }
        }
    }

    @Test
    void fieldPassDoesNotLeaveAVanillaArmorOrGlintShell() {
        for (var slot : EquipmentSlot.values()) {
            var root = CelestweaveArmorGeometry.create(slot, false).bakeRoot();
            assertTrue(root.getAllParts().allMatch(p -> p.isEmpty()));
        }
        assertTrue(CelestweaveArmorGeometry.surfaces(EquipmentSlot.MAINHAND, false).isEmpty());
    }

    @Test
    void headLeavesTheFaceOpenAndSlimArmsAreNarrower() {
        var head = CelestweaveArmorGeometry.surfaces(EquipmentSlot.HEAD, false).getFirst();
        // The head arcs stop at the temples; no triangles span the central face.
        assertTrue(head.vertices().stream().allMatch(v -> Math.abs(v.x()) > 0.10F));
        assertTrue(head.vertices().stream().anyMatch(v -> v.v() >= 2));
        var normal = CelestweaveArmorGeometry.surfaces(EquipmentSlot.CHEST, false).stream().filter(s -> s.bone().equals("right_arm")).findFirst().orElseThrow().vertices();
        var slim = CelestweaveArmorGeometry.surfaces(EquipmentSlot.CHEST, true).stream().filter(s -> s.bone().equals("right_arm")).findFirst().orElseThrow().vertices();
        double normalWidth = normal.stream().mapToDouble(v -> v.x()).max().orElseThrow()
                - normal.stream().mapToDouble(v -> v.x()).min().orElseThrow();
        double slimWidth = slim.stream().mapToDouble(v -> v.x()).max().orElseThrow()
                - slim.stream().mapToDouble(v -> v.x()).min().orElseThrow();
        assertTrue(slimWidth < normalWidth);
    }
}
