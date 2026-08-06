package com.moakiee.ae2lt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.locator.MenuLocator;

// Exposes MenuTypeBuilder internals so 1.21's `buildUnregistered(ResourceLocation)`
// behavior can be reimplemented without registering the type into ae2's queue.
@Mixin(MenuTypeBuilder.class)
public interface MenuTypeBuilderAccessor<M extends AEBaseMenu, I> {
    @Accessor(value = "id", remap = false)
    void ae2lt$setId(ResourceLocation id);

    @Accessor(value = "id", remap = false)
    ResourceLocation ae2lt$getId();

    @Accessor(value = "menuType", remap = false)
    void ae2lt$setMenuType(MenuType<M> menuType);

    @Accessor(value = "menuType", remap = false)
    MenuType<M> ae2lt$getMenuType();

    @Invoker(value = "fromNetwork", remap = false)
    M ae2lt$invokeFromNetwork(int containerId, Inventory inv, FriendlyByteBuf packetBuf);

    @Invoker(value = "open", remap = false)
    boolean ae2lt$invokeOpen(Player player, MenuLocator locator, boolean fromSubMenu);
}
