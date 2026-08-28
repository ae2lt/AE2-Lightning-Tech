package com.moakiee.ae2lt.overload.runtime.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.overload.runtime.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.runtime.model.MatchMode;

class IdOnlySecondaryIdentityTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void idOnlyMetadataPreservesCapabilityOnlyIdentity() {
        var capturedInput = capabilityOnlyStack(42);
        var capturedOutput = capabilityOnlyStack(84);
        capturedInput.setCount(4);
        capturedOutput.setCount(3);

        assertFalse(AEItemKey.of(capturedInput).equals(AEItemKey.of(capturedInput).dropSecondary()),
                "fixture must carry a distinct Forge capability identity");

        var details = new OverloadPatternDetails(
                parsed(capturedInput, capturedOutput), idOnlyMetadata());

        assertEquals(AEItemKey.of(capturedInput), AEItemKey.of(details.inputs().get(0).template()));
        assertEquals(AEItemKey.of(capturedOutput), AEItemKey.of(details.outputs().get(0).template()));
        assertEquals(1, details.inputs().get(0).template().getCount());
        assertEquals(1, details.outputs().get(0).template().getCount());
    }

    @Test
    void ae2ViewKeepsCapabilityIdentityWhileAcceptingSameItemVariants() {
        var capturedInput = capabilityOnlyStack(42);
        var capturedOutput = capabilityOnlyStack(84);
        var inputKey = AEItemKey.of(capturedInput);
        var outputKey = AEItemKey.of(capturedOutput);
        var overload = new OverloadPatternDetails(
                parsed(capturedInput, capturedOutput), idOnlyMetadata());
        IPatternDetails source = new IPatternDetails() {
            private final IInput[] inputs = {new IInput() {
                private final GenericStack[] possible = {new GenericStack(inputKey, 1)};

                @Override public GenericStack[] getPossibleInputs() { return possible; }
                @Override public long getMultiplier() { return 1; }
                @Override public boolean isValid(AEKey key, Level level) {
                    return inputKey.equals(key);
                }
                @Override public AEKey getRemainingKey(AEKey template) { return null; }
            }};

            @Override public AEItemKey getDefinition() { return inputKey; }
            @Override public IInput[] getInputs() { return inputs; }
            @Override public GenericStack[] getOutputs() {
                return new GenericStack[] {new GenericStack(outputKey, 1)};
            }
        };

        var details = new Ae2OverloadPatternDetails(inputKey, overload, source);
        var runtimeVariant = AEItemKey.of(capabilityOnlyStack(99));

        assertEquals(inputKey, details.getInputs()[0].getPossibleInputs()[0].what());
        assertEquals(outputKey, details.getOutputs()[0].what());
        assertTrue(details.getInputs()[0].isValid(runtimeVariant, null));
        assertTrue(details.producesSameIdVariants(0));
    }

    private static ParsedPatternDefinition parsed(ItemStack input, ItemStack output) {
        return new ParsedPatternDefinition(
                new SourcePatternSnapshot(
                        new ResourceLocation("ae2lt", "capability_identity_test"), null, null),
                List.of(new ParsedPatternInput(0, input)),
                List.of(new ParsedPatternOutput(0, output, true)));
    }

    private static EncodedOverloadPattern idOnlyMetadata() {
        return EncodedOverloadPattern.builder()
                .input(0, MatchMode.ID_ONLY)
                .output(0, MatchMode.ID_ONLY)
                .build();
    }

    private static ItemStack capabilityOnlyStack(int energy) {
        var caps = new CompoundTag();
        caps.putInt("ae2lt:test_energy", energy);
        return new ItemStack(Items.DIAMOND, 1, caps);
    }
}
