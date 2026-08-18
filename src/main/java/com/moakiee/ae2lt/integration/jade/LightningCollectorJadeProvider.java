package com.moakiee.ae2lt.integration.jade;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.LightningCollectorBlockEntity;
import com.moakiee.ae2lt.registry.ModBlocks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class LightningCollectorJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final String TAG_COOLDOWN_TICKS = "CooldownTicks";
    private static final ResourceLocation UID =
            new ResourceLocation(AE2LightningTech.MODID, "lightning_collector");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof LightningCollectorBlockEntity collector) {
            data.putInt(TAG_COOLDOWN_TICKS, collector.getCooldownTicks());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getBlockState().is(ModBlocks.LIGHTNING_COLLECTOR.get())) {
            return;
        }
        var data = accessor.getServerData();
        if (!data.contains(TAG_COOLDOWN_TICKS, Tag.TAG_INT)) {
            return;
        }

        int cooldownTicks = data.getInt(TAG_COOLDOWN_TICKS);
        if (cooldownTicks <= 0) {
            return;
        }

        int cooldownSeconds = Math.max(1, (cooldownTicks + 19) / 20);
        tooltip.add(Component.translatable("jade.ae2lt.lightning_collector.cooldown", cooldownSeconds));
    }
}

