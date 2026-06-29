package com.moakiee.ae2lt.client.ctm;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * Generic connected-texture baked model (Mekanism-style compact CTM).
 *
 * <p>Connection info is computed once per block in {@link #getModelData} (which has
 * level/pos access) and carried via {@link #CONNECTION}; {@link #getQuads} then has
 * no world access and only consumes that data. When the predicate is inactive
 * (e.g. unformed glass) or there is no data (item rendering) the plain base texture
 * is drawn instead of the CTM.
 */
public class ConnectedTextureBakedModel implements IDynamicBakedModel {

    public static final ModelProperty<CtmConnectionState> CONNECTION = new ModelProperty<>();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static int DEBUG_COUNT = 0;

    private final TextureAtlasSprite baseSprite;
    private final TextureAtlasSprite ctmSprite;
    private final ConnectionPredicate predicate;
    private final ChunkRenderTypeSet renderTypes;
    private final boolean ambientOcclusion;
    private final boolean gui3d;
    private final boolean usesBlockLight;

    public ConnectedTextureBakedModel(TextureAtlasSprite baseSprite, TextureAtlasSprite ctmSprite,
            ConnectionPredicate predicate, ChunkRenderTypeSet renderTypes,
            boolean ambientOcclusion, boolean gui3d, boolean usesBlockLight) {
        this.baseSprite = baseSprite;
        this.ctmSprite = ctmSprite;
        this.predicate = predicate;
        this.renderTypes = renderTypes;
        this.ambientOcclusion = ambientOcclusion;
        this.gui3d = gui3d;
        this.usesBlockLight = usesBlockLight;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        if (!predicate.isActive(level, pos, state)) {
            if (DEBUG_COUNT < 48) {
                DEBUG_COUNT++;
                System.out.println("[CTM] " + pos + " active=false");
            }
            return modelData;
        }
        boolean[] culled = new boolean[DIRECTIONS.length];
        int[] edges = new int[DIRECTIONS.length];
        int[] corners = new int[DIRECTIONS.length];
        for (Direction face : DIRECTIONS) {
            int idx = face.get3DDataValue();
            culled[idx] = predicate.connects(level, pos, state, face);
            int mask = 0;
            for (int edge = 0; edge < 4; edge++) {
                if (predicate.connects(level, pos, state, CtmFaceGeometry.neighborDir(face, edge))) {
                    mask |= (1 << edge);
                }
            }
            edges[idx] = mask;
            int cornerMask = 0;
            for (var quadrant : CtmTileSelector.Quadrant.values()) {
                if (predicate.connects(level, pos, state, CtmFaceGeometry.cornerPos(pos, face, quadrant))) {
                    cornerMask |= 1 << quadrant.ordinal();
                }
            }
            corners[idx] = cornerMask;
        }
        if (DEBUG_COUNT < 48) {
            DEBUG_COUNT++;
            StringBuilder sb = new StringBuilder();
            for (Direction f : DIRECTIONS) {
                sb.append(f).append("=e").append(edges[f.get3DDataValue()])
                        .append("/k").append(corners[f.get3DDataValue()])
                        .append("/c").append(culled[f.get3DDataValue()] ? 1 : 0).append(' ');
            }
            System.out.println("[CTM] " + pos + " ctm=" + ctmSprite.contents().width() + "x"
                    + ctmSprite.contents().height() + " " + sb);
        }
        return modelData.derive().with(CONNECTION, new CtmConnectionState(culled, edges, corners)).build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
            ModelData extraData, @Nullable RenderType renderType) {
        if (side == null) {
            return List.of();
        }
        CtmConnectionState conn = extraData.get(CONNECTION);
        if (conn == null) {
            // Inactive (unformed) or no level (item) -> plain base face.
            return List.of(CtmFaceGeometry.fullFace(side, baseSprite));
        }
        if (conn.culled(side)) {
            return List.of();
        }
        int edges = conn.edges(side);
        int corners = conn.corners(side);
        List<BakedQuad> quads = new ArrayList<>(4);
        for (int sq = 0; sq < 2; sq++) {
            for (int tq = 0; tq < 2; tq++) {
                var tile = CtmTileSelector.select(CtmTileSelector.quadrant(sq, tq), edges, corners);
                TextureAtlasSprite sprite = tile.source() == CtmTileSelector.Source.BASE ? baseSprite : ctmSprite;
                quads.add(CtmFaceGeometry.quadrant(side, sq, tq, tile, sprite));
            }
        }
        return quads;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return renderTypes;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return ambientOcclusion;
    }

    @Override
    public boolean isGui3d() {
        return gui3d;
    }

    @Override
    public boolean usesBlockLight() {
        return usesBlockLight;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return baseSprite;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
