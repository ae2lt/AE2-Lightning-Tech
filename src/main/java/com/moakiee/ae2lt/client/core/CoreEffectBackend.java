package com.moakiee.ae2lt.client.core;

import com.moakiee.ae2lt.client.core.veil.VeilCoreEffectShaders;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.slf4j.Logger;

final class CoreEffectBackend {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DefaultArtifactVersion MINIMUM_VEIL_VERSION =
            new DefaultArtifactVersion("4.3.0");
    private static final DefaultArtifactVersion MAXIMUM_VEIL_VERSION =
            new DefaultArtifactVersion("5.0.0");

    private static volatile boolean veilUsable = detectCompatibleVeil();

    private CoreEffectBackend() {
    }

    static boolean useVeil() {
        return veilUsable;
    }

    static void disableVeil(Throwable cause) {
        if (veilUsable) {
            veilUsable = false;
            LOGGER.warn("Veil core-effect backend failed; falling back to native shaders", cause);
        }
    }

    private static boolean detectCompatibleVeil() {
        if (!ModList.get().isLoaded("veil")) {
            return false;
        }

        var installedVersion = ModList.get().getModContainerById("veil")
                .map(container -> container.getModInfo().getVersion())
                .orElse(null);
        if (installedVersion == null
                || installedVersion.compareTo(MINIMUM_VEIL_VERSION) < 0
                || installedVersion.compareTo(MAXIMUM_VEIL_VERSION) >= 0) {
            LOGGER.warn("Unsupported Veil version {} installed; AE2LT supports [4.3.0,5.0.0) "
                    + "and will use native core-effect shaders", installedVersion);
            return false;
        }

        try {
            boolean compatible = VeilCoreEffectShaders.isApiCompatible();
            if (!compatible) {
                LOGGER.warn("Veil is installed but its shader bridge API is incompatible; "
                        + "using native core-effect shaders");
            }
            return compatible;
        } catch (LinkageError | RuntimeException exception) {
            LOGGER.warn("Veil is installed but its shader bridge API is unavailable; "
                    + "using native core-effect shaders", exception);
            return false;
        }
    }
}
