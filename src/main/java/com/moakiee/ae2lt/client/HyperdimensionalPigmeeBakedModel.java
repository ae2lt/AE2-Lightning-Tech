package com.moakiee.ae2lt.client;

import net.minecraft.client.resources.model.BakedModel;

/**
 * Marks the Hyperdimensional Pigmee item for the custom renderer while retaining
 * all of the ordinary Pigmee model's transforms.
 */
final class HyperdimensionalPigmeeBakedModel extends SpinningFumoBakedModel {
    HyperdimensionalPigmeeBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public boolean isCustomRenderer() {
        return true;
    }

    BakedModel baseModel() {
        return originalModel;
    }
}
