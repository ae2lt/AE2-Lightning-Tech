package com.moakiee.ae2lt.logic;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Immutable physical endpoint identity shared by local and wireless targets.
 *
 * <p>The complete identity includes the accessed face. {@link #sameTarget}
 * deliberately ignores the face and is reserved for connection-list semantics,
 * where rebinding another side of the same machine replaces the old binding.</p>
 */
public abstract class TargetAddress {
    private final ResourceKey<Level> dimension;
    private final BlockPos pos;
    private final Direction boundFace;
    private final int hashCode;

    protected TargetAddress(
            ResourceKey<Level> dimension,
            BlockPos pos,
            Direction boundFace) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.pos = Objects.requireNonNull(pos, "pos").immutable();
        this.boundFace = Objects.requireNonNull(boundFace, "boundFace");
        int hash = this.dimension.hashCode();
        hash = 31 * hash + this.pos.hashCode();
        this.hashCode = 31 * hash + this.boundFace.hashCode();
    }

    public final ResourceKey<Level> dimension() {
        return dimension;
    }

    public final BlockPos pos() {
        return pos;
    }

    public final Direction boundFace() {
        return boundFace;
    }

    public final boolean sameTarget(
            ResourceKey<Level> otherDimension,
            BlockPos otherPos) {
        return dimension.equals(otherDimension) && pos.equals(otherPos);
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TargetAddress address)) {
            return false;
        }
        return dimension.equals(address.dimension)
                && pos.equals(address.pos)
                && boundFace == address.boundFace;
    }

    @Override
    public final int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[dimension=" + dimension.location()
                + ", pos=" + pos + ", boundFace=" + boundFace + ']';
    }
}
