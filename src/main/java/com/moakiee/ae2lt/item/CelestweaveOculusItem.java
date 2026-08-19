package com.moakiee.ae2lt.item;

import java.util.function.Consumer;

import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import com.moakiee.ae2lt.celestweave.ArmorPart;
import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.client.CelestweaveHeadRenderExtensions;

public final class CelestweaveOculusItem extends BaseCelestweaveArmorItem {
    public CelestweaveOculusItem(Properties properties) {
        super(ArmorPart.HEAD, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(CelestweaveHeadRenderExtensions.INSTANCE);
    }
}
