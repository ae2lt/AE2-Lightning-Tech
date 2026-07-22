package com.moakiee.ae2lt.mixin;

import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.common.MEStorageMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MEStorageMenu.class)
public interface MEStorageMenuAccessor {
    @Accessor("updateHelper")
    IncrementalUpdateHelper ae2lt$getUpdateHelper();
}
