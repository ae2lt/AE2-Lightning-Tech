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
     * Capacity-aware form used by provider dispatch after it has resolved the
     * exact AE2 storage target for the selected face.
     */
    default boolean canAccept(
            ServerLevel level,
            BlockPos pos,
            Direction face,
            IPatternDetails pattern,
            @Nullable PatternProviderTarget cachedTarget) {
        return canAccept(level, pos, face, pattern);
    }

    /**
     * Whether this target supports aggregated copies. Dedicated crafting
     * machines whose {@code pushPattern} call is itself the execution event
     * should return {@code false} and remain on one-copy dispatch.
     */
    default boolean supportsBatch(
            ServerLevel level, BlockPos pos, Direction face, IPatternDetails pattern) {
        return true;
    }

    /**
     * Attempt to push up to {@code maxCopies} copies of the pattern's inputs.
     *
     * @param blocking      if {@code true}, refuse when the target already holds pattern inputs
     * @param patternInputs the union of all input keys (secondary dropped); used only when blocking
     * @param cachedTarget  调用方预取的 target（命中缓存可避免重复 BlockCapability 查询）；
     *                      为 null 时实现需自行解析。仅 generic inventory 路径会用到。
     * @return a {@link PushResult} containing the number of copies whose
     *         ownership was accepted and any provider-owned overflow
     */
    PushResult pushCopies(ServerLevel level, BlockPos pos, Direction face,
                          IPatternDetails pattern, KeyCounter[] inputs, int maxCopies,
                          boolean blocking, Set<AEKey> patternInputs,
                          IActionSource source,
                          @Nullable PatternProviderTarget cachedTarget);

    /**
     * Dispatch using an explicit input-acceptance contract. Existing external
     * adapters retain their previous behavior unless they override this method.
     */
    default PushResult pushCopies(
            ServerLevel level,
            BlockPos pos,
            Direction face,
            IPatternDetails pattern,
            KeyCounter[] inputs,
            int maxCopies,
            PatternInputAcceptance inputAcceptance,
            boolean blocking,
            Set<AEKey> patternInputs,
            IActionSource source,
            @Nullable PatternProviderTarget cachedTarget) {
        return pushCopies(
                level, pos, face, pattern, inputs, maxCopies,
                blocking, patternInputs, source, cachedTarget);
    }

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
     * Destination for outputs extracted by {@link #extractOutputs}. Lets the
     * caller cap extraction up-front (power + storage limits) so items are
     * never pulled out of a machine without a guaranteed place to go.
     */
    interface OutputSink {
        /** Max amount of {@code what} the sink can accept right now; 0 skips extraction. */
        long maxAccept(AEKey what, long available);

        /** Store extracted items. Returns the amount actually stored. */
        long accept(AEKey what, long amount);

        /**
         * Last-resort delivery for items that could neither be stored via
         * {@link #accept} nor pushed back into the machine. Must not void them.
         */
        void acceptOverflow(AEKey what, long amount);
    }

    /**
     * Extract items matching {@code allowedOutputs} from the machine and hand
     * them to {@code sink}. Implementations must query {@link OutputSink#maxAccept}
     * before each extract so items without a destination stay in the machine
     * instead of being voided.
     *
     * @param allowedOutputs filter describing which outputs belong to currently
     *                       loaded patterns
     * @return whether output was extracted, absent, blocked, or unavailable
     */
    default OutputReturnResult extractOutputs(
            ServerLevel level,
            BlockPos pos,
            Direction face,
            AllowedOutputFilter allowedOutputs,
            IActionSource source,
            OutputSink sink) {
        return OutputReturnResult.UNAVAILABLE;
    }
}
