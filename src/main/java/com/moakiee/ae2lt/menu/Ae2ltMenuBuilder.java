package com.moakiee.ae2lt.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.MenuTypeBuilder;

import com.moakiee.ae2lt.mixin.MenuTypeBuilderAccessor;

/**
 * Re-implements 1.21's {@code MenuTypeBuilder.buildUnregistered(ResourceLocation)}
 * for 1.20.1 by accessing the builder's private state via mixin. The 1.20.1
 * variant of {@code build(String)} forces the {@code ae2:} namespace and queues
 * the type for ae2's own forge registry, neither of which we want here — we
 * register through our own {@link com.moakiee.ae2lt.registry.ModMenuTypes}.
 */
public final class Ae2ltMenuBuilder {
    private Ae2ltMenuBuilder() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <M extends AEBaseMenu, I> MenuType<M> buildUnregistered(
            MenuTypeBuilder<M, I> builder, ResourceLocation id) {
        MenuTypeBuilderAccessor<M, I> accessor = (MenuTypeBuilderAccessor) (Object) builder;
        if (accessor.ae2lt$getMenuType() != null) {
            throw new IllegalStateException("buildUnregistered already called for " + id);
        }
        accessor.ae2lt$setId(id);
        MenuType<M> menuType = IForgeMenuType.create(
                (containerId, inv, buf) -> fromNetwork(accessor, containerId, inv, buf));
        accessor.ae2lt$setMenuType(menuType);
        MenuOpener.addOpener(menuType, accessor::ae2lt$invokeOpen);
        return menuType;
    }

    private static <M extends AEBaseMenu, I> M fromNetwork(
            MenuTypeBuilderAccessor<M, I> accessor,
            int containerId, Inventory inv, FriendlyByteBuf buf) {
        return accessor.ae2lt$invokeFromNetwork(containerId, inv, buf);
    }
}
