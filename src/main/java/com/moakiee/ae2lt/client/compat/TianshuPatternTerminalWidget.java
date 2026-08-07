package com.moakiee.ae2lt.client.compat;

import appeng.menu.SlotSemantics;
import com.illusivesoulworks.polymorph.api.client.widgets.PlayerRecipesWidget;
import com.moakiee.ae2lt.client.TianshuPatternEncodingTermScreen;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.ResourceLocation;

/** Polymorph recipe selector for the custom Tianshu pattern-terminal screen. */
final class TianshuPatternTerminalWidget extends PlayerRecipesWidget {
    private static final WidgetSprites OUTPUT = sprites("output_button");
    private static final WidgetSprites CURRENT_OUTPUT = sprites("current_output");
    private static final WidgetSprites SELECTOR = sprites("selector_button");

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

    @Override
    public Pair<WidgetSprites, WidgetSprites> getOutputSprites() {
        return Pair.of(OUTPUT, CURRENT_OUTPUT);
    }

    @Override
    public WidgetSprites getSelectorSprites() {
        return SELECTOR;
    }

    private boolean isCraftingMode() {
        return screen.getMenu().tianshuMode == TianshuEncodingMode.CRAFTING;
    }

    private static WidgetSprites sprites(String name) {
        return new WidgetSprites(
                ResourceLocation.fromNamespaceAndPath("polyeng", name),
                ResourceLocation.fromNamespaceAndPath("polyeng", name + "_highlighted"));
    }
}
