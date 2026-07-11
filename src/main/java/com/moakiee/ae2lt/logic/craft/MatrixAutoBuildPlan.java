package com.moakiee.ae2lt.logic.craft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;

public final class MatrixAutoBuildPlan {
    private static final BlockPos DEFAULT_PORT_LOCAL = new BlockPos(6, 5, 3);

    private final List<Placement> placements;
    private final List<BlockPos> blocked;
    private final int missingPatternStorages;

    private MatrixAutoBuildPlan(List<Placement> placements, List<BlockPos> blocked, int missingPatternStorages) {
        this.placements = List.copyOf(placements);
        this.blocked = List.copyOf(blocked);
        this.missingPatternStorages = missingPatternStorages;
    }

    public static MatrixAutoBuildPlan create(ComponentResolver resolver, int patternStorageBudget) {
        Objects.requireNonNull(resolver);
        var placements = new ArrayList<Placement>();
        var blocked = new ArrayList<BlockPos>();
        int remainingPatternStorages = Math.max(0, patternStorageBudget);
        BlockPos portTarget = selectPortTarget(resolver);
        boolean hasPatternStorage = hasExistingPatternStorage(resolver);
        boolean willPlacePatternStorage = false;

        for (var entry : MatrixMultiblockTemplate.entries()) {
            var role = entry.role();
            if (!shouldAutoBuild(role)) {
                continue;
            }

            var local = entry.localPos();
            var component = normalize(resolver.componentAt(local));

            if (role == MatrixMultiblockRole.PATTERN_BAY) {
                if (component.isPatternStorage()) {
                    continue;
                }
                if (component != MatrixMultiblockComponent.AIR) {
                    blocked.add(local);
                    continue;
                }
                if (remainingPatternStorages > 0) {
                    placements.add(new Placement(local, Target.PATTERN_STORAGE));
                    remainingPatternStorages--;
                    willPlacePatternStorage = true;
                }
                continue;
            }

            var target = targetFor(role, local, portTarget);
            if (target == null) {
                continue;
            }
            if (matchesTarget(component, target)) {
                continue;
            }
            if (component != MatrixMultiblockComponent.AIR) {
                blocked.add(local);
                continue;
            }
            placements.add(new Placement(local, target));
        }

        int missingPatternStorages = hasPatternStorage || willPlacePatternStorage ? 0 : 1;
        return new MatrixAutoBuildPlan(placements, blocked, missingPatternStorages);
    }

    public List<Placement> placements() {
        return placements;
    }

    public List<BlockPos> blocked() {
        return blocked;
    }

    public int missingPatternStorages() {
        return missingPatternStorages;
    }

    private static BlockPos selectPortTarget(ComponentResolver resolver) {
        BlockPos firstExistingPort = null;
        for (var entry : MatrixMultiblockTemplate.entries()) {
            if (entry.role() == MatrixMultiblockRole.PORT_CANDIDATE
                    && normalize(resolver.componentAt(entry.localPos())) == MatrixMultiblockComponent.MATRIX_PORT) {
                if (entry.localPos().equals(DEFAULT_PORT_LOCAL)) {
                    return DEFAULT_PORT_LOCAL;
                }
                if (firstExistingPort == null) {
                    firstExistingPort = entry.localPos();
                }
            }
        }
        return firstExistingPort != null ? firstExistingPort : DEFAULT_PORT_LOCAL;
    }

    private static boolean hasExistingPatternStorage(ComponentResolver resolver) {
        for (var entry : MatrixMultiblockTemplate.entries()) {
            if (entry.role() == MatrixMultiblockRole.PATTERN_BAY
                    && normalize(resolver.componentAt(entry.localPos())).isPatternStorage()) {
                return true;
            }
        }
        return false;
    }

    private static Target targetFor(MatrixMultiblockRole role, BlockPos local, BlockPos portTarget) {
        return switch (role) {
            case CASING -> Target.CASING;
            case CONSTRAINT_FRAME -> Target.CONSTRAINT_FRAME;
            case GLASS -> Target.GLASS;
            case PORT_CANDIDATE -> local.equals(portTarget)
                    ? Target.PORT
                    : Target.CONSTRAINT_FRAME;
            default -> null;
        };
    }

    private static boolean matchesTarget(MatrixMultiblockComponent component, Target target) {
        return switch (target) {
            case CASING -> component == MatrixMultiblockComponent.MATRIX_CASING;
            case CONSTRAINT_FRAME -> component == MatrixMultiblockComponent.MATRIX_CONSTRAINT_FRAME;
            case GLASS -> component == MatrixMultiblockComponent.MATRIX_GLASS;
            case PORT -> component == MatrixMultiblockComponent.MATRIX_PORT;
            case PATTERN_STORAGE -> component.isPatternStorage();
        };
    }

    private static boolean shouldAutoBuild(MatrixMultiblockRole role) {
        return role != MatrixMultiblockRole.EMPTY
                && role != MatrixMultiblockRole.CONTROLLER
                && role != MatrixMultiblockRole.CRAFTING_BAY;
    }

    private static MatrixMultiblockComponent normalize(MatrixMultiblockComponent component) {
        return component == null ? MatrixMultiblockComponent.OTHER : component;
    }

    public enum Target {
        CASING,
        CONSTRAINT_FRAME,
        GLASS,
        PORT,
        PATTERN_STORAGE
    }

    public record Placement(BlockPos localPos, Target target) {
    }

    @FunctionalInterface
    public interface ComponentResolver {
        MatrixMultiblockComponent componentAt(BlockPos localPos);
    }
}
