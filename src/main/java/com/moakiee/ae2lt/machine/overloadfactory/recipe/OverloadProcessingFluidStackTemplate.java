package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

public record OverloadProcessingFluidStackTemplate(
        Holder<Fluid> fluid,
        int amount,
        DataComponentPatch components) {
    @SuppressWarnings("deprecation")
    public static final OverloadProcessingFluidStackTemplate EMPTY =
            new OverloadProcessingFluidStackTemplate(
                    Fluids.EMPTY.builtInRegistryHolder(),
                    0,
                    DataComponentPatch.EMPTY);

    public static final Codec<OverloadProcessingFluidStackTemplate> CODEC =
            RecordCodecBuilder.<OverloadProcessingFluidStackTemplate>create(instance -> instance.group(
                            BuiltInRegistries.FLUID.holderByNameCodec()
                                    .fieldOf("id")
                                    .forGetter(OverloadProcessingFluidStackTemplate::fluid),
                            Codec.INT.fieldOf("amount").forGetter(OverloadProcessingFluidStackTemplate::amount),
                            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                                    .forGetter(OverloadProcessingFluidStackTemplate::components))
                    .apply(instance, OverloadProcessingFluidStackTemplate::new))
                    .validate(OverloadProcessingFluidStackTemplate::validate);

    public static final StreamCodec<RegistryFriendlyByteBuf, OverloadProcessingFluidStackTemplate> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.holderRegistry(Registries.FLUID),
                    OverloadProcessingFluidStackTemplate::fluid,
                    ByteBufCodecs.VAR_INT,
                    OverloadProcessingFluidStackTemplate::amount,
                    DataComponentPatch.STREAM_CODEC,
                    OverloadProcessingFluidStackTemplate::components,
                    OverloadProcessingFluidStackTemplate::new);

    public OverloadProcessingFluidStackTemplate {
        Objects.requireNonNull(fluid, "fluid");
        Objects.requireNonNull(components, "components");
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }

    public static OverloadProcessingFluidStackTemplate from(FluidStack stack) {
        if (stack.isEmpty()) {
            return EMPTY;
        }
        @SuppressWarnings("deprecation")
        Holder<Fluid> fluid = stack.getFluid().builtInRegistryHolder();
        return new OverloadProcessingFluidStackTemplate(
                fluid,
                stack.getAmount(),
                stack.getComponentsPatch());
    }

    public boolean isEmpty() {
        return isEmptyFluid() || amount <= 0;
    }

    public FluidStack create() {
        if (isEmpty()) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(fluid, amount, components);
    }

    private boolean isEmptyFluid() {
        return fluid.is(Fluids.EMPTY.builtInRegistryHolder());
    }

    private static DataResult<OverloadProcessingFluidStackTemplate> validate(
            OverloadProcessingFluidStackTemplate template) {
        if (template.isEmptyFluid()) {
            if (template.amount == 0) {
                return DataResult.success(EMPTY);
            }
            return DataResult.error(() -> "empty fluid must have amount 0");
        }
        if (template.amount <= 0) {
            return DataResult.error(() -> "fluid amount must be positive");
        }
        return DataResult.success(template);
    }
}
