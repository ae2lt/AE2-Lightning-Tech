package com.moakiee.ae2lt.device.network;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface DeviceNetworkBinding {
    @Nullable GlobalPos getBoundPos(ItemStack stack);

    void bind(ItemStack stack, GlobalPos pos);

    void unbind(ItemStack stack);

    BindingResolveResult resolve(ItemStack stack, ServerPlayer player);
}
