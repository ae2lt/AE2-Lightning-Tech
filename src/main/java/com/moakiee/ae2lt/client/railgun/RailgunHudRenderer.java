package com.moakiee.ae2lt.client.railgun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.config.RailgunDefaults;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;
import com.moakiee.ae2lt.registry.ModDataComponents;

/**
 * Renders a small charge-progress bar near the crosshair while the player is
 * holding right-click on the railgun.
 *
 * <p>The bar is a 13x19 lightning-bolt icon placed immediately to the right of
 * the crosshair. The empty texture is drawn as the background; the full
 * texture is revealed progressively from the bottom up as charge builds.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class RailgunHudRenderer {

    private static final ResourceLocation EMPTY_TEX = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/hud/lightning_charging_bar.png");
    private static final ResourceLocation FULL_TEX = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/hud/lightning_charging_bar_full.png");

    private static final int ICON_W = 13;
    private static final int ICON_H = 19;
    /** Horizontal gap between the crosshair center and the icon's left edge. */
    private static final int CROSSHAIR_OFFSET_X = 10;

    private RailgunHudRenderer() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Charge-progress bar only while right-click-charging.
        if (!mc.player.isUsingItem()) return;
        ItemStack stack = mc.player.getUseItem();
        if (!(stack.getItem() instanceof ElectromagneticRailgunItem)) return;

        long ticks = stack.getOrDefault(ModDataComponents.RAILGUN_CHARGE_TICKS.get(), 0L);
        int t3 = RailgunDefaults.CHARGE_TICKS_TIER3;
        float progress = Math.min(1.0f, (float) ticks / (float) t3);

        GuiGraphics gfx = e.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int x = w / 2 + CROSSHAIR_OFFSET_X;
        int y = (h - ICON_H) / 2;

        // Empty bolt as the always-visible background.
        gfx.blit(EMPTY_TEX, x, y, 0, 0, ICON_W, ICON_H, ICON_W, ICON_H);

        // Reveal the lit bolt from the bottom up by sampling the matching
        // bottom slice of the full texture; this keeps pixel alignment exact
        // even at fractional progress values.
        int filledH = Math.round(ICON_H * progress);
        if (filledH > 0) {
            int emptyTop = ICON_H - filledH;
            gfx.blit(FULL_TEX, x, y + emptyTop, 0, emptyTop, ICON_W, filledH, ICON_W, ICON_H);
        }

        ensureUserStillUsing(mc.player, stack);
    }

    private static void ensureUserStillUsing(LivingEntity user, ItemStack stack) {
        // No-op; placeholder hook for future state validation.
    }
}
