package com.moakiee.ae2lt.client.render;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.client.render.cablebus.CableBusRenderState;
import appeng.client.render.cablebus.CubeBuilder;

import com.moakiee.ae2lt.AE2LightningTech;

public final class OverloadedCableRenderHelper {
    private static final String OVERLOAD_CABLE_LINE_TEXTURE_FOLDER = "part/cable/overload/";
    private static final String OVERLOAD_CABLE_CORE_TEXTURE_FOLDER = "part/cable/core/overload/";

    private OverloadedCableRenderHelper() {
    }

    public static void addCableQuads(CableBusRenderState renderState, List<BakedQuad> quadsOut) {
        var cableType = renderState.getCableType();
        if (cableType == AECableType.NONE) {
            return;
        }

        var textureCore = getCoreTexture(renderState.getCableColor());
        var textureLine = getLineTexture(renderState.getCableColor());
        var connectionTypes = renderState.getConnectionTypes();

        boolean noAttachments = !renderState.getAttachments().values().stream()
                .anyMatch(IPartModel::requireCableConnection);
        if (noAttachments && isStraightLine(cableType, connectionTypes)) {
            addStraightDenseConnection(connectionTypes.keySet().iterator().next(), textureLine, quadsOut);
            return;
        }

        addDenseCore(textureCore, quadsOut);

        for (Entry<Direction, AECableType> connection : connectionTypes.entrySet()) {
            var facing = connection.getKey();
            var connectionType = connection.getValue();
            var cableBusAdjacent = renderState.getCableBusAdjacent().contains(facing);

            if (connectionType == AECableType.GLASS
                    || connectionType == AECableType.COVERED
                    || connectionType == AECableType.SMART) {
                addCoveredConnection(facing, connectionType, cableBusAdjacent, textureLine, quadsOut);
            } else {
                addDenseConnection(facing, textureLine, quadsOut);
            }
        }
    }

    private static TextureAtlasSprite getCoreTexture(AEColor color) {
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        return atlas.apply(ResourceLocation.fromNamespaceAndPath(
                AE2LightningTech.MODID,
                OVERLOAD_CABLE_CORE_TEXTURE_FOLDER + color.name().toLowerCase(Locale.ROOT)));
    }

    private static TextureAtlasSprite getLineTexture(AEColor color) {
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        return atlas.apply(ResourceLocation.fromNamespaceAndPath(
                AE2LightningTech.MODID,
                OVERLOAD_CABLE_LINE_TEXTURE_FOLDER + color.name().toLowerCase(Locale.ROOT)));
    }

    private static boolean isStraightLine(AECableType cableType, EnumMap<Direction, AECableType> sides) {
        var it = sides.entrySet().iterator();
        if (!it.hasNext()) {
            return false;
        }

        var firstConnection = it.next();
        var firstSide = firstConnection.getKey();
        var firstType = firstConnection.getValue();

        if (!it.hasNext()) {
            return false;
        }
        if (firstSide.getOpposite() != it.next().getKey()) {
            return false;
        }
        if (it.hasNext()) {
            return false;
        }

        var secondType = sides.get(firstSide.getOpposite());
        return firstType == secondType && cableType == firstType;
    }

    private static void addDenseCore(TextureAtlasSprite texture, List<BakedQuad> quadsOut) {
        var cubeBuilder = new CubeBuilder(quadsOut);
        cubeBuilder.setTexture(texture);
        cubeBuilder.addCube(3, 3, 3, 13, 13, 13);
    }

    private static void addDenseConnection(Direction facing, TextureAtlasSprite texture, List<BakedQuad> quadsOut) {
        var cubeBuilder = new CubeBuilder(quadsOut);
        cubeBuilder.setDrawFaces(EnumSet.complementOf(EnumSet.of(facing)));
        cubeBuilder.setTexture(texture);
        addDenseCableSizedCube(facing, cubeBuilder);
    }

    private static void addCoveredConnection(Direction facing, AECableType connectionType,
            boolean cableBusAdjacent, TextureAtlasSprite texture, List<BakedQuad> quadsOut) {
        var cubeBuilder = new CubeBuilder(quadsOut);
        cubeBuilder.setDrawFaces(EnumSet.complementOf(EnumSet.of(facing)));
        cubeBuilder.setTexture(texture);

        if (connectionType != AECableType.GLASS && !cableBusAdjacent) {
            addBigCoveredCableSizedCube(facing, cubeBuilder);
        }

        addCoveredCableSizedCube(facing, cubeBuilder);
    }

    private static void addStraightDenseConnection(Direction facing, TextureAtlasSprite texture,
            List<BakedQuad> quadsOut) {
        var cubeBuilder = new CubeBuilder(quadsOut);
        cubeBuilder.setTexture(texture);
        setStraightCableUVs(cubeBuilder, facing, 3 / 16f, 13 / 16f);
        addStraightDenseCableSizedCube(facing, cubeBuilder);
    }

    private static void setStraightCableUVs(CubeBuilder cubeBuilder, Direction facing, float x, float y) {
        switch (facing) {
            case DOWN, UP -> {
                cubeBuilder.setCustomUv(Direction.NORTH, x, 0, y, x);
                cubeBuilder.setCustomUv(Direction.EAST, x, 0, y, x);
                cubeBuilder.setCustomUv(Direction.SOUTH, x, 0, y, x);
                cubeBuilder.setCustomUv(Direction.WEST, x, 0, y, x);
            }
            case EAST, WEST -> {
                cubeBuilder.setCustomUv(Direction.UP, 0, x, x, y);
                cubeBuilder.setCustomUv(Direction.DOWN, 0, x, x, y);
                cubeBuilder.setCustomUv(Direction.NORTH, 0, x, x, y);
                cubeBuilder.setCustomUv(Direction.SOUTH, 0, x, x, y);
            }
            case NORTH, SOUTH -> {
                cubeBuilder.setCustomUv(Direction.UP, x, 0, y, x);
                cubeBuilder.setCustomUv(Direction.DOWN, x, 0, y, x);
                cubeBuilder.setCustomUv(Direction.EAST, 0, x, x, y);
                cubeBuilder.setCustomUv(Direction.WEST, 0, x, x, y);
            }
        }
    }

    private static void addStraightDenseCableSizedCube(Direction facing, CubeBuilder cubeBuilder) {
        switch (facing) {
            case DOWN, UP -> {
                cubeBuilder.setUvRotation(Direction.EAST, 2);
                cubeBuilder.addCube(3, -0.01f, 3, 13, 16.01f, 13);
                cubeBuilder.setUvRotation(Direction.EAST, 0);
            }
            case EAST, WEST -> {
                cubeBuilder.setUvRotation(Direction.SOUTH, 2);
                cubeBuilder.setUvRotation(Direction.NORTH, 2);
                cubeBuilder.addCube(-0.01f, 3, 3, 16.01f, 13, 13);
                cubeBuilder.setUvRotation(Direction.SOUTH, 0);
                cubeBuilder.setUvRotation(Direction.NORTH, 0);
            }
            case NORTH, SOUTH -> {
                cubeBuilder.setUvRotation(Direction.EAST, 2);
                cubeBuilder.setUvRotation(Direction.WEST, 2);
                cubeBuilder.addCube(3, 3, -0.01f, 13, 13, 16.01f);
                cubeBuilder.setUvRotation(Direction.EAST, 0);
                cubeBuilder.setUvRotation(Direction.WEST, 0);
            }
        }
    }

    private static void addDenseCableSizedCube(Direction facing, CubeBuilder cubeBuilder) {
        switch (facing) {
            case DOWN -> cubeBuilder.addCube(4, 0, 4, 12, 5, 12);
            case EAST -> cubeBuilder.addCube(11, 4, 4, 16, 12, 12);
            case NORTH -> cubeBuilder.addCube(4, 4, 0, 12, 12, 5);
            case SOUTH -> cubeBuilder.addCube(4, 4, 11, 12, 12, 16);
            case UP -> cubeBuilder.addCube(4, 11, 4, 12, 16, 12);
            case WEST -> cubeBuilder.addCube(0, 4, 4, 5, 12, 12);
        }
    }

    private static void addCoveredCableSizedCube(Direction facing, CubeBuilder cubeBuilder) {
        switch (facing) {
            case DOWN -> cubeBuilder.addCube(6, 0, 6, 10, 5, 10);
            case EAST -> cubeBuilder.addCube(11, 6, 6, 16, 10, 10);
            case NORTH -> cubeBuilder.addCube(6, 6, 0, 10, 10, 5);
            case SOUTH -> cubeBuilder.addCube(6, 6, 11, 10, 10, 16);
            case UP -> cubeBuilder.addCube(6, 11, 6, 10, 16, 10);
            case WEST -> cubeBuilder.addCube(0, 6, 6, 5, 10, 10);
        }
    }

    private static void addBigCoveredCableSizedCube(Direction facing, CubeBuilder cubeBuilder) {
        switch (facing) {
            case DOWN -> cubeBuilder.addCube(5, 0, 5, 11, 4, 11);
            case EAST -> cubeBuilder.addCube(12, 5, 5, 16, 11, 11);
            case NORTH -> cubeBuilder.addCube(5, 5, 0, 11, 11, 4);
            case SOUTH -> cubeBuilder.addCube(5, 5, 12, 11, 11, 16);
            case UP -> cubeBuilder.addCube(5, 12, 5, 11, 16, 11);
            case WEST -> cubeBuilder.addCube(0, 5, 5, 4, 11, 11);
        }
    }
}
