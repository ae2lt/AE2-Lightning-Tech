package com.moakiee.ae2lt.client.ctm;

import net.minecraft.core.Direction;

/**
 * Pre-computed connection data for one block, carried via ModelData to the baked
 * model. {@code edgeConnect} holds, per face (indexed by {@link Direction#get3DDataValue()}),
 * a 4-bit mask of which face-space edge neighbours connect (see CtmFaceGeometry
 * edge constants). {@code cornerConnect} holds a 4-bit mask of diagonal neighbours
 * in {@link CtmTileSelector.Quadrant#ordinal()} order. {@code faceCulled} marks
 * faces whose opposite neighbour also connects (interior faces that should not render).
 */
public record CtmConnectionState(boolean[] faceCulled, int[] edgeConnect, int[] cornerConnect) {

    public boolean culled(Direction face) {
        return faceCulled[face.get3DDataValue()];
    }

    public int edges(Direction face) {
        return edgeConnect[face.get3DDataValue()];
    }

    public int corners(Direction face) {
        return cornerConnect[face.get3DDataValue()];
    }
}
