package com.moakiee.ae2lt.client.ctm;

import java.util.function.Function;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

/**
 * Unbaked geometry for {@code ae2lt:connected_texture}. Resolves the {@code base}
 * and {@code ctm} sprites from the model's {@code textures} block (so they are
 * stitched into the block atlas automatically) and binds the connection predicate.
 */
public class ConnectedTextureGeometry implements IUnbakedGeometry<ConnectedTextureGeometry> {

    private final ResourceLocation connectionId;
    private final ChunkRenderTypeSet renderTypes;
    private final boolean ambientOcclusion;
    private final boolean gui3d;
    private final boolean usesBlockLight;

    public ConnectedTextureGeometry(ResourceLocation connectionId, ChunkRenderTypeSet renderTypes,
            boolean ambientOcclusion, boolean gui3d, boolean usesBlockLight) {
        this.connectionId = connectionId;
        this.renderTypes = renderTypes;
        this.ambientOcclusion = ambientOcclusion;
        this.gui3d = gui3d;
        this.usesBlockLight = usesBlockLight;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
        TextureAtlasSprite base = spriteGetter.apply(context.getMaterial("base"));
        TextureAtlasSprite ctm = spriteGetter.apply(context.getMaterial("ctm"));
        ConnectionPredicate predicate = ConnectionPredicates.get(connectionId);
        return new ConnectedTextureBakedModel(base, ctm, predicate, renderTypes,
                ambientOcclusion, gui3d, usesBlockLight);
    }
}
