package com.moakiee.ae2lt.config;

import java.nio.file.Files;
import java.nio.file.Path;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.neoforged.fml.loading.FMLPaths;

public final class AE2LTConfigMigration {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String CONFIG_FILE_NAME = "ae2lt-common.toml";
    private static final String VERSION_KEY = "configVersion";

    private static boolean migrationOccurred;

    private AE2LTConfigMigration() {
    }

    public static void runIfNeeded() {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);
        if (!Files.exists(configFile)) {
            return;
        }
        int version = readVersion(configFile);
        if (version >= AE2LTCommonConfig.CURRENT_CONFIG_VERSION) {
            return;
        }
        try {
            Files.delete(configFile);
            migrationOccurred = true;
            LOG.info("[ae2lt] detected legacy {} (version {} < {}); deleted, defaults will be regenerated.",
                    CONFIG_FILE_NAME, version, AE2LTCommonConfig.CURRENT_CONFIG_VERSION);
        } catch (Exception e) {
            LOG.warn("[ae2lt] failed to delete legacy config {}: {}", CONFIG_FILE_NAME, e.toString());
        }
    }

    public static boolean migrationOccurred() {
        return migrationOccurred;
    }

    private static int readVersion(Path file) {
        try (CommentedFileConfig raw = CommentedFileConfig.builder(file)
                .sync()
                .preserveInsertionOrder()
                .build()) {
            raw.load();
            Object value = raw.get(VERSION_KEY);
            if (value instanceof Number n) {
                return n.intValue();
            }
        } catch (Exception e) {
            LOG.warn("[ae2lt] failed to read {} during migration probe: {}", CONFIG_FILE_NAME, e.toString());
        }
        return 1;
    }
}
