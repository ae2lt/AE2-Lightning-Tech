package com.moakiee.ae2lt.overload.runtime.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.overload.runtime.model.MatchMode;

class IdOnlySecondaryIdentityTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void idOnlyErasesCapabilityOnlyIdentityFromAe2FacingKey() {
        var exactKey = AEItemKey.of(capabilityOnlyStack());

        assertFalse(exactKey.hasTag(), "fixture must not use ordinary item NBT");
        assertFalse(exactKey.equals(exactKey.dropSecondary()),
                "fixture must carry a distinct Forge capability identity");

        var wiped = Ae2OverloadPatternDetails.wipeIfIdOnly(
                new GenericStack(exactKey, 7), MatchMode.ID_ONLY);

        assertEquals(exactKey.dropSecondary(), wiped.what());
        assertEquals(7, wiped.amount());
    }

    @Test
    void idOnlyErasesCapabilityOnlyIdentityFromRuntimeMetadata() {
        var source = capabilityOnlyStack();
        source.setCount(4);

        var wiped = OverloadPatternDetails.wipeIfIdOnly(source, MatchMode.ID_ONLY);

        assertEquals(4, wiped.getCount());
        assertNull(wiped.getTag());
        assertEquals(AEItemKey.of(Items.DIAMOND), AEItemKey.of(wiped));
        assertFalse(AEItemKey.of(source).equals(AEItemKey.of(wiped)));
    }

    @Test
    void strictModePreservesCapabilityIdentity() {
        var source = capabilityOnlyStack();
        var stack = new GenericStack(AEItemKey.of(source), 1);

        assertSame(stack, Ae2OverloadPatternDetails.wipeIfIdOnly(stack, MatchMode.STRICT));
        assertSame(source, OverloadPatternDetails.wipeIfIdOnly(source, MatchMode.STRICT));
    }

    private static ItemStack capabilityOnlyStack() {
        var caps = new CompoundTag();
        caps.putInt("ae2lt:test_energy", 42);
        return new ItemStack(Items.DIAMOND, 1, caps);
    }
}
