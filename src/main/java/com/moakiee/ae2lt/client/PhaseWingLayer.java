package com.moakiee.ae2lt.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.item.CelestweaveCoreItem;
import com.moakiee.ae2lt.item.PhaseLockProjectionItem;

/** Materializes vanilla-shaped wings only while Celestweave phase-wing flight is active. */
public final class PhaseWingLayer
        extends ElytraLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation WING_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");

    public PhaseWingLayer(PlayerRenderer renderer, EntityModelSet models) {
        super(renderer, models);
    }

    @Override
    public boolean shouldRender(ItemStack stack, AbstractClientPlayer player) {
        return player.isFallFlying()
                && isCelestweaveChest(stack);
    }

    @Override
    public ResourceLocation getElytraTexture(ItemStack stack, AbstractClientPlayer player) {
        return WING_TEXTURE;
    }

    private static boolean isCelestweaveChest(ItemStack stack) {
        if (stack.getItem() instanceof CelestweaveCoreItem) {
            return true;
        }
        return stack.getItem() instanceof PhaseLockProjectionItem projection
                && projection.equipmentSlot() == EquipmentSlot.CHEST;
    }
}
