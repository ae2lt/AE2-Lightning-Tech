package com.moakiee.ae2lt.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.Button;

import appeng.client.gui.widgets.VerticalButtonBar;

@Mixin(VerticalButtonBar.class)
public interface VerticalButtonBarAccessor {
    @Accessor("buttons")
    List<Button> ae2lt$getButtons();
}
