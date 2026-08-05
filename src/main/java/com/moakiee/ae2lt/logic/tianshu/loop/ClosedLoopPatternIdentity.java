package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.stacks.AEItemKey;
import com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.HolderLookup;

/**
 * Derives the transient seed-ledger key required by Thunderbolt from encoded pattern content.
 *
 * <p>Closed-loop payloads and repositories do not persist an identity field. The UUID exists only
 * as the content-hash shape currently required by the reusable-seed runtime API.
 */
public final class ClosedLoopPatternIdentity {
    private static final String DOMAIN = "ae2lt:closed-loop:";

    public static UUID runtimeGroupId(
            AEItemKey definition, HolderLookup.Provider registries) {
        if (definition == null || registries == null) {
            throw new IllegalArgumentException("closed-loop definition and registries are required");
        }
        var fingerprint = SourcePatternSnapshot
                .fromItemStack(definition.toStack(), registries)
                .fingerprint();
        return UUID.nameUUIDFromBytes(
                (DOMAIN + fingerprint).getBytes(StandardCharsets.UTF_8));
    }

    private ClosedLoopPatternIdentity() {
    }
}
