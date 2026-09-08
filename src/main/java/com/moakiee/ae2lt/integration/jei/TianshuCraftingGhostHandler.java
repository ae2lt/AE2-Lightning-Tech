package com.moakiee.ae2lt.integration.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.slot.FakeSlot;
import com.moakiee.ae2lt.client.TianshuCraftingTermScreen;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

@SuppressWarnings("rawtypes")
public final class TianshuCraftingGhostHandler implements IGhostIngredientHandler<TianshuCraftingTermScreen> {
    @Override public <I> List<Target<I>> getTargetsTyped(TianshuCraftingTermScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        var stack = mark(ingredient.getIngredient());
        if (stack.isEmpty()) return List.of();
        var targets = new ArrayList<Target<I>>();
        for (Object raw : screen.getMenu().slots) {
            Slot slot = (Slot) raw;
            if (!(slot instanceof FakeSlot fake) || !fake.isSlotEnabled() || slot.x < 0 || slot.y < 0 || !fake.canSetFilterTo(stack)) continue;
            targets.add(new Target<>() {
                @Override public Rect2i getArea() { return new Rect2i(screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y, 16, 16); }
                @Override public void accept(I value) { fake.setFilterTo(mark(value)); }
            });
        }
        return targets;
    }
    private static ItemStack mark(Object value) {
        if (value instanceof ItemStack stack) return stack.copy();
        if (value instanceof FluidStack fluid && !fluid.isEmpty()) return GenericStack.wrapInItemStack(AEFluidKey.of(fluid), 1);
        if (value instanceof GenericStack generic) return GenericStack.wrapInItemStack(generic);
        if (value instanceof AEKey key) return GenericStack.wrapInItemStack(key, 1);
        return ItemStack.EMPTY;
    }
    @Override public void onComplete() {}
}
