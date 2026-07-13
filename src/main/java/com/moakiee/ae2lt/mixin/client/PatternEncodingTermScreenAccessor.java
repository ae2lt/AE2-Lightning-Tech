package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.me.items.EncodingModePanel;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.widgets.TabButton;
import appeng.parts.encoding.EncodingMode;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PatternEncodingTermScreen.class)
public interface PatternEncodingTermScreenAccessor {
    @Accessor("modePanels")
    Map<EncodingMode, EncodingModePanel> ae2lt$getModePanels();

    @Accessor("modeTabButtons")
    Map<EncodingMode, TabButton> ae2lt$getModeTabButtons();
}
