package com.moakiee.ae2lt.part;

import com.moakiee.ae2lt.grid.OverloadedGridNodeOwner;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.world.entity.player.Player;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AEColor;
import appeng.api.util.AECableType;
import appeng.items.parts.ColoredPartItem;
import appeng.parts.networking.CoveredDenseCablePart;
import appeng.parts.networking.IUsedChannelProvider;

/**
 * Minimal AE2LT-owned cable part shell.
 * We keep it as a separate part type now so future owner-scoped channel logic
 * can special-case only this cable, without touching vanilla dense cable items.
 * <p>
 * Important: this class is the only cable owner type that AE2LT's 128-channel
 * logic and extra tooltip display should apply to.
 */
public class OverloadedCablePart extends CoveredDenseCablePart
        implements OverloadedGridNodeOwner, IUsedChannelProvider {

    public OverloadedCablePart(ColoredPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        // AE2 1.21.1 uses IManagedGridNode#setTagName here.
        // If your target AE2/MC version renames this API, adjust this override accordingly.
        return super.createMainNode().setTagName("overloaded_cable");
    }

    @Override
    public AECableType getCableConnectionType() {
        // Stay on the covered dense cable path for maximum compatibility in this stage.
        return AECableType.DENSE_COVERED;
    }

    @Override
    public int getUsedChannelsInfo() {
        // Reuse AE2's real connection usage values rather than tracking a duplicate state
        // for Jade/WTHIT/TOP.
        int used = 0;
        IGridNode node = this.getGridNode();
        if (node != null && node.isActive()) {
            for (var connection : node.getConnections()) {
                used = Math.max(used, connection.getUsedChannels());
            }
        }
        return used;
    }

    @Override
    public int getMaxChannelsInfo() {
        if (this.getGridNode() == null) {
            return 0;
        }
        return -1;
    }

    @Override
    public boolean changeColor(AEColor newColor, Player who) {
        if (this.getCableColor() == newColor) {
            return false;
        }

        var newPart = ModItems.getOverloadedCable(newColor);

        if (isClientSide()) {
            return true;
        }

        setPartItem(newPart);
        getMainNode().setGridColor(getCableColor());
        getHost().partChanged();
        getHost().markForUpdate();
        getHost().markForSave();
        return true;
    }
}
