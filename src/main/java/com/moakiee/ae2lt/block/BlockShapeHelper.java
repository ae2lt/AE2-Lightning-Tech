package com.moakiee.ae2lt.block;

import java.util.EnumMap;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class BlockShapeHelper {
    private BlockShapeHelper() {
    }

    static VoxelShape or(VoxelShape... shapes) {
        VoxelShape result = Shapes.empty();
        for (var shape : shapes) {
            result = Shapes.or(result, shape);
        }
        return result;
    }

    static EnumMap<Direction, VoxelShape> createAllFacingShapes(VoxelShape upShape) {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        for (var direction : Direction.values()) {
            shapes.put(direction, rotateFromUp(upShape, direction));
        }
        return shapes;
    }

    static EnumMap<Direction, VoxelShape> createHorizontalFacingShapes(VoxelShape northShape) {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        for (var direction : Direction.Plane.HORIZONTAL) {
            shapes.put(direction, rotateFromNorth(northShape, direction));
        }
        return shapes;
    }

    private static VoxelShape rotateFromUp(VoxelShape shape, Direction direction) {
        if (direction == Direction.UP) {
            return shape;
        }

        VoxelShape[] rotated = { Shapes.empty() };
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> rotated[0] = Shapes.or(
                rotated[0],
                switch (direction) {
                    case DOWN -> Shapes.box(minX, 1 - maxY, 1 - maxZ, maxX, 1 - minY, 1 - minZ);
                    case NORTH -> Shapes.box(minX, minZ, 1 - maxY, maxX, maxZ, 1 - minY);
                    case SOUTH -> Shapes.box(1 - maxX, minZ, minY, 1 - minX, maxZ, maxY);
                    case EAST -> Shapes.box(minY, minZ, minX, maxY, maxZ, maxX);
                    case WEST -> Shapes.box(1 - maxY, minZ, 1 - maxX, 1 - minY, maxZ, 1 - minX);
                    case UP -> Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
                }));
        return rotated[0];
    }

    private static VoxelShape rotateFromNorth(VoxelShape shape, Direction direction) {
        if (direction == Direction.NORTH) {
            return shape;
        }

        VoxelShape[] rotated = { Shapes.empty() };
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> rotated[0] = Shapes.or(
                rotated[0],
                switch (direction) {
                    case EAST -> Shapes.box(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX);
                    case SOUTH -> Shapes.box(1 - maxX, minY, 1 - maxZ, 1 - minX, maxY, 1 - minZ);
                    case WEST -> Shapes.box(minZ, minY, 1 - maxX, maxZ, maxY, 1 - minX);
                    case NORTH -> Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
                    default -> throw new IllegalArgumentException("Direction must be horizontal: " + direction);
                }));
        return rotated[0];
    }
}
