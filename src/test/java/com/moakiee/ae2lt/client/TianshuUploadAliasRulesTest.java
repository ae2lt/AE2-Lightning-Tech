package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TianshuUploadAliasRulesTest {
    @Test
    void namespaceAliasesUseAnExplicitWholeIdWildcard() {
        assertEquals("mekanism:*", TianshuUploadAliasRules.namespaceGlob("mekanism"));
        assertEquals("minecraft:*", TianshuUploadAliasRules.namespaceGlob(" minecraft "));
        assertEquals("", TianshuUploadAliasRules.namespaceGlob(""));
        assertEquals("", TianshuUploadAliasRules.namespaceGlob(null));
    }
}

