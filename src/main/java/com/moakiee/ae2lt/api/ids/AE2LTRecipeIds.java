package com.moakiee.ae2lt.api.ids;

import net.minecraft.resources.ResourceLocation;

/**
 * Frozen registry IDs for the public-facing recipe types of AE2 Lightning Tech.
 *
 * <p>These constants are part of the API contract. The mod will not change the
 * registered ID of any of these recipe types without a major version bump.
 */
public final class AE2LTRecipeIds {

    private static final String MOD_ID = AE2LTBlockEntityIds.MOD_ID;

    public static final ResourceLocation LIGHTNING_ASSEMBLY =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "lightning_assembly");

    public static final ResourceLocation LIGHTNING_TRANSFORM =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "lightning_transform");

    public static final ResourceLocation LIGHTNING_SIMULATION =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "lightning_simulation");

    public static final ResourceLocation OVERLOAD_PROCESSING =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "overload_processing");

    public static final ResourceLocation CRYSTAL_CATALYZER =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "crystal_catalyzer");

    public static final ResourceLocation LIGHTNING_STRIKE =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "lightning_strike");

    private AE2LTRecipeIds() {
    }
}
