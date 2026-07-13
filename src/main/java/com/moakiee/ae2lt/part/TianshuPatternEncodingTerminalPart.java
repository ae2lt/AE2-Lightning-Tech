package com.moakiee.ae2lt.part;

import appeng.api.parts.IPartItem;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternTerminalHost;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

public final class TianshuPatternEncodingTerminalPart extends PatternEncodingTerminalPart
        implements TianshuPatternTerminalHost {
    private static final String TAG_MODE = "TianshuEncodingMode";
    private TianshuEncodingMode tianshuMode = TianshuEncodingMode.CRAFTING;

    public TianshuPatternEncodingTerminalPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public MenuType<?> getMenuType(Player player) {
        return TianshuPatternEncodingTermMenu.TYPE;
    }

    @Override
    public TianshuEncodingMode getTianshuEncodingMode() {
        return tianshuMode;
    }

    @Override
    public void setTianshuEncodingMode(TianshuEncodingMode mode) {
        if (mode != null && tianshuMode != mode) {
            tianshuMode = mode;
            markForSave();
        }
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        try {
            tianshuMode = TianshuEncodingMode.valueOf(data.getString(TAG_MODE));
        } catch (IllegalArgumentException ignored) {
            tianshuMode = TianshuEncodingMode.CRAFTING;
        }
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putString(TAG_MODE, tianshuMode.name());
    }
}
