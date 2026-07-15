package com.moakiee.ae2lt.logic.tianshu;

import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.block.TianshuSupercomputingUnitBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class TianshuMultiblockScanner {
    public static TianshuMultiblockScanAttempt scan(Level level, BlockPos controllerPos, Direction orientation) {
        return scan(controllerPos, orientation, pos -> componentAt(level, pos));
    }

    public static TianshuMultiblockScanAttempt scan(
            BlockPos controllerPos,
            Direction orientation,
            Function<BlockPos, TianshuMultiblockComponent> resolver) {
        var issues = new ArrayList<TianshuMultiblockScanIssue>();
        var members = new ArrayList<BlockPos>();
        var cores = new ArrayList<BlockPos>(27);
        var ports = new ArrayList<BlockPos>(2);
        var patternStorages = new ArrayList<BlockPos>();
        var seedStorages = new ArrayList<BlockPos>();
        CpuMainCoreTier mainCore = null;
        int capacityCores = 0;
        int parallelCores = 0;
        int closedLoopPatternStorages = 0;
        int closedLoopSeedStorages = 0;
        BlockPos min = null;
        BlockPos max = null;

        for (int x = 0; x < TianshuMultiblockTemplate.SIZE; x++) {
            for (int y = 0; y < TianshuMultiblockTemplate.SIZE; y++) {
                for (int z = 0; z < TianshuMultiblockTemplate.SIZE; z++) {
                    var local = new BlockPos(x, y, z);
                    var role = TianshuMultiblockTemplate.roleAt(local);
                    var world = worldPos(controllerPos, local, orientation);
                    min = min == null ? world : new BlockPos(
                            Math.min(min.getX(), world.getX()), Math.min(min.getY(), world.getY()), Math.min(min.getZ(), world.getZ()));
                    max = max == null ? world : new BlockPos(
                            Math.max(max.getX(), world.getX()), Math.max(max.getY(), world.getY()), Math.max(max.getZ(), world.getZ()));
                    var component = resolver.apply(world);
                    switch (role) {
                        case CONTROLLER -> {
                            if (component != TianshuMultiblockComponent.CONTROLLER) {
                                addOnce(issues, TianshuMultiblockScanIssue.INVALID_CONTROLLER);
                            } else {
                                members.add(world.immutable());
                            }
                        }
                        case CASING -> {
                            if (component != TianshuMultiblockComponent.CASING) {
                                addOnce(issues, TianshuMultiblockScanIssue.MISSING_CASING);
                            } else {
                                members.add(world.immutable());
                            }
                        }
                        case GLASS -> {
                            if (component != TianshuMultiblockComponent.GLASS) {
                                addOnce(issues, TianshuMultiblockScanIssue.MISSING_GLASS);
                            } else {
                                members.add(world.immutable());
                            }
                        }
                        case PORT_CANDIDATE -> {
                            if (component == TianshuMultiblockComponent.PORT) {
                                ports.add(world.immutable());
                                members.add(world.immutable());
                            } else if (component == TianshuMultiblockComponent.CASING) {
                                members.add(world.immutable());
                            } else {
                                addOnce(issues, TianshuMultiblockScanIssue.MISSING_CASING);
                            }
                        }
                        case CORE_RESERVED -> {
                            cores.add(world.immutable());
                            boolean center = local.equals(new BlockPos(3, 3, 3));
                            CpuMainCoreTier tier = mainTier(component);
                            if (center) {
                                if (tier == null) {
                                    addOnce(issues, TianshuMultiblockScanIssue.MISSING_MAIN_CORE);
                                } else {
                                    mainCore = tier;
                                    members.add(world.immutable());
                                }
                            } else if (component == TianshuMultiblockComponent.STORAGE_CORE) {
                                capacityCores++;
                                members.add(world.immutable());
                            } else if (component == TianshuMultiblockComponent.PARALLEL_CORE) {
                                parallelCores++;
                                members.add(world.immutable());
                            } else if (component == TianshuMultiblockComponent.CLOSED_LOOP_PATTERN_STORAGE) {
                                closedLoopPatternStorages++;
                                patternStorages.add(world.immutable());
                                members.add(world.immutable());
                            } else if (component == TianshuMultiblockComponent.CLOSED_LOOP_SEED_STORAGE) {
                                closedLoopSeedStorages++;
                                seedStorages.add(world.immutable());
                                members.add(world.immutable());
                            } else {
                                if (tier != null) addOnce(issues, TianshuMultiblockScanIssue.MAIN_CORE_OUTSIDE_CENTER);
                                addOnce(issues, TianshuMultiblockScanIssue.INVALID_PERIPHERAL_CORE);
                            }
                        }
                        case IGNORED -> {
                            if (component != TianshuMultiblockComponent.AIR) {
                                addOnce(issues, TianshuMultiblockScanIssue.UNEXPECTED_BLOCK);
                            }
                        }
                    }
                }
            }
        }
        if (ports.isEmpty()) {
            addOnce(issues, TianshuMultiblockScanIssue.MISSING_PORT);
        } else if (ports.size() > 1) {
            addOnce(issues, TianshuMultiblockScanIssue.MULTIPLE_PORTS);
        }
        if (capacityCores == 0) addOnce(issues, TianshuMultiblockScanIssue.MISSING_STORAGE_CORE);
        if (parallelCores == 0) addOnce(issues, TianshuMultiblockScanIssue.MISSING_PARALLEL_CORE);
        if (!issues.isEmpty()) {
            return new TianshuMultiblockScanAttempt(null, List.copyOf(issues));
        }
        var profile = CpuInternalCoreCalculator.calculate(mainCore, capacityCores, parallelCores);
        var functionProfile = new TianshuFunctionProfile(
                closedLoopPatternStorages, closedLoopSeedStorages);
        return new TianshuMultiblockScanAttempt(new TianshuMultiblockScanResult(
                controllerPos.immutable(), orientation, min, max, ports.getFirst(),
                List.copyOf(members), List.copyOf(cores), List.copyOf(patternStorages),
                List.copyOf(seedStorages),
                profile, functionProfile), List.of());
    }

    public static TianshuMultiblockComponent componentAt(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) return TianshuMultiblockComponent.AIR;
        if (state.is(ModBlocks.TIANSHU_SUPERCOMPUTER_CASING.get())) return TianshuMultiblockComponent.CASING;
        if (state.is(ModBlocks.TIANSHU_SUPERCOMPUTER_GLASS.get())) return TianshuMultiblockComponent.GLASS;
        if (state.is(ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get())) return TianshuMultiblockComponent.CONTROLLER;
        if (state.is(ModBlocks.TIANSHU_SUPERCOMPUTER_PORT.get())) return TianshuMultiblockComponent.PORT;
        if (state.getBlock() instanceof TianshuSupercomputingUnitBlock unit) return unit.component();
        return TianshuMultiblockComponent.OTHER;
    }

    private static CpuMainCoreTier mainTier(TianshuMultiblockComponent component) {
        return switch (component) {
            case MAIN_BASELINE -> CpuMainCoreTier.BASELINE;
            case MAIN_QUANTUM -> CpuMainCoreTier.QUANTUM;
            case MAIN_OVERLOAD -> CpuMainCoreTier.OVERLOAD;
            case MAIN_MULTIDIMENSIONAL -> CpuMainCoreTier.MULTIDIMENSIONAL;
            default -> null;
        };
    }

    public static BlockPos worldPos(BlockPos controllerPos, BlockPos local, Direction orientation) {
        int dx = local.getX() - TianshuMultiblockTemplate.CONTROLLER.getX();
        int dy = local.getY() - TianshuMultiblockTemplate.CONTROLLER.getY();
        int dz = local.getZ() - TianshuMultiblockTemplate.CONTROLLER.getZ();
        return switch (orientation) {
            case WEST -> controllerPos.offset(dx, dy, dz);
            case NORTH -> controllerPos.offset(-dz, dy, dx);
            case EAST -> controllerPos.offset(-dx, dy, -dz);
            case SOUTH -> controllerPos.offset(dz, dy, -dx);
            default -> throw new IllegalArgumentException("Tianshu controller must face horizontally");
        };
    }

    private static void addOnce(List<TianshuMultiblockScanIssue> issues, TianshuMultiblockScanIssue issue) {
        if (!issues.contains(issue)) {
            issues.add(issue);
        }
    }

    private TianshuMultiblockScanner() {
    }
}
