package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.item.ClosedLoopPatternItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ClosedLoopPatternDecoder implements IPatternDetailsDecoder {
    public static final ClosedLoopPatternDecoder INSTANCE = new ClosedLoopPatternDecoder();

    private ClosedLoopPatternDecoder() {
    }

    @Override
    public boolean isEncodedPattern(ItemStack stack) {
        return stack.getItem() instanceof ClosedLoopPatternItem item && item.hasPayload(stack);
    }

    @Override
    public @Nullable IPatternDetails decodePattern(AEItemKey what, Level level) {
        if (what == null || !(what.getItem() instanceof ClosedLoopPatternItem item)) return null;
        var stack = what.toStack();
        var payload = item.readPayload(stack, level).orElse(null);
        if (payload == null) return null;
        try {
            int executionMember = item.readExecutionMember(stack);
            if (executionMember >= 0) {
                if (executionMember >= payload.memberPatterns().size()) return null;
                var stored = payload.memberPatterns().get(executionMember);
                var delegate = appeng.api.crafting.PatternDetailsHelper.decodePattern(
                        stored.pattern().toItemStack(level.registryAccess()), level);
                if (delegate == null || delegate instanceof TianshuClosedLoopPatternDetails) return null;
                var seedAmounts = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
                for (var seed : payload.seeds()) seedAmounts.merge(
                        seed.what(), seed.amount(), com.moakiee.thunderbolt.core.planner.Sat::add);
                return ClosedLoopExpandedPatternDetails.wrap(
                        delegate, seedAmounts, payload.memberPatterns().size() == 1, what,
                        executionMember);
            }
            if (!payload.enabled()) return null;
            return new Ae2ClosedLoopPatternDetails(what, payload, level);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
