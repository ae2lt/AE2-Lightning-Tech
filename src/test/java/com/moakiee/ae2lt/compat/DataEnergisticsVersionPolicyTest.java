package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;

final class DataEnergisticsVersionPolicyTest {
    @Test
    void warnsBeforeTheCee32c9ReleaseBoundary() {
        assertTrue(DataEnergisticsVersionPolicy.shouldWarn(
                new DefaultArtifactVersion("2.3.2")));
        assertTrue(DataEnergisticsVersionPolicy.shouldWarn(
                new DefaultArtifactVersion("2.4.3")));
        assertTrue(DataEnergisticsVersionPolicy.shouldWarn(
                new DefaultArtifactVersion("2.4.4-beta.1")));
    }

    @Test
    void staysSilentFromVersion244Onward() {
        assertFalse(DataEnergisticsVersionPolicy.shouldWarn(
                new DefaultArtifactVersion("2.4.4")));
        assertFalse(DataEnergisticsVersionPolicy.shouldWarn(
                new DefaultArtifactVersion("2.4.5")));
    }

    @Test
    void unknownVersionsFailSafeToWarning() {
        assertTrue(DataEnergisticsVersionPolicy.shouldWarn(null));
    }
}
