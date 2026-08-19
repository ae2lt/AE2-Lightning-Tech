package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import appeng.api.stacks.AEKey;
import com.moakiee.thunderbolt.core.crafting.loop.ReusableSeedPattern;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ClosedLoopPublicationSupportTest {
    @Test
    void publishedDefinitionSkipsFullValidation() {
        var validations = new AtomicInteger();

        var result = ClosedLoopPublicationSupport.reusePublishedOrValidate(
                "published", Set.of("published"), () -> {
                    validations.incrementAndGet();
                    return "rebuilt";
                });

        assertEquals("published", result);
        assertEquals(0, validations.get());
    }

    @Test
    void newDefinitionMustPassFullValidation() {
        var validations = new AtomicInteger();

        var result = ClosedLoopPublicationSupport.reusePublishedOrValidate(
                "candidate", Set.of(), () -> {
                    validations.incrementAndGet();
                    return "validated";
                });

        assertEquals("validated", result);
        assertEquals(1, validations.get());
        assertNull(ClosedLoopPublicationSupport.reusePublishedOrValidate(
                "invalid", Set.of(), () -> null));
    }

    @Test
    void seedSnapshotIsSharedWithinOnePublicationPass() {
        var snapshots = new AtomicInteger();
        Map<AEKey, Long> sourceSnapshot = Map.of();
        var memoizer = new ClosedLoopPublicationSupport.SeedSnapshotMemoizer(ignored -> {
            snapshots.incrementAndGet();
            return sourceSnapshot;
        });

        var first = memoizer.apply((ReusableSeedPattern) null);
        var second = memoizer.apply((ReusableSeedPattern) null);

        assertSame(first, second);
        assertEquals(1, snapshots.get());
    }

    @Test
    void failedSeedSnapshotIsNotCached() {
        var snapshots = new AtomicInteger();
        var memoizer = new ClosedLoopPublicationSupport.SeedSnapshotMemoizer(ignored -> {
            if (snapshots.incrementAndGet() == 1) {
                throw new IllegalStateException("transient inventory failure");
            }
            return Map.of();
        });

        assertThrows(IllegalStateException.class,
                () -> memoizer.apply((ReusableSeedPattern) null));
        memoizer.apply((ReusableSeedPattern) null);
        memoizer.apply((ReusableSeedPattern) null);
        assertEquals(2, snapshots.get());
    }
}
