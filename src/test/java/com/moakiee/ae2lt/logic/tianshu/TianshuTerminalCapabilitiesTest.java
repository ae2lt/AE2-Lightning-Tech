package com.moakiee.ae2lt.logic.tianshu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalAction;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalCapabilities;
import org.junit.jupiter.api.Test;

class TianshuTerminalCapabilitiesTest {
    @Test
    void standaloneTerminalKeepsOrdinaryWorkAndLocalClosedLoopEncoding() {
        var capabilities = TianshuTerminalCapabilities.withoutTianshu();

        assertTrue(capabilities.allows(TianshuTerminalAction.ENCODE_NORMAL_PATTERN));
        assertTrue(capabilities.allows(TianshuTerminalAction.UPLOAD_NORMAL_PATTERN));
        assertTrue(capabilities.allows(TianshuTerminalAction.ENCODE_CLOSED_LOOP_PATTERN));
        assertFalse(capabilities.allows(TianshuTerminalAction.UPLOAD_CLOSED_LOOP_PATTERN));
        assertFalse(capabilities.allows(TianshuTerminalAction.CONFIGURE_INVENTORY_MAINTENANCE));
        assertFalse(capabilities.allows(TianshuTerminalAction.CONFIGURE_RESERVED_STOCK));
    }

    @Test
    void closedLoopUploadRequiresWarehouseAndSeedStorage() {
        var incomplete = TianshuTerminalCapabilities.forTianshu(true,
                new TianshuFunctionProfile(1, 0));
        var complete = TianshuTerminalCapabilities.forTianshu(true,
                new TianshuFunctionProfile(1, 1));

        assertFalse(incomplete.allows(TianshuTerminalAction.UPLOAD_CLOSED_LOOP_PATTERN));
        assertTrue(complete.allows(TianshuTerminalAction.UPLOAD_CLOSED_LOOP_PATTERN));
        assertTrue(complete.allows(TianshuTerminalAction.CONFIGURE_INVENTORY_MAINTENANCE));
        assertTrue(complete.allows(TianshuTerminalAction.CONFIGURE_RESERVED_STOCK));
    }
}
