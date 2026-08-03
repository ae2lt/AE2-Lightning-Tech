package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TianshuPolymorphIntegrationSourceContractTest {
    @Test
    void optionalBootstrapGuardsApiClassLoadingAndRegistersTianshuScreen() throws Exception {
        var bootstrap = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/compat/TianshuPolymorphCompatBootstrap.java"));
        var integration = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/compat/TianshuPolymorphClientCompat.java"));
        var widget = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/compat/TianshuPatternTerminalWidget.java"));

        assertTrue(bootstrap.contains("isLoaded(\"polymorph\")"));
        assertTrue(bootstrap.contains("isLoaded(\"polyeng\")"));
        assertTrue(bootstrap.contains("TianshuPolymorphClientCompat::register"));
        assertTrue(integration.contains("screen instanceof TianshuPatternEncodingTermScreen<?>"));
        assertTrue(widget.contains("tianshuMode == TianshuEncodingMode.CRAFTING"));
        assertTrue(widget.contains("refreshPolymorphRecipe()"));
    }
}
