package com.moakiee.ae2lt.logic.craft;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.moakiee.ae2lt.block.MatrixMultiblockComponentBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;

public final class MatrixMultiblockScanner {
    private MatrixMultiblockScanner() {
    }

    public static Optional<MatrixMultiblockScanResult> find(BlockPos controllerPos,
                                                            Direction orientation,
                                                            ComponentResolver resolver) {
        Objects.requireNonNull(controllerPos);
        Objects.requireNonNull(orientation);
        Objects.requireNonNull(resolver);
        var attempt = scan(controllerPos, orientation, resolver);
        return attempt.formed() ? Optional.of(attempt.result()) : Optional.empty();
    }

    public static Optional<MatrixMultiblockScanResult> findInLevel(BlockGetter level,
                                                                   BlockPos controllerPos,
                                                                   Direction orientation) {
        Objects.requireNonNull(level);
        return find(controllerPos, orientation, pos -> componentAt(level, pos));
    }

    public static MatrixMultiblockScanAttempt scan(BlockPos controllerPos,
                                                   Direction orientation,
                                                   ComponentResolver resolver) {
        Objects.requireNonNull(controllerPos);
        Objects.requireNonNull(orientation);
        Objects.requireNonNull(resolver);

        if (orientation.getAxis() == Direction.Axis.Y) {
            return new MatrixMultiblockScanAttempt(
                    orientation,
                    List.of(MatrixMultiblockScanIssue.UNEXPECTED_COMPONENT),
                    null);
        }

        var issues = new ArrayList<MatrixMultiblockScanIssue>();
        var members = new ArrayList<MatrixMultiblockMember>();
        var craftingMembers = new ArrayList<MatrixMultiblockMember>();
        var patternMembers = new ArrayList<MatrixMultiblockMember>();

        BlockPos portPos = null;
        int portCount = 0;
        int multiplierCount = 0;
        boolean hasMainCoreAtCenter = false;
        MatrixMultiblockComponent mainCoreAtCenter = null;
        boolean hasMainCoreOutsideCenter = false;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (var entry : MatrixMultiblockTemplate.entries()) {
            var localPos = entry.localPos();
            var role = entry.role();
            var worldPos = worldPos(controllerPos, localPos, orientation);
            var component = normalize(resolver.componentAt(worldPos));

            minX = Math.min(minX, worldPos.getX());
            minY = Math.min(minY, worldPos.getY());
            minZ = Math.min(minZ, worldPos.getZ());
            maxX = Math.max(maxX, worldPos.getX());
            maxY = Math.max(maxY, worldPos.getY());
            maxZ = Math.max(maxZ, worldPos.getZ());

            if (!accepts(role, component, localPos)) {
                addIssue(issues, MatrixMultiblockScanIssue.UNEXPECTED_COMPONENT);
            }

            if (role == MatrixMultiblockRole.PORT_CANDIDATE && component == MatrixMultiblockComponent.MATRIX_PORT) {
                portCount++;
                portPos = worldPos;
            }

            if (role == MatrixMultiblockRole.CRAFTING_BAY) {
                if (localPos.equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)) {
                    hasMainCoreAtCenter = component.isMainCore();
                    if (hasMainCoreAtCenter) {
                        mainCoreAtCenter = component;
                    }
                } else if (component.isMainCore()) {
                    hasMainCoreOutsideCenter = true;
                }
                if (component.isMultiplierSubCore()) {
                    multiplierCount++;
                }
            }

            if (component != MatrixMultiblockComponent.AIR && role != MatrixMultiblockRole.EMPTY) {
                var member = new MatrixMultiblockMember(worldPos, localPos, role, component);
                members.add(member);
                if (role == MatrixMultiblockRole.CRAFTING_BAY) {
                    craftingMembers.add(member);
                } else if (role == MatrixMultiblockRole.PATTERN_BAY) {
                    patternMembers.add(member);
                }
            }
        }

        if (portCount == 0) {
            addIssue(issues, MatrixMultiblockScanIssue.MISSING_PORT);
        } else if (portCount > 1) {
            addIssue(issues, MatrixMultiblockScanIssue.MULTIPLE_PORTS);
        }
        if (!hasMainCoreAtCenter) {
            addIssue(issues, MatrixMultiblockScanIssue.MISSING_MAIN_CORE);
        }
        if (patternMembers.isEmpty()) {
            addIssue(issues, MatrixMultiblockScanIssue.MISSING_PATTERN_STORAGE);
        }
        if (hasMainCoreOutsideCenter) {
            addIssue(issues, MatrixMultiblockScanIssue.MAIN_CORE_OUTSIDE_CENTER);
        }
        if (mainCoreAtCenter != MatrixMultiblockComponent.CREATIVE_MAIN_CORE
                && multiplierCount > MatrixCraftingProfile.MULTIPLIER_LIMIT) {
            addIssue(issues, MatrixMultiblockScanIssue.MULTIPLIER_LIMIT_EXCEEDED);
        }

        MatrixMultiblockScanResult result = null;
        if (issues.isEmpty() && portPos != null) {
            result = new MatrixMultiblockScanResult(
                    controllerPos,
                    orientation,
                    new BlockPos(minX, minY, minZ),
                    new BlockPos(maxX, maxY, maxZ),
                    portPos,
                    members,
                    craftingMembers,
                    patternMembers);
        }

        return new MatrixMultiblockScanAttempt(orientation, issues, result);
    }

    public static BlockPos worldPos(BlockPos controllerPos, BlockPos localPos, Direction orientation) {
        int dx = localPos.getX() - MatrixMultiblockTemplate.CONTROLLER_LOCAL.getX();
        int dy = localPos.getY() - MatrixMultiblockTemplate.CONTROLLER_LOCAL.getY();
        int dz = localPos.getZ() - MatrixMultiblockTemplate.CONTROLLER_LOCAL.getZ();
        return switch (orientation) {
            case EAST -> controllerPos.offset(dx, dy, dz);
            case SOUTH -> controllerPos.offset(-dz, dy, dx);
            case WEST -> controllerPos.offset(-dx, dy, -dz);
            case NORTH -> controllerPos.offset(dz, dy, -dx);
            default -> throw new IllegalArgumentException("Matrix orientation must be horizontal: " + orientation);
        };
    }

    public static Set<BlockPos> candidateControllerPositions(BlockPos changedPos) {
        Objects.requireNonNull(changedPos);
        var candidates = new LinkedHashSet<BlockPos>();
        for (var orientation : Direction.Plane.HORIZONTAL) {
            for (var entry : MatrixMultiblockTemplate.entries()) {
                candidates.add(controllerPosFor(changedPos, entry.localPos(), orientation));
            }
        }
        return Set.copyOf(candidates);
    }

    public static BlockPos controllerPosFor(BlockPos worldPos, BlockPos localPos, Direction orientation) {
        Objects.requireNonNull(worldPos);
        Objects.requireNonNull(localPos);
        Objects.requireNonNull(orientation);
        int dx = localPos.getX() - MatrixMultiblockTemplate.CONTROLLER_LOCAL.getX();
        int dy = localPos.getY() - MatrixMultiblockTemplate.CONTROLLER_LOCAL.getY();
        int dz = localPos.getZ() - MatrixMultiblockTemplate.CONTROLLER_LOCAL.getZ();
        return switch (orientation) {
            case EAST -> worldPos.offset(-dx, -dy, -dz);
            case SOUTH -> worldPos.offset(dz, -dy, -dx);
            case WEST -> worldPos.offset(dx, -dy, dz);
            case NORTH -> worldPos.offset(-dz, -dy, dx);
            default -> throw new IllegalArgumentException("Matrix orientation must be horizontal: " + orientation);
        };
    }

    public static MatrixMultiblockComponent componentAt(BlockGetter level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) {
            return MatrixMultiblockComponent.AIR;
        }
        if (state.getBlock() instanceof MatrixMultiblockComponentBlock componentBlock) {
            return componentBlock.matrixComponent(state);
        }
        return MatrixMultiblockComponent.OTHER;
    }

    private static boolean accepts(MatrixMultiblockRole role, MatrixMultiblockComponent component, BlockPos localPos) {
        return switch (role) {
            case EMPTY -> true;
            case CASING -> component == MatrixMultiblockComponent.MATRIX_CASING;
            case CONSTRAINT_FRAME -> component == MatrixMultiblockComponent.MATRIX_CONSTRAINT_FRAME;
            case GLASS -> component == MatrixMultiblockComponent.MATRIX_GLASS;
            case CONTROLLER -> component == MatrixMultiblockComponent.MATRIX_CONTROLLER;
            case PORT_CANDIDATE -> component == MatrixMultiblockComponent.MATRIX_PORT
                    || component == MatrixMultiblockComponent.MATRIX_CONSTRAINT_FRAME;
            case PATTERN_BAY -> component == MatrixMultiblockComponent.AIR || component.isPatternStorage();
            case CRAFTING_BAY -> acceptsCraftingBay(component, localPos);
        };
    }

    private static boolean acceptsCraftingBay(MatrixMultiblockComponent component, BlockPos localPos) {
        if (localPos.equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)) {
            return component.isMainCore();
        }
        return component.isCraftingSubCore();
    }

    private static MatrixMultiblockComponent normalize(MatrixMultiblockComponent component) {
        return component == null ? MatrixMultiblockComponent.OTHER : component;
    }

    private static void addIssue(List<MatrixMultiblockScanIssue> issues, MatrixMultiblockScanIssue issue) {
        if (!issues.contains(issue)) {
            issues.add(issue);
        }
    }

    @FunctionalInterface
    public interface ComponentResolver {
        MatrixMultiblockComponent componentAt(BlockPos pos);
    }
}
