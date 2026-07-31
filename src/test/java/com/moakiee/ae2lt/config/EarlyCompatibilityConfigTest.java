package com.moakiee.ae2lt.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class EarlyCompatibilityConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void protectionDefaultsToEnabledWhenTheConfigIsMissing() {
        assertTrue(EarlyCompatibilityConfig.readDataEnergisticsProtection(
                temporaryDirectory.resolve("missing.toml")));
    }

    @Test
    void protectionCanBeDisabledInTheCompatibilitySection() throws Exception {
        Path config = temporaryDirectory.resolve("ae2lt-common.toml");
        Files.writeString(config, """
                [compatibility]
                dataEnergisticsMixinProtection = false # requires a restart

                [network]
                dataEnergisticsMixinProtection = true
                """);

        assertFalse(EarlyCompatibilityConfig.readDataEnergisticsProtection(config));
    }

    @Test
    void malformedValuesFailSafeToEnabled() throws Exception {
        Path config = temporaryDirectory.resolve("ae2lt-common.toml");
        Files.writeString(config, """
                [compatibility]
                dataEnergisticsMixinProtection = maybe
                """);

        assertTrue(EarlyCompatibilityConfig.readDataEnergisticsProtection(config));
    }
}
