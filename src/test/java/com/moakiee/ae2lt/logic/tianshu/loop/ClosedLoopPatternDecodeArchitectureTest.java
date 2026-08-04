package com.moakiee.ae2lt.logic.tianshu.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClosedLoopPatternDecodeArchitectureTest {
    @Test
    void validationAvailabilityAndRuntimeDetailsShareOneMemberDecode() throws Exception {
        var loopRoot = Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/tianshu/loop");
        var decoder = Files.readString(loopRoot.resolve("ClosedLoopPatternDecoder.java"));
        var validator = Files.readString(loopRoot.resolve("ClosedLoopPatternValidator.java"));
        var details = Files.readString(loopRoot.resolve("Ae2ClosedLoopPatternDetails.java"));
        var controller = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/"
                        + "TianshuSupercomputerControllerBlockEntity.java"));

        assertEquals(1, occurrences(decoder, ".toItemStack("),
                "only the closed-loop decoder may restore member snapshots");
        assertTrue(validator.contains(
                "ClosedLoopPatternDecoder.decodePayload(payload, level).validation()"));
        assertFalse(validator.contains(".toItemStack("));
        assertFalse(details.contains(".toItemStack("));
        assertFalse(details.contains("PatternDetailsHelper.decodePattern"));
        assertTrue(controller.contains("decodedClosedLoopPatterns.computeIfAbsent("));
        assertTrue(controller.contains("membersAreAvailable(decoded.members())"));
        assertTrue(controller.contains("decoded.createDetails("));
        assertFalse(controller.contains("PatternDetailsHelper.decodePattern"));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int offset = 0; (offset = source.indexOf(needle, offset)) >= 0;
                offset += needle.length()) {
            count++;
        }
        return count;
    }
}
