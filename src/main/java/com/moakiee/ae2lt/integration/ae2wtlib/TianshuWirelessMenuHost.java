package com.moakiee.ae2lt.integration.ae2wtlib;

import appeng.api.implementations.blockentities.IViewCellStorage;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.parts.encoding.PatternEncodingLogic;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternTerminalHost;
import de.mari_023.ae2wtlib.api.AE2wtlibComponents;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import java.util.function.BiConsumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class TianshuWirelessMenuHost extends WTMenuHost
        implements IViewCellStorage, TianshuPatternTerminalHost, IPatternTerminalLogicHost {
    private static final String TAG_TIANSHU_MODE = "Ae2ltTianshuMode";
    private final PatternEncodingLogic logic = new PatternEncodingLogic(this);
    private TianshuEncodingMode mode = TianshuEncodingMode.CRAFTING;

    public TianshuWirelessMenuHost(ItemWT item, Player player, ItemMenuHostLocator locator,
                                   BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
        var tag = getItemStack().getOrDefault(AE2wtlibComponents.PATTERN_ENCODING_LOGIC, new CompoundTag());
        logic.readFromNBT(tag, player.registryAccess());
        try { mode = TianshuEncodingMode.valueOf(tag.getString(TAG_TIANSHU_MODE)); }
        catch (IllegalArgumentException ignored) { mode = TianshuEncodingMode.fromAe2(logic.getMode()); }
    }

    @Override public PatternEncodingLogic getLogic() { return logic; }
    @Override public Level getLevel() { return getPlayer().level(); }
    @Override public TianshuEncodingMode getTianshuEncodingMode() { return mode; }

    @Override
    public void setTianshuEncodingMode(TianshuEncodingMode mode) {
        if (mode != null) {
            this.mode = mode;
            markForSave();
        }
    }

    @Override
    public void markForSave() {
        var tag = getItemStack().getOrDefault(AE2wtlibComponents.PATTERN_ENCODING_LOGIC, new CompoundTag()).copy();
        logic.writeToNBT(tag, getPlayer().registryAccess());
        tag.putString(TAG_TIANSHU_MODE, mode.name());
        getItemStack().set(AE2wtlibComponents.PATTERN_ENCODING_LOGIC, tag);
    }
}
