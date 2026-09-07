package com.moakiee.ae2lt.client.armor;

import java.util.EnumMap;
import java.util.Map;

import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.item.PhaseLockProjectionItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Open lightning fields; an installed structural core changes the whole equipped set to violet. */
public final class CelestweaveArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>>
        extends RenderLayer<T, M> {
    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final Map<EquipmentSlot, CelestweaveArmorModel<T>> models = new EnumMap<>(EquipmentSlot.class);

    public CelestweaveArmorLayer(RenderLayerParent<T, M> parent, EntityModelSet entityModels, boolean slim) {
        super(parent);
        for (EquipmentSlot slot : SLOTS) {
            models.put(slot, new CelestweaveArmorModel<>(
                    entityModels.bakeLayer(CelestweaveArmorModel.layer(slot, slim)), slot, slim));
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, T entity,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
            float netHeadYaw, float headPitch) {
        if (entity instanceof Player player && player.isSpectator()) return;
        var viewer = Minecraft.getInstance().player;
        boolean ghost = entity.isInvisible();
        if (ghost && (viewer == null || entity.isInvisibleTo(viewer))) return;
        float visibility = ghost ? 0.15F : 1.0F;
        boolean overloaded = false;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!matches(stack, slot)) continue;
            // Phase-lock projections only persist while the chest anchor has its structural core.
            if (stack.getItem() instanceof PhaseLockProjectionItem
                    || CelestweaveArmorState.hasCore(stack, entity.registryAccess())) {
                overloaded = true;
                break;
            }
        }
        int tint = FastColor.ARGB32.color(Mth.clamp((int) (visibility * 255), 0, 255),
                overloaded ? 188 : 105, overloaded ? 100 : 202, 255);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!matches(stack, slot)) continue;
            CelestweaveArmorModel<T> model = models.get(slot);
            model.prepare(getParentModel());
            model.renderToBuffer(pose, buffers.getBuffer(CelestweaveFieldRenderType.field()),
                    packedLight, OverlayTexture.NO_OVERLAY,
                    tint);
        }
    }

    static boolean matches(ItemStack stack, EquipmentSlot slot) {
        return stack.getItem() instanceof BaseCelestweaveArmorItem armor && armor.getEquipmentSlot() == slot
                || stack.getItem() instanceof PhaseLockProjectionItem projection && projection.equipmentSlot() == slot;
    }

}
