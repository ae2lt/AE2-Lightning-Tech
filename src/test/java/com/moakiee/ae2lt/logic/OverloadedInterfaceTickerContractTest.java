package com.moakiee.ae2lt.logic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OverloadedInterfaceTickerContractTest {
    private static final Path LOGIC_SOURCE = Path.of(
            "src/main/java/com/moakiee/ae2lt/logic/OverloadedInterfaceLogic.java");

    @Test
    void proxyTickerMustAcceptAlertsFromInterfaceStateChanges() throws IOException {
        var source = Files.readString(LOGIC_SOURCE);

        assertTrue(source.contains(
                "new TickingRequest(MIN_TICKS, MAX_TICKS, false, true)"),
                "The interface calls alertDevice after mode and connection changes, so its ticker must be alertable");
    }
}
