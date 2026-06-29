package com.moakiee.ae2lt.client.ctm;

import java.util.EnumMap;

import org.joml.Vector3f;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

/**
 * Per-face geometry for Mekanism-style compact CTM.
 *
 * <p>Face corners and the face-space neighbour directions are taken from AE2's
 * {@code GlassBakedModel}/{@code RenderHelper} so UV orientation matches vanilla
 * cube faces (no mirroring/flip). {@code CORNERS[side] = [TL, BL, BR, TR]} in
 * texture space; {@code NEIGHBORS[side] = [up, right, down, left]} world dirs.
 *
 * <p>The CTM sheet is a 4x4 grid of 8x8 submaps. Each face is split into four
 * quadrants; the selector picks one submap per quadrant from the edge and corner
 * connection masks.
 */
public final class CtmFaceGeometry {

    // Edge indices into NEIGHBORS[] and the edgeConnect bitmask.
    public static final int UP = 0;
    public static final int RIGHT = 1;
    public static final int DOWN = 2;
    public static final int LEFT = 3;

    private static final EnumMap<Direction, Vector3f[]> CORNERS = buildCorners();
    private static final EnumMap<Direction, Direction[]> NEIGHBORS = buildNeighbors();

    private CtmFaceGeometry() {
    }

    /** World direction of the given face-space edge (UP/RIGHT/DOWN/LEFT). */
    public static Direction neighborDir(Direction face, int edge) {
        return NEIGHBORS.get(face)[edge];
    }

    /** World position of the diagonal neighbour for the given face quadrant. */
    static BlockPos cornerPos(BlockPos pos, Direction face, CtmTileSelector.Quadrant quadrant) {
        return switch (quadrant) {
            case TOP_LEFT -> pos.relative(neighborDir(face, UP)).relative(neighborDir(face, LEFT));
            case TOP_RIGHT -> pos.relative(neighborDir(face, UP)).relative(neighborDir(face, RIGHT));
            case BOTTOM_RIGHT -> pos.relative(neighborDir(face, DOWN)).relative(neighborDir(face, RIGHT));
            case BOTTOM_LEFT -> pos.relative(neighborDir(face, DOWN)).relative(neighborDir(face, LEFT));
        };
    }

    /** Full-face quad using the whole {@code base} sprite (UV 0..1). */
    public static BakedQuad fullFace(Direction side, TextureAtlasSprite sprite) {
        Vector3f[] c = CORNERS.get(side);
        return quad(side, c[0], c[1], c[2], c[3], sprite, 0f, 0f, 1f, 1f);
    }

    /** One quadrant sub-quad. {@code sq,tq} select the face quarter. */
    static BakedQuad quadrant(Direction side, int sq, int tq, CtmTileSelector.Tile tile,
            TextureAtlasSprite sprite) {
        Vector3f[] c = CORNERS.get(side);
        float s0 = sq * 0.5f, s1 = s0 + 0.5f;
        float t0 = tq * 0.5f, t1 = t0 + 0.5f;
        Vector3f tl = lerp(c, s0, t0);
        Vector3f bl = lerp(c, s0, t1);
        Vector3f br = lerp(c, s1, t1);
        Vector3f tr = lerp(c, s1, t0);
        float step = 1f / tile.source().gridSize();
        float u0 = tile.x() * step, u1 = u0 + step;
        float v0 = tile.y() * step, v1 = v0 + step;
        return quad(side, tl, bl, br, tr, sprite, u0, v0, u1, v1);
    }

    // Bilinear point on the face: P = TL + s*(TR-TL) + t*(BL-TL). c = [TL, BL, BR, TR].
    private static Vector3f lerp(Vector3f[] c, float s, float t) {
        Vector3f tl = c[0], bl = c[1], tr = c[3];
        return new Vector3f(
                tl.x + s * (tr.x - tl.x) + t * (bl.x - tl.x),
                tl.y + s * (tr.y - tl.y) + t * (bl.y - tl.y),
                tl.z + s * (tr.z - tl.z) + t * (bl.z - tl.z));
    }

    private static BakedQuad quad(Direction side, Vector3f tl, Vector3f bl, Vector3f br, Vector3f tr,
            TextureAtlasSprite sprite, float u0, float v0, float u1, float v1) {
        QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer();
        builder.setSprite(sprite);
        builder.setDirection(side);
        builder.setShade(true);
        Vec3i n = side.getNormal();
        putVertex(builder, n, tl, sprite, u0, v0);
        putVertex(builder, n, bl, sprite, u0, v1);
        putVertex(builder, n, br, sprite, u1, v1);
        putVertex(builder, n, tr, sprite, u1, v0);
        return builder.bakeQuad();
    }

    // Vertex element order must match the BLOCK vertex format declaration order.
    private static void putVertex(QuadBakingVertexConsumer builder, Vec3i normal, Vector3f pos,
            TextureAtlasSprite sprite, float u, float v) {
        builder.addVertex(pos.x, pos.y, pos.z);
        builder.setColor(1f, 1f, 1f, 1f);
        builder.setNormal(normal.getX(), normal.getY(), normal.getZ());
        builder.setUv(sprite.getU(u), sprite.getV(v));
    }

    // Face corners [TL, BL, BR, TR] in texture space (ported from AE2 RenderHelper).
    private static EnumMap<Direction, Vector3f[]> buildCorners() {
        EnumMap<Direction, Vector3f[]> map = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.values()) {
            float o = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 0f : 1f;
            Vector3f[] corners = switch (facing.getAxis()) {
                case X -> new Vector3f[] { new Vector3f(o, 1, 1), new Vector3f(o, 0, 1),
                        new Vector3f(o, 0, 0), new Vector3f(o, 1, 0) };
                case Y -> new Vector3f[] { new Vector3f(1, o, 1), new Vector3f(1, o, 0),
                        new Vector3f(0, o, 0), new Vector3f(0, o, 1) };
                case Z -> new Vector3f[] { new Vector3f(0, 1, o), new Vector3f(0, 0, o),
                        new Vector3f(1, 0, o), new Vector3f(1, 1, o) };
            };
            if (facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
                corners = new Vector3f[] { corners[3], corners[2], corners[1], corners[0] };
            }
            map.put(facing, corners);
        }
        return map;
    }

    // Face-space [up, right, down, left] world directions (ported from AE2 makeBitmask).
    private static EnumMap<Direction, Direction[]> buildNeighbors() {
        EnumMap<Direction, Direction[]> map = new EnumMap<>(Direction.class);
        map.put(Direction.DOWN, new Direction[] { Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST });
        map.put(Direction.UP, new Direction[] { Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST });
        map.put(Direction.NORTH, new Direction[] { Direction.UP, Direction.WEST, Direction.DOWN, Direction.EAST });
        map.put(Direction.SOUTH, new Direction[] { Direction.UP, Direction.EAST, Direction.DOWN, Direction.WEST });
        map.put(Direction.WEST, new Direction[] { Direction.UP, Direction.SOUTH, Direction.DOWN, Direction.NORTH });
        map.put(Direction.EAST, new Direction[] { Direction.UP, Direction.NORTH, Direction.DOWN, Direction.SOUTH });
        return map;
    }
}
