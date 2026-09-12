package com.moakiee.ae2lt.integration.jade;

import appeng.integration.modules.igtooltip.TooltipIds;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.block.PigmeeCrystalCatalyzerBlock;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** AE2's inherited block-entity provider also visits this standalone variant. */
public final class PigmeeCrystalCatalyzerJadeProvider implements IBlockComponentProvider {
    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "pigmee_crystal_catalyzer");
    }

    @Override
    public int getDefaultPriority() {
        return 1001; // After AE2's grid-state provider (1000).
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlock() instanceof PigmeeCrystalCatalyzerBlock) {
            tooltip.remove(TooltipIds.GRID_NODE_STATE);
        }
    }
}
