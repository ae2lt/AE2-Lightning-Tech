package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

class ClosedLoopMolecularPatternDetails extends ClosedLoopExpandedPatternDetails
        implements IMolecularAssemblerSupportedPattern {
    private final IMolecularAssemblerSupportedPattern molecular;

    ClosedLoopMolecularPatternDetails(IMolecularAssemblerSupportedPattern molecular,
                                      java.util.Map<AEKey, Long> seedAmounts,
                                      java.util.Set<AEKey> cycleKeys,
                                      java.util.UUID seedGroupId,
                                      boolean singleSeedInputPerMember,
                                      java.util.Map<Integer, AEKey> plannedSeedInputSlots,
                                      appeng.api.stacks.AEItemKey persistenceDefinition,
                                      int dispatchOrder) {
        super(molecular, seedAmounts, cycleKeys, seedGroupId, singleSeedInputPerMember,
                plannedSeedInputSlots, persistenceDefinition, dispatchOrder);
        this.molecular = molecular;
    }

    // 1.20.1 AE2 passes a plain Container to assemble() but a CraftingContainer to getRemainingItems().
    @Override public ItemStack assemble(Container input, Level level) {
        return molecular.assemble(input, level);
    }
    @Override public boolean isItemValid(int slotIndex, AEItemKey key, Level level) {
        return molecular.isItemValid(slotIndex, key, level);
    }
    @Override public boolean isSlotEnabled(int slot) { return molecular.isSlotEnabled(slot); }
    @Override public void fillCraftingGrid(KeyCounter[] inputHolder, CraftingGridAccessor accessor) {
        molecular.fillCraftingGrid(inputHolder, accessor);
    }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
        return molecular.getRemainingItems(input);
    }
}
