package com.moakiee.ae2lt.client.armor;

import java.util.List;
import com.moakiee.ae2lt.AE2LightningTech;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

public final class CelestweaveArmorModel<T extends LivingEntity> extends HumanoidModel<T> {
    private final ModelPart root;
    private final List<CelestweaveArmorGeometry.FieldSurface> surfaces;

    public CelestweaveArmorModel(ModelPart root, EquipmentSlot slot, boolean slim) {
        super(root);
        this.root = root;
        this.surfaces = CelestweaveArmorGeometry.surfaces(slot, slim);
    }

    public static ModelLayerLocation layer(EquipmentSlot slot, boolean slim) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID,
                "celestweave_field_" + slot.getName() + (slim ? "_slim" : "")), "main");
    }

    public void prepare(HumanoidModel<T> parent) {
        parent.copyPropertiesTo(this);
        head.visible = parent.head.visible;
        body.visible = parent.body.visible;
        rightArm.visible = parent.rightArm.visible;
        leftArm.visible = parent.leftArm.visible;
        rightLeg.visible = parent.rightLeg.visible;
        leftLeg.visible = parent.leftLeg.visible;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        for (var surface : surfaces) {
            ModelPart part = root.getChild(surface.bone());
            if (!part.visible) continue;
            pose.pushPose();
            if (young) {
                if (surface.bone().equals("head")) {
                    pose.scale(0.75F, 0.75F, 0.75F);
                    pose.translate(0, 1, 0);
                } else {
                    pose.scale(0.5F, 0.5F, 0.5F);
                    pose.translate(0, 1.5, 0);
                }
            }
            part.translateAndRotate(pose);
            int tint = FastColor.ARGB32.color((int) (FastColor.ARGB32.alpha(color) * surface.strength()),
                    FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color));
            for (var vertex : surface.vertices()) {
                buffer.addVertex(pose.last(), vertex.x(), vertex.y(), vertex.z())
                        .setColor(tint).setUv(vertex.u(), vertex.v()).setOverlay(overlay).setLight(light)
                        .setNormal(pose.last(), vertex.nx(), vertex.ny(), vertex.nz());
            }
            pose.popPose();
        }
    }
}
