package com.moakiee.ae2lt.celestweave.service;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.moakiee.ae2lt.AE2LightningTech;

public final class CelestweaveAdvancementService {
    private static final ResourceLocation RADIATION_ASSIMILATION =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "main/radiation_assimilation");
    private static final String RADIATION_HEALING_CRITERION = "radiation_healing";

    private CelestweaveAdvancementService() {
    }

    public static void awardRadiationAssimilation(ServerPlayer player) {
        var advancement = player.server.getAdvancements().get(RADIATION_ASSIMILATION);
        if (advancement != null) {
            player.getAdvancements().award(advancement, RADIATION_HEALING_CRITERION);
        }
    }
}
