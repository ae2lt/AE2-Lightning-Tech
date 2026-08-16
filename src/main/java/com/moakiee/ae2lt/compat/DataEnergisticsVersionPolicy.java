package com.moakiee.ae2lt.compat;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/** Controls only the user-facing warning; the mixin ownership boundary remains version-independent. */
public final class DataEnergisticsVersionPolicy {
    // 2.4.4 is the first release built from cee32c9, where the AE2LT compatibility
    // mixins were removed. Earlier releases keep the legacy warning.
    private static final ArtifactVersion SILENT_FROM = new DefaultArtifactVersion("2.4.4");

    private DataEnergisticsVersionPolicy() {}

    public static boolean shouldWarn(ArtifactVersion version) {
        return version == null || version.compareTo(SILENT_FROM) < 0;
    }
}
