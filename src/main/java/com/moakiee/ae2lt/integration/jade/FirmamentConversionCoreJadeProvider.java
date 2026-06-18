package com.moakiee.ae2lt.integration.jade;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.FirmamentConversionCoreBlockEntity;
import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class FirmamentConversionCoreJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "firmament_conversion_core");
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_PROCESS_TIME = "ProcessTime";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof FirmamentConversionCoreBlockEntity core) {
            data.putInt(TAG_PROGRESS, core.getProgress());
            data.putInt(TAG_PROCESS_TIME, core.getProcessTime());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getBlockState().is(ModBlocks.FIRMAMENT_CONVERSION_CORE.get())) {
            return;
        }

        CompoundTag data = accessor.getServerData();
        int processTime = data.getInt(TAG_PROCESS_TIME);
        if (processTime <= 0) {
            tooltip.add(Component.translatable("jade.ae2lt.firmament_conversion_core.idle"));
            return;
        }

        int progress = data.getInt(TAG_PROGRESS);
        int percent = Math.min(100, progress * 100 / processTime);
        tooltip.add(Component.translatable("jade.ae2lt.firmament_conversion_core.progress", percent));
    }
}
