package com.moakiee.ae2lt.part;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import appeng.util.inv.AppEngInternalInventory;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopTerminalDraft;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternTerminalDraft;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternTerminalHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalState;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

public final class TianshuPatternEncodingTerminalPart extends PatternEncodingTerminalPart
        implements TianshuPatternTerminalHost {
    @PartModels
    private static final ResourceLocation MODEL_OFF = new ResourceLocation(AE2LightningTech.MODID, "part/tianshu_pattern_encoding_terminal_off");
    @PartModels
    private static final ResourceLocation MODEL_ON = new ResourceLocation(AE2LightningTech.MODID, "part/tianshu_pattern_encoding_terminal_on");

    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL = new PartModel(
            MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private final TianshuTerminalState terminalState = new TianshuTerminalState();

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
        return terminalState.getEncodingMode();
    }

    @Override
    public void setTianshuEncodingMode(TianshuEncodingMode mode) {
        if (terminalState.setEncodingMode(mode)) {
            markForSave();
        }
    }

    @Override
    public boolean isMaintainableView() {
        return terminalState.isMaintainableView();
    }

    @Override
    public void setMaintainableView(boolean enabled) {
        if (terminalState.setMaintainableView(enabled)) {
            markForSave();
        }
    }

    @Nullable
    @Override
    public ClosedLoopTerminalDraft getClosedLoopTerminalDraft() {
        return terminalState.getClosedLoopDraft();
    }

    @Override
    public void setClosedLoopTerminalDraft(@Nullable ClosedLoopTerminalDraft draft) {
        if (terminalState.setClosedLoopDraft(draft)) {
            markForSave();
        }
    }

    @Nullable
    @Override
    public ProcessingPatternTerminalDraft getProcessingPatternTerminalDraft() {
        return terminalState.getProcessingDraft();
    }

    @Override
    public void setProcessingPatternTerminalDraft(
            @Nullable ProcessingPatternTerminalDraft draft) {
        if (terminalState.setProcessingDraft(draft)) {
            markForSave();
        }
    }

    @Override
    public void readFromNBT(CompoundTag data) {
        super.readFromNBT(data);
        terminalState.read(data, TianshuTerminalState.NbtFormat.PART);
    }

    @Override
    public void writeToNBT(CompoundTag data) {
        super.writeToNBT(data);
        terminalState.write(data, TianshuTerminalState.NbtFormat.PART);
    }
}
