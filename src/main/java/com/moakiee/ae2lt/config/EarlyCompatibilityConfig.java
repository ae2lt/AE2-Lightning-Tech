package com.moakiee.ae2lt.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads compatibility switches that are needed before NeoForge loads the normal mod config.
 *
 * <p>Mixin selection is finalized during bootstrap, so these values are intentionally captured
 * once for the lifetime of the process. Changing them requires a full client or server restart.
 */
public final class EarlyCompatibilityConfig {
    static final String SECTION = "compatibility";
    static final String DATA_ENERGISTICS_PROTECTION_KEY = "dataEnergisticsMixinProtection";
    private static final boolean DEFAULT_DATA_ENERGISTICS_PROTECTION = true;
    private static final Path COMMON_CONFIG_PATH = Path.of("config", "ae2lt-common.toml");

    private static final boolean DATA_ENERGISTICS_PROTECTION =
            readDataEnergisticsProtection(COMMON_CONFIG_PATH);

    private EarlyCompatibilityConfig() {
    }

    public static boolean dataEnergisticsMixinProtectionEnabled() {
        return DATA_ENERGISTICS_PROTECTION;
    }

    static boolean readDataEnergisticsProtection(Path configPath) {
        if (!Files.isRegularFile(configPath)) {
            return DEFAULT_DATA_ENERGISTICS_PROTECTION;
        }

        boolean inCompatibilitySection = false;
        try {
            for (String rawLine : Files.readAllLines(configPath, StandardCharsets.UTF_8)) {
                String line = stripComment(rawLine).strip();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    inCompatibilitySection = line.equals("[" + SECTION + "]");
                    continue;
                }

                int separator = line.indexOf('=');
                if (separator < 0) {
                    continue;
                }
                String key = line.substring(0, separator).strip();
                boolean matchingSectionKey = inCompatibilitySection
                        && key.equals(DATA_ENERGISTICS_PROTECTION_KEY);
                boolean matchingDottedKey = key.equals(
                        SECTION + "." + DATA_ENERGISTICS_PROTECTION_KEY);
                if (!matchingSectionKey && !matchingDottedKey) {
                    continue;
                }

                String value = line.substring(separator + 1).strip();
                if (value.equalsIgnoreCase("true")) {
                    return true;
                }
                if (value.equalsIgnoreCase("false")) {
                    return false;
                }
                return DEFAULT_DATA_ENERGISTICS_PROTECTION;
            }
        } catch (IOException | SecurityException ignored) {
            return DEFAULT_DATA_ENERGISTICS_PROTECTION;
        }
        return DEFAULT_DATA_ENERGISTICS_PROTECTION;
    }

    private static String stripComment(String line) {
        int commentStart = line.indexOf('#');
        return commentStart < 0 ? line : line.substring(0, commentStart);
    }
}
