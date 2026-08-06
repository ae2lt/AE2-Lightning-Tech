package com.moakiee.ae2lt.logic.craft;

import java.util.List;
import java.util.Objects;

import com.moakiee.thunderbolt.ae2.api.crafting.IBatchCraftingProvider;
import com.moakiee.thunderbolt.ae2.api.crafting.BatchDispatchMode;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

/**
 * Logic facade for the future matrix interface block.
 *
 * <p>The final block shape and registry name are intentionally not represented here. A real block
 * entity should host the AE grid node and delegate provider/inventory behavior to this facade,
 * while the controller remains a non-networked structure/GUI part.
 */
public final class MatrixMultiblockInterface implements IBatchCraftingProvider {
    private final MatrixCraftingCluster cluster;
    private final MatrixPatternRepository patternRepository;

    public MatrixMultiblockInterface(MatrixCraftingCluster cluster, MatrixPatternRepository patternRepository) {
        this.cluster = Objects.requireNonNull(cluster);
        this.patternRepository = Objects.requireNonNull(patternRepository);
        this.cluster.addPatternCore(patternRepository);
    }

    public MatrixMultiblockPortRole role() {
        return MatrixMultiblockPortRole.INTERFACE;
    }

    public MatrixPatternRepository patternRepository() {
        return patternRepository;
    }

    public boolean insertPattern(IPatternDetails pattern) {
        return patternRepository.insert(pattern);
    }

    public List<IPatternDetails> insertPatterns(List<? extends IPatternDetails> patterns) {
        return patternRepository.insertAll(patterns);
    }

    public MatrixPatternStorageUnit exposedPatternUnit() {
        return patternRepository.exposedUnit();
    }

    public MatrixPatternRepository.UpgradeResult upgradeT1PatternUnits(int maxUpgrades) {
        return patternRepository.upgradeT1Units(maxUpgrades);
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return cluster.getAvailablePatterns();
    }

    @Override
    public boolean isBusy() {
        return cluster.isBusy();
    }

    @Override
    public long getBatchCapacity(IPatternDetails details) {
        return cluster.getBatchCapacity(details);
    }

    @Override
    public BatchDispatchMode getBatchDispatchMode(IPatternDetails details) {
        return cluster.batchDispatchMode();
    }

    @Override
    public long pushBatch(IPatternDetails details, KeyCounter[] oneCopyTemplate, long maxCraft) {
        return cluster.pushBatch(details, oneCopyTemplate, maxCraft);
    }
}
