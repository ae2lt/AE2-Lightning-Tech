package com.moakiee.ae2lt.logic;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderTarget;

/**
 * Stateless adapter that knows how to interact with one category of machines.
 * <p>
 * Implementations are singletons registered in {@link MachineAdapterRegistry}.
 * All world context is passed as method parameters — adapters hold no mutable state.
 * <p>
 * The dispatch unit is a <em>pattern task copy</em>, not individual items.
 */
public interface MachineAdapter {

    /**
     * Does this adapter recognise the block at the given position?
     * Called once per dispatch attempt; first adapter that returns {@code true} wins.
     */
    boolean supports(ServerLevel level, BlockPos pos);

    /**
     * Quick pre-check: can the machine plausibly accept tasks for this pattern
     * through the given face?  Should be cheap (no item simulation).
     */
    boolean canAccept(ServerLevel level, BlockPos pos, Direction face, IPatternDetails pattern);

    /**
     * Attempt to push up to {@code maxCopies} copies of the pattern's inputs.
     *
     * @param blocking      if {@code true}, refuse when the target already holds pattern inputs
     * @param patternInputs the union of all input keys (secondary dropped); used only when blocking
     * @param cachedTarget  调用方预取的 target（命中缓存可避免重复 BlockCapability 查询）；
     *                      为 null 时实现需自行解析。仅 generic inventory 路径会用到。
     * @return a {@link PushResult} containing the number of accepted copies and any overflow items
     */
    PushResult pushCopies(ServerLevel level, BlockPos pos, Direction face,
                          IPatternDetails pattern, KeyCounter[] inputs, int maxCopies,
                          boolean blocking, Set<AEKey> patternInputs,
                          IActionSource source,
                          @Nullable PatternProviderTarget cachedTarget);

    /**
     * Try to flush leftover items into the same target.
     * <p>
     * Default implementation uses {@link PatternProviderTarget} which works for
     * any block exposing ME-storage or platform external-storage capabilities.
     *
     * @param cachedTarget 同 {@link #pushCopies}，调用方预取的 target；为 null 则自行解析。
     * @return {@code true} if all overflow was delivered
     */
    default boolean flushOverflow(ServerLevel level, BlockPos pos, Direction face,
                                  List<GenericStack> overflow, IActionSource source,
                                  @Nullable PatternProviderTarget cachedTarget) {
        if (!level.isLoaded(pos)) return false;
        var target = cachedTarget;
        if (target == null) {
            var be = level.getBlockEntity(pos);
            target = PatternProviderTarget.get(level, pos, be, face, source);
        }
        if (target == null) return false;

        var it = overflow.listIterator();
        while (it.hasNext()) {
            var stack = it.next();
            var inserted = target.insert(stack.what(), stack.amount(), Actionable.MODULATE);
            if (inserted >= stack.amount()) {
                it.remove();
            } else if (inserted > 0) {
                it.set(new GenericStack(stack.what(), stack.amount() - inserted));
            }
        }
        return overflow.isEmpty();
    }

    /**
     * Extract items from the machine that match the allowed output keys.
     * Only items accepted by {@code allowedOutputs} will be extracted — input
     * materials, catalysts, upgrades etc. are left alone.
     *
     * @param allowedOutputs filter describing which outputs belong to currently
     *                       loaded patterns
     * @return extracted stacks (already committed, caller must store them)
     */
    default List<GenericStack> extractOutputs(ServerLevel level, BlockPos pos, Direction face,
                                              AllowedOutputFilter allowedOutputs, IActionSource source) {
        return List.of();
    }
}
