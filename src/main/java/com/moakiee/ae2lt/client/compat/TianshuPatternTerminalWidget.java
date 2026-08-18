package com.moakiee.ae2lt.client.compat;

import appeng.menu.SlotSemantics;
import com.illusivesoulworks.polymorph.client.recipe.widget.PlayerRecipesWidget;
import com.moakiee.ae2lt.client.TianshuPatternEncodingTermScreen;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Polymorph recipe selector for the custom Tianshu pattern-terminal screen.
 *
 * <p>1.20.1: the polymorph client API moved — the widget base now lives in
 * {@code com.illusivesoulworks.polymorph.client.recipe.widget} (non-API impl)
 * and the registry entry point is {@code PolymorphClient.get()} instead of the
 * 1.21 {@code PolymorphWidgets.getInstance()}.</p>
 */
final class TianshuPatternTerminalWidget extends PlayerRecipesWidget {
    private final TianshuPatternEncodingTermScreen<?> screen;

    TianshuPatternTerminalWidget(TianshuPatternEncodingTermScreen<?> screen) {
        super(screen, screen.getMenu().getSlots(SlotSemantics.CRAFTING_RESULT).get(0));
        this.screen = screen;
    }

    @Override
    public void selectRecipe(ResourceLocation id) {
        super.selectRecipe(id);
        screen.getMenu().getPlayer().level().getRecipeManager().byKey(id)
                .ifPresent(recipe -> screen.getMenu().refreshPolymorphRecipe());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (isCraftingMode()) super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return isCraftingMode() && super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isCraftingMode() {
        return screen.getMenu().tianshuMode == TianshuEncodingMode.CRAFTING;
    }
}
