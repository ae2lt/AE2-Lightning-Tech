package com.moakiee.ae2lt.overload.runtime.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OverloadPatternConcreteKeyTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void idOnlyMetadataKeepsConcreteInputAndOutputTemplates() {
        var capturedInput = namedPickaxe("captured-input");
        var capturedOutput = namedPickaxe("captured-output");
        var parsed = new ParsedPatternDefinition(
                new SourcePatternSnapshot(
                        new ResourceLocation("ae2lt", "test_pattern"),
                        null,
                        null),
                List.of(new ParsedPatternInput(0, capturedInput)),
                List.of(new ParsedPatternOutput(0, capturedOutput, true)));
        var metadata = EncodedOverloadPattern.builder()
                .input(0, MatchMode.ID_ONLY)
                .output(0, MatchMode.ID_ONLY)
                .build();

        var details = new OverloadPatternDetails(parsed, metadata);

        assertTrue(ItemStack.isSameItemSameTags(
                capturedInput, details.inputs().get(0).template()));
        assertTrue(ItemStack.isSameItemSameTags(
                capturedOutput, details.outputs().get(0).template()));
        assertEquals(MatchMode.ID_ONLY, details.inputMode(0));
        assertEquals(MatchMode.ID_ONLY, details.outputMode(0));
    }

    @Test
    void ae2ViewKeepsConcreteAnchorsWhileIsValidProvidesSameIdMembership() {
        var capturedInput = namedPickaxe("captured-input");
        var capturedOutput = namedPickaxe("captured-output");
        var inputKey = AEItemKey.of(capturedInput);
        var outputKey = AEItemKey.of(capturedOutput);
        var parsed = new ParsedPatternDefinition(
                new SourcePatternSnapshot(
                        new ResourceLocation("ae2lt", "test_pattern"),
                        null,
                        null),
                List.of(new ParsedPatternInput(0, capturedInput)),
                List.of(new ParsedPatternOutput(0, capturedOutput, true)));
        var metadata = EncodedOverloadPattern.builder()
                .input(0, MatchMode.ID_ONLY)
                .output(0, MatchMode.ID_ONLY)
                .build();
        var overload = new OverloadPatternDetails(parsed, metadata);
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
        var runtimeVariant = AEItemKey.of(namedPickaxe("runtime-variant"));

        assertEquals(inputKey, details.getInputs()[0].getPossibleInputs()[0].what());
        assertTrue(details.getInputs()[0].isValid(runtimeVariant, null));
        assertEquals(outputKey, details.getOutputs()[0].what());
        assertTrue(details.producesSameIdVariants(0));
    }

    private static ItemStack namedPickaxe(String name) {
        var stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.setHoverName(Component.literal(name));
        return stack;
    }
}
