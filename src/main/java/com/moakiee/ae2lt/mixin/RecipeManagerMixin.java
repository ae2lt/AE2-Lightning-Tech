package com.moakiee.ae2lt.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.moakiee.ae2lt.util.RecipeManagerByTypeAccess;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin implements RecipeManagerByTypeAccess {
    @Shadow
    protected abstract <C extends Container, T extends Recipe<C>> Map<ResourceLocation, T> byType(RecipeType<T> type);

    @Override
    public <C extends Container, T extends Recipe<C>> Map<ResourceLocation, T> ae2lt$getByType(RecipeType<T> type) {
        return this.byType(type);
    }
}
