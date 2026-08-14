package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MatrixPatternStorageValidationContractTest {
    @Test
    void patternValidationRejectsOrdinaryItemsBeforeDecoding() throws Exception {
        String storage = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/MatrixPatternStorageBlockEntity.java"));

        String validation = methodBody(storage,
                "public boolean isValidPatternStack(ItemStack stack)");
        int encodedPatternGuard = validation.indexOf(
                "!PatternDetailsHelper.isEncodedPattern(stack)");
        int decodePattern = validation.indexOf("PatternDetailsHelper.decodePattern(stack, level)");

        assertTrue(encodedPatternGuard >= 0,
                "Ordinary automation inputs need a cheap pattern-item guard");
        assertTrue(decodePattern > encodedPatternGuard,
                "Only encoded patterns should pay the full decoder and component-hash cost");
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method: " + signature);
        int bodyStart = source.indexOf('{', start);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return source.substring(bodyStart, i + 1);
            }
        }
        throw new AssertionError("Unterminated method: " + signature);
    }
}
