package com.moakiee.ae2lt.part;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import appeng.util.inv.AppEngInternalInventory;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopTerminalDraft;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternTerminalHost;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

public final class TianshuPatternEncodingTerminalPart extends PatternEncodingTerminalPart
        implements TianshuPatternTerminalHost {
    @PartModels
    private static final ResourceLocation MODEL_OFF = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "part/tianshu_pattern_encoding_terminal_off");
    @PartModels
    private static final ResourceLocation MODEL_ON = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "part/tianshu_pattern_encoding_terminal_on");

    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL = new PartModel(
            MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private static final String TAG_MODE = "TianshuEncodingMode";
    private static final String TAG_CLOSED_LOOP_DRAFT = "ClosedLoopDraft";
    private TianshuEncodingMode tianshuMode = TianshuEncodingMode.CRAFTING;
    @Nullable private ClosedLoopTerminalDraft closedLoopDraft;

    public TianshuPatternEncodingTerminalPart(IPartItem<?> partItem) {
        super(partItem);

        // This terminal sources blank patterns exclusively from ME storage. Keep AE2's inherited
        // physical slot at zero capacity so parent-menu integrations cannot pull the first 64
        // blanks out of the network. The menu stages one extracted blank directly in the encoded
        // result inventory for the duration of an encoding request.
        var blankPatternInventory = getLogic().getBlankPatternInv();
        if (blankPatternInventory instanceof AppEngInternalInventory inventory) {
            inventory.setMaxStackSize(0, 0);
        }
    }

    @Override
    public MenuType<?> getMenuType(Player player) {
        return TianshuPatternEncodingTermMenu.TYPE;
    }

    @Override
    public IPartModel getStaticModels() {
        return selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
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

    @Nullable
    @Override
    public ClosedLoopTerminalDraft getClosedLoopTerminalDraft() {
        return closedLoopDraft;
    }

    @Override
    public void setClosedLoopTerminalDraft(@Nullable ClosedLoopTerminalDraft draft) {
        if (ClosedLoopTerminalDraft.sameState(closedLoopDraft, draft)) return;
        closedLoopDraft = draft;
        markForSave();
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        try {
            tianshuMode = TianshuEncodingMode.valueOf(data.getString(TAG_MODE));
        } catch (IllegalArgumentException ignored) {
            tianshuMode = TianshuEncodingMode.CRAFTING;
        }
        closedLoopDraft = data.contains(TAG_CLOSED_LOOP_DRAFT, net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? ClosedLoopTerminalDraft.read(data.getCompound(TAG_CLOSED_LOOP_DRAFT), registries)
                : null;
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putString(TAG_MODE, tianshuMode.name());
        if (closedLoopDraft != null) {
            data.put(TAG_CLOSED_LOOP_DRAFT, closedLoopDraft.write(registries));
        } else {
            data.remove(TAG_CLOSED_LOOP_DRAFT);
        }
    }
}
