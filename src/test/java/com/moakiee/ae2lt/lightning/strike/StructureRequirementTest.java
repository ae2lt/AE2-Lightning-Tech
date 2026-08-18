package com.moakiee.ae2lt.lightning.strike;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StructureRequirementTest {
    @Test
    void networkReaderMatchesWriterFieldOrder() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/lightning/strike/StructureRequirement.java"));
        String reader = source.substring(
                source.indexOf("public static StructureRequirement fromNetwork"),
                source.indexOf("public void toNetwork"));
        String writer = source.substring(source.indexOf("public void toNetwork"));

        assertTrue(reader.indexOf("readBlockPos()") < reader.indexOf("readResourceLocation()"));
        assertTrue(writer.indexOf("writeBlockPos(offset)") < writer.indexOf("writeResourceLocation("));
    }
}
