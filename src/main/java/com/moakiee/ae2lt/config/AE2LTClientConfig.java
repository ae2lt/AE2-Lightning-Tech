package com.moakiee.ae2lt.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class AE2LTClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<TianshuUploadTrigger> TIANSHU_UPLOAD_TRIGGER;
    private static final ModConfigSpec.BooleanValue TIANSHU_INTERCEPT_DUPLICATE_ENCODING;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> TIANSHU_UPLOAD_ALIASES;
    private static final ModConfigSpec.BooleanValue DISABLE_VEIL_RENDERING;
    private static final ModConfigSpec.BooleanValue RENDER_MULTIBLOCK_CORE_EFFECTS;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("tianshuTerminal");
        TIANSHU_UPLOAD_TRIGGER = builder
                .comment("Modifier condition that starts pattern upload after encoding")
                .defineEnum("uploadTrigger", TianshuUploadTrigger.NO_SHIFT);
        TIANSHU_INTERCEPT_DUPLICATE_ENCODING = builder
                .comment("Cancel encoding when the same pattern already exists on the ME network")
                .define("interceptDuplicatePatternEncoding", true);
        TIANSHU_UPLOAD_ALIASES = builder
                .comment("Recipe type/category id to pattern-provider alias mappings (source=alias)")
                .defineListAllowEmpty("uploadAliases", List.of(),
                        value -> value instanceof String text
                                && text.length() <= 512
                                && text.indexOf('=') > 0);
        builder.pop();
        builder.push("compatibility");
        DISABLE_VEIL_RENDERING = builder
                .comment(
                        "Compatibility switch: disable all Veil-backed rendering provided by AE2 Lightning Tech.",
                        "Enable only when a graphics driver or another rendering mod is incompatible with Veil.")
                .define("disableVeilRendering", false);
        builder.pop();
        builder.push("rendering");
        RENDER_MULTIBLOCK_CORE_EFFECTS = builder
                .comment("Render formed Tianshu and matter-warping core effects")
                .define("multiblockCoreEffects", true);
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

    public static boolean interceptDuplicatePatternEncoding() {
        return TIANSHU_INTERCEPT_DUPLICATE_ENCODING.get();
    }

    public static boolean renderMultiblockCoreEffects() {
        return RENDER_MULTIBLOCK_CORE_EFFECTS.get();
    }

    public static boolean useVeilRendering() {
        return !DISABLE_VEIL_RENDERING.get();
    }

    public static void setInterceptDuplicatePatternEncoding(boolean enabled) {
        TIANSHU_INTERCEPT_DUPLICATE_ENCODING.set(enabled);
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
