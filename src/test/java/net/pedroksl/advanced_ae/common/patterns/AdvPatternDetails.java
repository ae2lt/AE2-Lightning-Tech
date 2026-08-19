package net.pedroksl.advanced_ae.common.patterns;

import java.util.HashMap;

import net.minecraft.core.Direction;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/** Test double for the optional AdvancedAE 1.20.1 pattern contract. */
public interface AdvPatternDetails {
    boolean directionalInputsSet();

    HashMap<AEKey, Direction> getDirectionMap();

    Direction getDirectionSideForInputKey(AEKey key);

    void pushInputsToExternalInventory(
            KeyCounter[] inputHolder, IPatternDetails.PatternInputSink inputSink);
}
