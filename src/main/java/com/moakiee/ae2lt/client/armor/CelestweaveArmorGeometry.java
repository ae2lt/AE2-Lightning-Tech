package com.moakiee.ae2lt.client.armor;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Vector3f;

/** Open, rectilinear field armor. Flat faces and angular shoulders follow Minecraft pixel silhouettes. */
public final class CelestweaveArmorGeometry {
    private static final int DIVISIONS = 12;
    private CelestweaveArmorGeometry() {}

    public record FieldVertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {}
    public record FieldSurface(String bone, List<FieldVertex> vertices, float strength) {}

    public static LayerDefinition create(EquipmentSlot ignored, boolean slim) {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5, slim ? 2.5F : 2, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5, slim ? 2.5F : 2, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12, 0));
        return LayerDefinition.create(mesh, 16, 16);
    }

    public static List<FieldSurface> surfaces(EquipmentSlot slot, boolean slim) {
        List<FieldSurface> surfaces = new ArrayList<>();
        switch (slot) {
            case HEAD -> volume(surfaces, "head", 0, -4.3F, 0, 9.3F, 8.8F, 9.3F, 0.02F, 0.85F);
            case CHEST -> {
                volume(surfaces, "body", 0, 5.0F, 0, 9.2F, 11.8F, 5.4F, 0.08F, 0.9F);
                for (int side : new int[] {-1, 1}) {
                    String bone = side < 0 ? "right_arm" : "left_arm";
                    float center = side * (slim ? 0.5F : 1);
                    // Shoulder fields flare beyond the skin; elbows stay open.
                    volume(surfaces, bone, center + side * 0.2F, -0.1F, 0,
                            slim ? 4.9F : 5.9F, 5.4F, 5.8F, 0.10F, 1);
                    volume(surfaces, bone, center, 6.7F, 0,
                            slim ? 4.3F : 5.3F, 7.1F, 5.3F, 0.04F, 0.95F);
                }
            }
            case LEGS -> {
                for (String bone : List.of("right_leg", "left_leg")) {
                    volume(surfaces, bone, 0, 2.8F, 0, 4.9F, 6.4F, 5.1F, 0.03F, 0.85F);
                }
            }
            case FEET -> {
                for (String bone : List.of("right_leg", "left_leg")) {
                    volume(surfaces, bone, 0, 8.8F, -0.20F, 5.1F, 7.0F, 5.6F, 0.06F, 1);
                }
            }
            default -> { }
        }
        return List.copyOf(surfaces);
    }

    private static void volume(List<FieldSurface> out, String bone, float cx, float cy, float cz,
            float w, float h, float d, float flare, float strength) {
        List<FieldVertex> vertices = new ArrayList<>();
        boolean axial = bone.equals("head") || bone.equals("body");
        float arc = bone.equals("head") ? 0.94F : axial ? 1.30F : 1.36F;
        float motifFamily = axial ? 0 : bone.endsWith("arm") ? (cy < 2 ? 16 : 20) : cy < 6 ? 24 : 28;
        // Two open rectangular sections and two spaced energy strata keep the face and joints clear.
        for (int layer = 0; layer < 2; layer++) {
            for (int side : new int[] {-1, 1}) {
                float centerAngle = axial ? side * (float) Math.PI / 2 : side < 0 ? 0 : (float) Math.PI;
                for (int i = 0; i < DIVISIONS; i++) {
                    for (int j = 0; j < DIVISIONS; j++) {
                        for (int corner : new int[] {0, 1, 2, 0, 2, 3}) {
                            float u = (i + (corner == 1 || corner == 2 ? 1 : 0)) / (float) DIVISIONS;
                            float v = (j + (corner >= 2 ? 1 : 0)) / (float) DIVISIONS;
                            float angle = centerAngle + (u * 2 - 1) * arc;
                            float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
                            // Project the arcs onto a square cross-section: all sides are planar.
                            float divisor = Math.max(Math.abs(sin), Math.abs(cos));
                            float squareX = sin / divisor, squareZ = -cos / divisor;
                            float profile = v < 0.25F ? 1 + flare
                                    : v < 0.75F ? 1 + flare - (v - 0.25F) * flare * 2
                                    : 1 - (v - 0.75F) * 0.20F;
                            float scale = profile * (layer == 0 ? 1 : 1.055F);
                            float y = cy + (v - 0.5F) * h + layer * 0.12F;
                            // Flat normals keep the energy from reading as a rounded glass bubble.
                            Vector3f normal = Math.abs(sin) >= Math.abs(cos)
                                    ? new Vector3f(Math.signum(sin), 0, 0)
                                    : new Vector3f(0, 0, -Math.signum(cos));
                            vertices.add(new FieldVertex((cx + squareX * w / 2 * scale) / 16,
                                    y / 16, (cz + squareZ * d / 2 * scale) / 16,
                                    u, v + layer * 2 + motifFamily, normal.x, normal.y, normal.z));
                        }
                    }
                }
            }
        }
        out.add(new FieldSurface(bone, List.copyOf(vertices), strength));
        if (axial) {
            // Sparse front/back embroidery bridges the open field without filling the skin areas.
            // UV families: 4 = chest weave, 8 = crown plane, 12 = brow lightning.
            for (int section = 0; section < (bone.equals("head") ? 3 : 2); section++) {
                boolean top = bone.equals("head") && section == 2;
                float family = bone.equals("body") ? 4 : top ? 8 : 12;
                List<FieldVertex> embroidery = new ArrayList<>();
                for (int corner : new int[] {0, 1, 2, 0, 2, 3}) {
                    float u = corner == 1 || corner == 2 ? 1 : 0;
                    float v = corner >= 2 ? 1 : 0;
                    float x = (u - 0.5F) * (bone.equals("head") ? 9.7F : 8.8F);
                    float y = bone.equals("body") ? 0.8F + v * 9.8F
                            : top ? -8.85F : -9.0F + v * 1.65F;
                    float z = top ? (v - 0.5F) * 9.7F
                            : (section == 0 ? -1 : 1) * (bone.equals("head") ? 4.88F : 3.15F);
                    embroidery.add(new FieldVertex(x / 16, y / 16, z / 16, u, v + family,
                            0, top ? -1 : 0, top ? 0 : section == 0 ? -1 : 1));
                }
                out.add(new FieldSurface(bone, List.copyOf(embroidery), 1));
            }
        }
    }
}
