package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class PortMetadataContractTest {
    private static final Path PROPERTIES_FILE = Path.of("gradle.properties");
    private static final Path MODS_TOML = Path.of("src", "main", "resources", "META-INF", "mods.toml");

    @Test
    void requiredDependenciesMatchTheVerifiedForgeBaseline() throws IOException {
        Properties properties = loadProperties();

        // Baseline synced with AE2 15.4.10 (AE2 declares forge [47.1.3,) and minecraft [1.20.1,1.20.2)).
        assertEquals("[1.20.1,1.20.2)", properties.getProperty("minecraft_version_range"));
        assertEquals("[47.1.3,)", properties.getProperty("forge_version_range"));
        assertEquals("[15.4.10,16)", properties.getProperty("ae2_version_range"));
        assertEquals("[1.0.7,1.1)", properties.getProperty("thunderbolt_version_range"));
    }

    @Test
    void everyApiBackedIntegrationHasAnExpandedVersionContract() throws IOException {
        String metadata = Files.readString(MODS_TOML);
        List<String> rangeProperties = List.of(
                "ae2_version_range",
                "jade_version_range",
                "jei_version_range",
                "emi_version_range",
                "advancedae_version_range",
                "extendedae_version_range",
                "appflux_version_range",
                "flux_networks_version_range",
                "mekanism_version_range",
                "curios_version_range",
                "polymorph_version_range",
                "thunderbolt_version_range",
                "ae2wtlib_version_range",
                "veil_version_range");

        for (String property : rangeProperties) {
            assertTrue(metadata.contains("versionRange = \"${" + property + "}\""), property);
        }
        assertFalse(metadata.contains("versionRange = \"[0,)\""));
    }

    @Test
    void veilIntegrationMatchesTheVerifiedReleaseFamily() throws IOException {
        Properties properties = loadProperties();

        assertEquals("[1.0.0,2)", properties.getProperty("veil_version_range"));
    }

    @Test
    void inventoryProfilesApiRemainsCompileTimeOnly() throws IOException {
        String buildScript = Files.readString(Path.of("build.gradle"));

        assertTrue(buildScript.contains(
                "compileOnly fg.deobf(\"maven.modrinth:inventory-profiles-next:${inventory_profiles_next_version}\")"));
        assertFalse(buildScript.contains(
                "runtimeOnly fg.deobf(\"maven.modrinth:inventory-profiles-next"));
        assertTrue(loadProperties().containsKey("inventory_profiles_next_version"));
    }

    @Test
    void publishedArtifactsRetainLicenseAndThirdPartyNotices() throws IOException {
        for (String file : List.of("LICENSE", "LICENSE_ASSETS.md", "THIRD_PARTY_NOTICES.md")) {
            assertTrue(Files.size(Path.of(file)) > 0, file);
        }

        String buildScript = Files.readString(Path.of("build.gradle"));
        assertTrue(buildScript.contains("from('LICENSE')"));
        assertTrue(buildScript.contains("from('LICENSE_ASSETS.md')"));
        assertTrue(buildScript.contains("from('THIRD_PARTY_NOTICES.md')"));
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(PROPERTIES_FILE)) {
            properties.load(reader);
        }
        return properties;
    }
}
