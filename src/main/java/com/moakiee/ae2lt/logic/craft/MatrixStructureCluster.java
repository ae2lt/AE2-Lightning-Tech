package com.moakiee.ae2lt.logic.craft;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;

public final class MatrixStructureCluster {
    private final MatrixMultiblockScanResult scanResult;
    private final Set<BlockPos> memberPositions;
    private final MatrixCraftingProfile craftingProfile;
    private final MatrixPatternRepository patternRepository;
    private boolean destroyed;

    public MatrixStructureCluster(MatrixMultiblockScanResult scanResult) {
        this.scanResult = scanResult;
        this.memberPositions = new HashSet<>();
        for (var member : scanResult.members()) {
            this.memberPositions.add(member.worldPos());
        }
        this.craftingProfile = scanResult.craftingProfile();
        this.patternRepository = scanResult.createEmptyPatternRepository();
    }

    public MatrixMultiblockScanResult scanResult() {
        return scanResult;
    }

    public boolean isFormed() {
        return !destroyed;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void destroy() {
        destroyed = true;
    }

    public boolean contains(BlockPos pos) {
        return memberPositions.contains(pos);
    }

    public Set<BlockPos> memberPositions() {
        return Set.copyOf(memberPositions);
    }

    public MatrixCraftingProfile craftingProfile() {
        return destroyed ? MatrixCraftingProfile.empty() : craftingProfile;
    }

    public MatrixPatternRepository patternRepository() {
        return patternRepository;
    }
}
