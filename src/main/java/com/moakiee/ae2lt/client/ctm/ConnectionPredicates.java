package com.moakiee.ae2lt.client.ctm;

import java.util.HashMap;
import java.util.Map;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.block.MatrixFormedBlock;
import com.moakiee.ae2lt.block.MatrixMultiblockComponentBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputerStructureBlock;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Registry of named {@link ConnectionPredicate}s referenced by the {@code connection}
 * field of a connected-texture model. Add new entries here to reuse the CTM
 * framework for other blocks.
 */
public final class ConnectionPredicates {

    private static final Map<ResourceLocation, ConnectionPredicate> REGISTRY = new HashMap<>();

    /** Generic: always active, connects to any adjacent block of the same type. */
    public static final ConnectionPredicate SAME_BLOCK = new ConnectionPredicate() {
        @Override
        public boolean isActive(BlockAndTintGetter level, BlockPos pos, BlockState self) {
            return true;
        }

        @Override
        public boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState self, Direction dir) {
            return level.getBlockState(pos.relative(dir)).is(self.getBlock());
        }

        @Override
        public boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState self, BlockPos neighbourPos) {
            return level.getBlockState(neighbourPos).is(self.getBlock());
        }
    };

    /** Matrix components: active only once formed; connects only to formed blocks of the same type. */
    public static final ConnectionPredicate MATRIX_FORMED_SAME_BLOCK = new ConnectionPredicate() {
        @Override
        public boolean isActive(BlockAndTintGetter level, BlockPos pos, BlockState self) {
            return isFormedMatrixComponent(self);
        }

        @Override
        public boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState self, Direction dir) {
            BlockState neighbour = level.getBlockState(pos.relative(dir));
            return neighbour.is(self.getBlock()) && isFormedMatrixComponent(neighbour);
        }

        @Override
        public boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState self, BlockPos neighbourPos) {
            BlockState neighbour = level.getBlockState(neighbourPos);
            return neighbour.is(self.getBlock()) && isFormedMatrixComponent(neighbour);
        }
    };

    /** Tianshu shell components: active only once formed; connects only to formed blocks of the same type. */
    public static final ConnectionPredicate TIANSHU_FORMED_SAME_BLOCK = new ConnectionPredicate() {
        @Override
        public boolean isActive(BlockAndTintGetter level, BlockPos pos, BlockState self) {
            return isFormedTianshuComponent(self);
        }

        @Override
        public boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState self, Direction dir) {
            BlockState neighbour = level.getBlockState(pos.relative(dir));
            return neighbour.is(self.getBlock()) && isFormedTianshuComponent(neighbour);
        }

        @Override
        public boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState self, BlockPos neighbourPos) {
            BlockState neighbour = level.getBlockState(neighbourPos);
            return neighbour.is(self.getBlock()) && isFormedTianshuComponent(neighbour);
        }
    };

    static {
        register(rl("same_block"), SAME_BLOCK);
        register(rl("matrix_formed_same_block"), MATRIX_FORMED_SAME_BLOCK);
        register(rl("tianshu_formed_same_block"), TIANSHU_FORMED_SAME_BLOCK);
    }

    private ConnectionPredicates() {
    }

    public static void register(ResourceLocation id, ConnectionPredicate predicate) {
        REGISTRY.put(id, predicate);
    }

    public static ConnectionPredicate get(ResourceLocation id) {
        return REGISTRY.getOrDefault(id, SAME_BLOCK);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, path);
    }

    private static boolean isFormedMatrixComponent(BlockState state) {
        if (!(state.getBlock() instanceof MatrixMultiblockComponentBlock componentBlock)
                || componentBlock.matrixComponent(state) == MatrixMultiblockComponent.MATRIX_CONTROLLER
                || !state.hasProperty(MatrixFormedBlock.FORMED)) {
            return false;
        }
        return state.getValue(MatrixFormedBlock.FORMED);
    }

    private static boolean isFormedTianshuComponent(BlockState state) {
        return state.getBlock() instanceof TianshuSupercomputerStructureBlock
                && state.hasProperty(TianshuSupercomputerStructureBlock.FORMED)
                && state.getValue(TianshuSupercomputerStructureBlock.FORMED);
    }
}
