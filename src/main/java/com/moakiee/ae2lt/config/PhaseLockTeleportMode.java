package com.moakiee.ae2lt.config;

/** Server policy for the phase-lock module's external-teleport protection. */
public enum PhaseLockTeleportMode {
    /** Ignore every external teleport, effectively disabling teleport protection. */
    IGNORE_ALL("ignore-all"),
    /** Ignore player-self commands and privileged management commands. */
    IGNORE_COMMAND("ignore-command"),
    /** Ignore only player-self commands; block every external teleport source. */
    IGNORE_NONE("ignore-none");

    private final String configValue;

    PhaseLockTeleportMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public boolean disablesProtection() {
        return this == IGNORE_ALL;
    }

    public boolean ignoresPrivilegedCommands() {
        return this == IGNORE_COMMAND;
    }

    public static PhaseLockTeleportMode fromConfigValue(String value) {
        for (var mode : values()) {
            if (mode.configValue.equals(value)) {
                return mode;
            }
        }
        return IGNORE_COMMAND;
    }

    public static boolean isValidConfigValue(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        for (var mode : values()) {
            if (mode.configValue.equals(text)) {
                return true;
            }
        }
        return false;
    }
}
