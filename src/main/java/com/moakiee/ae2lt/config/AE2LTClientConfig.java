package com.moakiee.ae2lt.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class AE2LTClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<TianshuUploadTrigger> TIANSHU_UPLOAD_TRIGGER;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> TIANSHU_UPLOAD_ALIASES;
    private static final ModConfigSpec.BooleanValue TIANSHU_SHOW_MAINTENANCE_HELP;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("tianshuTerminal");
        TIANSHU_UPLOAD_TRIGGER = builder
                .comment("Modifier condition that starts pattern upload after encoding")
                .defineEnum("uploadTrigger", TianshuUploadTrigger.NO_SHIFT);
        TIANSHU_UPLOAD_ALIASES = builder
                .comment("Pattern output id to pattern-provider alias mappings (output=alias)")
                .defineListAllowEmpty("uploadAliases", List.of(),
                        value -> value instanceof String text
                                && text.length() <= 512
                                && text.indexOf('=') > 0);
        TIANSHU_SHOW_MAINTENANCE_HELP = builder
                .comment("Show the inventory-maintenance explanation before the Shift + middle-click editor")
                .define("showMaintenanceHelp", true);
        builder.pop();
        SPEC = builder.build();
    }

    private AE2LTClientConfig() {
    }

    public static TianshuUploadTrigger uploadTrigger() {
        return TIANSHU_UPLOAD_TRIGGER.get();
    }

    public static void setUploadTrigger(TianshuUploadTrigger trigger) {
        if (trigger == null) return;
        TIANSHU_UPLOAD_TRIGGER.set(trigger);
        if (SPEC.isLoaded()) SPEC.save();
    }

    public static boolean showMaintenanceHelp() {
        return TIANSHU_SHOW_MAINTENANCE_HELP.get();
    }

    public static void setShowMaintenanceHelp(boolean show) {
        TIANSHU_SHOW_MAINTENANCE_HELP.set(show);
        if (SPEC.isLoaded()) SPEC.save();
    }

    public static synchronized String findUploadAlias(String sourceKey) {
        String normalized = normalizeAliasKey(sourceKey);
        if (normalized.isEmpty()) return null;
        for (var entry : TIANSHU_UPLOAD_ALIASES.get()) {
            int separator = entry.indexOf('=');
            if (separator <= 0) continue;
            if (normalizeAliasKey(entry.substring(0, separator)).equals(normalized)) {
                String alias = entry.substring(separator + 1).strip();
                return alias.isEmpty() ? null : alias;
            }
        }
        return null;
    }

    public static synchronized boolean setUploadAlias(String sourceKey, String alias) {
        String normalized = normalizeAliasKey(sourceKey);
        String cleanAlias = alias == null ? "" : alias.strip();
        if (normalized.isEmpty() || cleanAlias.isEmpty() || cleanAlias.length() > 256) return false;

        var updated = new ArrayList<String>();
        for (var entry : TIANSHU_UPLOAD_ALIASES.get()) {
            int separator = entry.indexOf('=');
            if (separator > 0
                    && normalizeAliasKey(entry.substring(0, separator)).equals(normalized)) continue;
            updated.add(entry);
        }
        updated.add(normalized + "=" + cleanAlias);
        TIANSHU_UPLOAD_ALIASES.set(List.copyOf(updated));
        if (SPEC.isLoaded()) SPEC.save();
        return true;
    }

    public static synchronized int removeUploadAliases(String alias) {
        String target = alias == null ? "" : alias.strip();
        if (target.isEmpty()) return 0;
        var updated = new ArrayList<String>();
        int removed = 0;
        for (var entry : TIANSHU_UPLOAD_ALIASES.get()) {
            int separator = entry.indexOf('=');
            String storedAlias = separator < 0 ? "" : entry.substring(separator + 1).strip();
            if (storedAlias.equalsIgnoreCase(target)) {
                removed++;
            } else {
                updated.add(entry);
            }
        }
        if (removed > 0) {
            TIANSHU_UPLOAD_ALIASES.set(List.copyOf(updated));
            if (SPEC.isLoaded()) SPEC.save();
        }
        return removed;
    }

    private static String normalizeAliasKey(String sourceKey) {
        return sourceKey == null ? "" : sourceKey.strip().toLowerCase(Locale.ROOT);
    }
}
