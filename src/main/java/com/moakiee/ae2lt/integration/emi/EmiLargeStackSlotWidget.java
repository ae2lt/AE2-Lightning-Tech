package com.moakiee.ae2lt.integration.emi;

import java.util.List;

import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.SlotWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

/** EMI counterpart of {@code LargeStackJeiItemRenderer}. */
final class EmiLargeStackSlotWidget extends SlotWidget {
    EmiLargeStackSlotWidget(EmiIngredient stack, int x, int y) {
        super(stack, x, y);
    }

    @Override
    public void drawStack(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        var bounds = getBounds();
        int iconX = bounds.x() + (bounds.width() - 16) / 2;
        int iconY = bounds.y() + (bounds.height() - 16) / 2;

        // EMI normally prints the raw long amount. Render only the icon here,
        // then use the same compact 0.75-scale counter as our GUIs and JEI.
        getStack().render(
                graphics,
                iconX,
                iconY,
                delta,
                EmiIngredient.RENDER_ICON
                        | EmiIngredient.RENDER_INGREDIENT
                        | EmiIngredient.RENDER_REMAINDER);
        LargeStackCountRenderer.renderCountAt(
                graphics,
                Minecraft.getInstance().font,
                iconX,
                iconY,
                getStack().getAmount());
    }

    @Override
    protected void addSlotTooltip(List<ClientTooltipComponent> tooltip) {
        long count = getStack().getAmount();
        if (count > 1) {
            var line = Component.translatable("ae2lt.gui.slot_count", String.format("%,d", count))
                    .withStyle(ChatFormatting.GRAY);
            tooltip.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        }
        super.addSlotTooltip(tooltip);
    }
}
