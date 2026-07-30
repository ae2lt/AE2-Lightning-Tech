package com.moakiee.ae2lt.client;

import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternUploadRouting;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Completes Alt recipe-viewer uploads while the recipe page remains open. The normal terminal
 * screen still owns every manual or modifier-configured upload.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class TianshuDirectUploadClient {
    private static WeakReference<TianshuPatternEncodingTermMenu> heldMenu =
            new WeakReference<>(null);
    private static WeakReference<Screen> heldRecipeScreen = new WeakReference<>(null);
    private static WeakReference<TianshuPatternEncodingTermMenu> awaitingResult =
            new WeakReference<>(null);
    private static Component awaitingTargetName;

    private TianshuDirectUploadClient() {
    }

    public static boolean holdRecipeScreen(
            TianshuPatternEncodingTermMenu menu, Screen recipeScreen) {
        if (menu == null || recipeScreen == null || !menu.hasPendingDirectUpload()) return false;
        heldMenu = new WeakReference<>(menu);
        heldRecipeScreen = new WeakReference<>(recipeScreen);
        return true;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        handleUploadResult(minecraft);

        var menu = heldMenu.get();
        var recipeScreen = heldRecipeScreen.get();
        if (menu == null || recipeScreen == null) {
            clearHeldRecipe();
            return;
        }
        if (minecraft.player == null || minecraft.player.containerMenu != menu
                || minecraft.screen != recipeScreen) {
            clearHeldRecipe();
            return;
        }
        if (!menu.hasPendingDirectUpload()) {
            clearHeldRecipe();
            return;
        }
        if (!menu.hasTriggeredUploadAck()) return;

        var stack = firstEncodedPattern(menu);
        TianshuRecipeTransferContext.acceptEncodedPattern(menu, stack);
        if (!TianshuRecipeTransferContext.isEncodingResultReady(menu, stack)) return;
        var route = minecraft.level == null
                ? TianshuPatternUploadRouting.Route.INVALID
                : TianshuPatternUploadRouting.classify(stack, minecraft.level);
        switch (route) {
            case CLOSED_LOOP_STORAGE, CRAFTING_ASSEMBLER -> {
                if (consumeDirectRequest(menu)) {
                    menu.uploadEncodedPattern();
                    awaitResult(menu, null);
                }
                clearHeldRecipe();
            }
            case PROCESSING_PROVIDER -> {
                if (!menu.requestDirectUploadTargetsAfterEncoding()) return;
                if (!menu.hasFreshDirectUploadTargets()) return;
                var selection = TianshuUploadSourceSelection.collect(menu);
                var target = TianshuUploadTargetMatcher.findUniqueCandidate(
                        menu.getUploadTargets(), selection.initialQuery());
                if (target != null && consumeDirectRequest(menu)) {
                    menu.uploadTianshuPatternToTarget(target.group());
                    awaitResult(menu, target.group().name());
                    clearHeldRecipe();
                } else {
                    // No safe unique target: return to the terminal without consuming the request.
                    // Its normal update path will open the visible provider picker.
                    clearHeldRecipe();
                    recipeScreen.onClose();
                }
            }
            case INVALID -> {
                clearHeldRecipe();
                recipeScreen.onClose();
            }
        }
    }

    private static boolean consumeDirectRequest(TianshuPatternEncodingTermMenu menu) {
        return menu.consumeTriggeredUpload() && menu.consumeDirectUploadRequest();
    }

    private static ItemStack firstEncodedPattern(TianshuPatternEncodingTermMenu menu) {
        return menu.getSlots(SlotSemantics.ENCODED_PATTERN).stream()
                .map(slot -> slot.getItem())
                .filter(stack -> !stack.isEmpty())
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }

    private static void awaitResult(
            TianshuPatternEncodingTermMenu menu, Component targetName) {
        awaitingResult = new WeakReference<>(menu);
        awaitingTargetName = targetName == null ? null : targetName.copy();
    }

    private static void handleUploadResult(Minecraft minecraft) {
        var menu = awaitingResult.get();
        if (menu == null) {
            clearAwaitingResult();
            return;
        }
        if (minecraft.player == null || minecraft.player.containerMenu != menu) {
            clearAwaitingResult();
            return;
        }
        if (menu.uploadState != 1 && menu.uploadState != 3) return;
        Component result = menu.uploadState == 1
                ? awaitingTargetName == null
                        ? Component.translatable("ae2lt.tianshu.upload.success")
                        : Component.translatable(
                                "ae2lt.tianshu.upload.success_target", awaitingTargetName)
                : Component.translatable("ae2lt.tianshu.upload.failed");
        minecraft.player.displayClientMessage(result, false);
        clearAwaitingResult();
    }

    private static void clearAwaitingResult() {
        awaitingResult = new WeakReference<>(null);
        awaitingTargetName = null;
    }

    private static void clearHeldRecipe() {
        heldMenu = new WeakReference<>(null);
        heldRecipeScreen = new WeakReference<>(null);
    }
}
