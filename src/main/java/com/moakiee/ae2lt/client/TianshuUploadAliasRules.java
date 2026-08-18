package com.moakiee.ae2lt.client;

/** Formatting rules for automatically generated upload-target aliases. */
public final class TianshuUploadAliasRules {
    private TianshuUploadAliasRules() {
    }

    public static String namespaceGlob(String namespace) {
        return namespace == null || namespace.isBlank() ? "" : namespace.strip() + ":*";
    }
}
