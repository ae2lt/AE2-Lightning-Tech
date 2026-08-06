package com.moakiee.ae2lt.api.ids;

import net.minecraft.resources.ResourceLocation;

/**
 * Frozen registry IDs for the public-facing block entities of AE2 Lightning Tech.
 *
 * <p>These constants are part of the API contract. The mod will not change the
 * registered ID of any of these block entities without a major version bump.
 *
 * <p>Note the mismatch on the simulation room: the Java class is named
 * {@code LightningSimulationChamberBlockEntity} but its registered path is
 * {@code lightning_simulation_room}. The constant follows the registered path.
 */
public final class AE2LTBlockEntityIds {

    /**
     * Mod id of AE2 Lightning Tech. Frozen as part of the API contract.
     */
    public static final String MOD_ID = "ae2lt";

    public static final ResourceLocation LIGHTNING_COLLECTOR =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "lightning_collector");

    public static final ResourceLocation LIGHTNING_SIMULATION_ROOM =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "lightning_simulation_room");

    public static final ResourceLocation LIGHTNING_ASSEMBLY_CHAMBER =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "lightning_assembly_chamber");

    public static final ResourceLocation OVERLOAD_PROCESSING_FACTORY =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "overload_processing_factory");

    public static final ResourceLocation TESLA_COIL =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "tesla_coil");

    public static final ResourceLocation CRYSTAL_CATALYZER =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "crystal_catalyzer");

    private AE2LTBlockEntityIds() {
    }
}
