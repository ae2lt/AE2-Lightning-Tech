package com.moakiee.ae2lt.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AE2LTClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<TianshuUploadTrigger> TIANSHU_UPLOAD_TRIGGER;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("tianshuTerminal");
        TIANSHU_UPLOAD_TRIGGER = builder
                .comment("Modifier condition that starts pattern upload after encoding")
                .defineEnum("uploadTrigger", TianshuUploadTrigger.NO_SHIFT);
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
}
