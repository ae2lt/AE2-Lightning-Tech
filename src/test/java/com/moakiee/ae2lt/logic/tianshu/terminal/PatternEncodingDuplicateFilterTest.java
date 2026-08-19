package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopMemberPattern;
import com.moakiee.ae2lt.logic.tianshu.loop.ClosedLoopPatternPayload;
import com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class PatternEncodingDuplicateFilterTest {
    @Test
    void closedLoopRuntimeStateDoesNotBypassDuplicateDetection() {
        var stored = payload("member", 1, 1, 2, false);
        var reauthored = payload("member", 1, 1, 2, true);

        assertTrue(PatternEncodingDuplicateFilter.sameClosedLoopPayload(stored, reauthored));
    }

    @Test
    void changedClosedLoopDefinitionRemainsEncodable() {
        var stored = payload("member", 1, 1, 2, true);

        assertFalse(PatternEncodingDuplicateFilter.sameClosedLoopPayload(
                stored, payload("other_member", 1, 1, 2, true)));
        assertFalse(PatternEncodingDuplicateFilter.sameClosedLoopPayload(
                stored, payload("member", 2, 1, 2, true)));
        assertFalse(PatternEncodingDuplicateFilter.sameClosedLoopPayload(
                stored, payload("member", 1, 1, 3, true)));
    }

    private static ClosedLoopPatternPayload payload(
            String memberId,
            int executionMultiplier,
            int storedMultiplier,
            long outputAmount,
            boolean enabled) {
        var member = new SourcePatternSnapshot(
                new ResourceLocation("ae2lt_duplicate_test", memberId),
                null, null);
        return new ClosedLoopPatternPayload(
                List.of(new ClosedLoopMemberPattern(member, 1)),
                List.of(new GenericStack(new TestKey("seed"), 1)),
                List.of(new GenericStack(new TestKey("input"), 4)),
                List.of(new GenericStack(new TestKey("output"), outputAmount)),
                executionMultiplier,
                storedMultiplier,
                enabled);
    }

    private static final class TestKey extends AEKey {
        private static final TestKeyType TYPE = new TestKeyType();
        private final String id;

        private TestKey(String id) {
            this.id = id;
        }

        @Override
        public AEKeyType getType() {
            return TYPE;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag() {
            var tag = new CompoundTag();
            tag.putString("id", id);
            return tag;
        }

        @Override
        public Object getPrimaryKey() {
            return id;
        }

        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("ae2lt_duplicate_test", id);
        }

        @Override
        public void writeToPacket(FriendlyByteBuf data) {
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id);
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TestKey other && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(new ResourceLocation("ae2lt_duplicate_test", "key"),
                    TestKey.class, Component.literal("test key"));
        }

        @Override
        public AEKey loadKeyFromTag(CompoundTag tag) {
            return null;
        }

        @Override
        public AEKey readFromPacket(FriendlyByteBuf input) {
            return null;
        }
    }
}
