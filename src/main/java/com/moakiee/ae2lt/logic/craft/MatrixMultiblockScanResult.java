package com.moakiee.ae2lt.logic.craft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class MatrixMultiblockScanResult {
    private final BlockPos controllerPos;
    private final Direction orientation;
    private final BlockPos minPos;
    private final BlockPos maxPos;
    private final BlockPos portPos;
    private final List<MatrixMultiblockMember> members;
    private final List<MatrixMultiblockMember> craftingMembers;
    private final List<MatrixMultiblockMember> patternMembers;

    MatrixMultiblockScanResult(BlockPos controllerPos,
                               Direction orientation,
                               BlockPos minPos,
                               BlockPos maxPos,
                               BlockPos portPos,
                               List<MatrixMultiblockMember> members,
                               List<MatrixMultiblockMember> craftingMembers,
                               List<MatrixMultiblockMember> patternMembers) {
        this.controllerPos = controllerPos.immutable();
        this.orientation = orientation;
        this.minPos = minPos.immutable();
        this.maxPos = maxPos.immutable();
        this.portPos = portPos.immutable();
        this.members = List.copyOf(members);
        this.craftingMembers = List.copyOf(craftingMembers);
        this.patternMembers = List.copyOf(patternMembers);
    }

    public BlockPos controllerPos() {
        return controllerPos;
    }

    public Direction orientation() {
        return orientation;
    }

    public BlockPos minPos() {
        return minPos;
    }

    public BlockPos maxPos() {
        return maxPos;
    }

    public BlockPos portPos() {
        return portPos;
    }

    public List<MatrixMultiblockMember> members() {
        return members;
    }

    public List<MatrixMultiblockMember> craftingMembers() {
        return craftingMembers;
    }

    public List<MatrixMultiblockMember> patternMembers() {
        return patternMembers;
    }

    public List<MatrixCraftingUnit> craftingUnits() {
        var units = new ArrayList<MatrixCraftingUnit>();
        for (var member : craftingMembers) {
            var unit = member.component().toCraftingUnit(distanceToCraftingCenter(member.localPos()));
            if (unit != null) {
                units.add(unit);
            }
        }
        return List.copyOf(units);
    }

    public MatrixCraftingProfile craftingProfile() {
        return MatrixCraftingProfile.fromUnits(craftingUnits());
    }

    public int closedLoopProcessorCount() {
        int count = 0;
        for (var member : craftingMembers) {
            if (member.component().isClosedLoopProcessor()) {
                count++;
            }
        }
        return count;
    }

    public boolean hasClosedLoopProcessor() {
        return closedLoopProcessorCount() > 0;
    }

    public List<MatrixPatternStorageTier> patternStorageTiers() {
        var tiers = new ArrayList<MatrixPatternStorageTier>();
        for (var member : patternMembers) {
            var tier = member.component().patternStorageTier();
            if (tier != null) {
                tiers.add(tier);
            }
        }
        return List.copyOf(tiers);
    }

    public MatrixPatternRepository createEmptyPatternRepository() {
        var units = new ArrayList<MatrixPatternStorageUnit>();
        for (var tier : patternStorageTiers()) {
            units.add(tier == MatrixPatternStorageTier.T2
                    ? MatrixPatternStorageUnit.t2()
                    : MatrixPatternStorageUnit.t1());
        }
        return new MatrixPatternRepository(units);
    }

    private static int distanceToCraftingCenter(BlockPos localPos) {
        return Math.abs(localPos.getX() - MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL.getX())
                + Math.abs(localPos.getY() - MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL.getY())
                + Math.abs(localPos.getZ() - MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL.getZ());
    }
}
