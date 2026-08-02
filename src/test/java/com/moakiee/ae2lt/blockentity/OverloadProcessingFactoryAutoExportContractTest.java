package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OverloadProcessingFactoryAutoExportContractTest {
    @Test
    void resultExportIsGuardedByTheAutoExportWorkCheck() throws Exception {
        String source = Files.readString(Path.of(
                        "src/main/java/com/moakiee/ae2lt/blockentity/OverloadProcessingFactoryBlockEntity.java"))
                .replace("\r\n", "\n");

        int methodStart = source.indexOf("public boolean pushOutResult()");
        int methodEnd = source.indexOf("public void onNeighborChanged", methodStart);

        assertTrue(methodStart >= 0, "Missing pushOutResult method");
        assertTrue(methodEnd > methodStart, "Could not isolate pushOutResult method");

        String method = source.substring(methodStart, methodEnd);
        assertTrue(method.contains(
                "if (!hasAutoExportWork() || !(level instanceof ServerLevel serverLevel))"));
        assertTrue(method.contains("AdjacentItemAutoExportHelper.hasAnyOutput(\n                autoExport,"));
        assertFalse(method.contains("AdjacentItemAutoExportHelper.hasAnyOutput(\n                true,"));
    }
}
