package com.moakiee.ae2lt.logic.tianshu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;

/**
 * Plans construction of the Tianshu shell without touching its 3x3x3 core chamber.
 * Closed-loop storage blocks already installed in cooling positions are preserved.
 */
public final class TianshuAutoBuildPlan {
    private final List<Placement> placements;
    private final List<BlockPos> blocked;

    private TianshuAutoBuildPlan(List<Placement> placements, List<BlockPos> blocked) {
        this.placements = List.copyOf(placements);
        this.blocked = List.copyOf(blocked);
    }

    public static TianshuAutoBuildPlan create(ComponentResolver resolver) {
        Objects.requireNonNull(resolver);
        var placements = new ArrayList<Placement>();
        var blocked = new ArrayList<BlockPos>();
        BlockPos portTarget = selectPortTarget(resolver);

        for (int x = 0; x < TianshuMultiblockTemplate.SIZE; x++) {
            for (int y = 0; y < TianshuMultiblockTemplate.SIZE; y++) {
                for (int z = 0; z < TianshuMultiblockTemplate.SIZE; z++) {
                    var local = new BlockPos(x, y, z);
                    var target = targetFor(TianshuMultiblockTemplate.roleAt(local), local, portTarget);
                    if (target == null) {
                        continue;
                    }

                    var component = normalize(resolver.componentAt(local));
                    if (matchesTarget(component, target)) {
                        continue;
                    }
                    if (component != TianshuMultiblockComponent.AIR) {
                        blocked.add(local);
                        continue;
                    }
                    placements.add(new Placement(local, target));
                }
            }
        }

        placements.sort(Comparator
                .comparingInt((Placement placement) -> placement.localPos().getY())
                .thenComparingInt(placement -> horizontalDistanceFromController(placement.localPos()))
                .thenComparingInt(placement -> Math.abs(
                        placement.localPos().getZ() - TianshuMultiblockTemplate.CONTROLLER.getZ()))
                .thenComparingInt(placement -> placement.localPos().getX())
                .thenComparingInt(placement -> placement.localPos().getZ()));
        return new TianshuAutoBuildPlan(placements, blocked);
    }

    public List<Placement> placements() {
        return placements;
    }

    public List<BlockPos> blocked() {
        return blocked;
    }

    private static BlockPos selectPortTarget(ComponentResolver resolver) {
        var lower = normalize(resolver.componentAt(TianshuMultiblockTemplate.LOWER_PORT));
        if (lower == TianshuMultiblockComponent.PORT) {
            return TianshuMultiblockTemplate.LOWER_PORT;
        }
        var upper = normalize(resolver.componentAt(TianshuMultiblockTemplate.UPPER_PORT));
        if (upper == TianshuMultiblockComponent.PORT) {
            return TianshuMultiblockTemplate.UPPER_PORT;
        }
        if (lower.isClosedLoopStorage() && !upper.isClosedLoopStorage()) {
            return TianshuMultiblockTemplate.UPPER_PORT;
        }
        if (upper.isClosedLoopStorage() && !lower.isClosedLoopStorage()) {
            return TianshuMultiblockTemplate.LOWER_PORT;
        }
        if (lower.fillsCoolingPosition() && upper == TianshuMultiblockComponent.AIR) {
            return TianshuMultiblockTemplate.UPPER_PORT;
        }
        return TianshuMultiblockTemplate.LOWER_PORT;
    }

    private static Target targetFor(TianshuMultiblockRole role, BlockPos local, BlockPos portTarget) {
        return switch (role) {
            case CASING -> Target.CASING;
            case COOLING -> Target.COOLING;
            case GLASS -> Target.GLASS;
            case PORT_CANDIDATE -> local.equals(portTarget) ? Target.PORT : Target.COOLING;
            case CONTROLLER, CORE_RESERVED, IGNORED -> null;
        };
    }

    private static boolean matchesTarget(TianshuMultiblockComponent component, Target target) {
        return switch (target) {
            case CASING -> component == TianshuMultiblockComponent.CASING;
            case COOLING -> component.fillsCoolingPosition();
            case GLASS -> component == TianshuMultiblockComponent.GLASS;
            case PORT -> component == TianshuMultiblockComponent.PORT;
        };
    }

    private static int horizontalDistanceFromController(BlockPos pos) {
        return Math.abs(pos.getX() - TianshuMultiblockTemplate.CONTROLLER.getX())
                + Math.abs(pos.getZ() - TianshuMultiblockTemplate.CONTROLLER.getZ());
    }

    private static TianshuMultiblockComponent normalize(TianshuMultiblockComponent component) {
        return component == null ? TianshuMultiblockComponent.OTHER : component;
    }

    public enum Target {
        CASING,
        COOLING,
        GLASS,
        PORT
    }

    public record Placement(BlockPos localPos, Target target) {
    }

    @FunctionalInterface
    public interface ComponentResolver {
        TianshuMultiblockComponent componentAt(BlockPos localPos);
    }
}
