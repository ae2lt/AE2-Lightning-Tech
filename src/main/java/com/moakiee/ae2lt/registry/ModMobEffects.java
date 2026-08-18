package com.moakiee.ae2lt.registry;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.effect.ElectromagneticParalysisEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class ModMobEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, AE2LightningTech.MODID);

    public static final RegistryObject<ElectromagneticParalysisEffect>
            ELECTROMAGNETIC_PARALYSIS = EFFECTS.register(
                    "electromagnetic_paralysis",
                    () -> {
                        var effect = new ElectromagneticParalysisEffect();
                        // 1.20.1 signature: addAttributeModifier(Attribute, String, double, Operation)
                        // with the String being a UUID; the ResourceLocation variant used by the
                        // NeoForge port arrived in 1.20.5 and would fail UUID parsing here.
                        effect.addAttributeModifier(
                                Attributes.MOVEMENT_SPEED,
                                "2c6f9d8a-4e5b-4a3f-b7d1-9e8c2a5f0b64",
                                -0.75D,
                                AttributeModifier.Operation.MULTIPLY_TOTAL);
                        return effect;
                    });

    private ModMobEffects() {}
}
