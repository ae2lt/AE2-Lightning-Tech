package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuUploadTargetData;
import java.util.Locale;
import java.util.function.BiPredicate;

/** Matching rules for recipe aliases against Tianshu pattern-provider groups. */
final class TianshuUploadTargetMatcher {
    private TianshuUploadTargetMatcher() {
    }

    static boolean matches(TianshuUploadTargetData target, String query) {
        if (target == null || query == null || query.isBlank()) return true;
        String normalizedQuery = normalize(query.strip());
        var group = target.group();
        if (group.icon() != null) {
            if (idMatches(group.icon().getId().toString(), normalizedQuery)) return true;
            if (nameMatches(group.icon().getDisplayName().getString(), normalizedQuery)) return true;
        }
        if (nameMatches(group.name().getString(), normalizedQuery)) return true;
        for (var line : group.tooltip()) {
            if (nameMatches(line.getString(), normalizedQuery)) return true;
        }
        return false;
    }

    /** Machine IDs use a left-anchored glob and may omit an arbitrary suffix. */
    static boolean idMatches(String machineId, String query) {
        if (machineId == null || query == null || query.isBlank()) return false;
        return globMatches(normalize(machineId), normalize(query) + "*");
    }

    /** Machine names use case-insensitive contains matching, with optional glob and pinyin. */
    static boolean nameMatches(String machineName, String query) {
        return nameMatches(machineName, query, JecSearchCompat::contains);
    }

    static boolean nameMatches(
            String machineName, String query, BiPredicate<String, String> pinyinContains) {
        if (machineName == null || query == null || query.isBlank()) return false;
        String normalizedName = normalize(machineName);
        String normalizedQuery = normalize(query);
        if (containsWildcard(normalizedQuery)
                ? globMatches(normalizedName, "*" + normalizedQuery + "*")
                : normalizedName.contains(normalizedQuery)) {
            return true;
        }
        if (!containsWildcard(normalizedQuery)) {
            return pinyinContains.test(normalizedName, normalizedQuery);
        }
        // JEC exposes contains rather than match positions. For a wildcard query, require every
        // fixed fragment to match the name's pinyin representation; '*'/'?' remain separators.
        boolean hasFragment = false;
        for (String fragment : normalizedQuery.split("[?*]+")) {
            if (fragment.isBlank()) continue;
            hasFragment = true;
            if (!pinyinContains.test(normalizedName, fragment)) return false;
        }
        return hasFragment;
    }

    /** Uses a custom machine name when present; otherwise the stable registry ID is preferred. */
    static String preferredAlias(String machineId, String defaultName, String currentName) {
        String id = machineId == null ? "" : machineId.strip();
        String defaultLabel = defaultName == null ? "" : defaultName.strip();
        String currentLabel = currentName == null ? "" : currentName.strip();
        if (currentLabel.isEmpty() || currentLabel.equals(defaultLabel)) return id;
        return currentLabel;
    }

    /** Linear-time wildcard matching where '*' spans any sequence and '?' spans one character. */
    static boolean globMatches(String value, String pattern) {
        if (value == null || pattern == null) return false;
        int valueIndex = 0;
        int patternIndex = 0;
        int starIndex = -1;
        int starValueIndex = -1;
        while (valueIndex < value.length()) {
            if (patternIndex < pattern.length()
                    && (pattern.charAt(patternIndex) == '?'
                    || pattern.charAt(patternIndex) == value.charAt(valueIndex))) {
                valueIndex++;
                patternIndex++;
            } else if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
                starIndex = patternIndex++;
                starValueIndex = valueIndex;
            } else if (starIndex >= 0) {
                patternIndex = starIndex + 1;
                valueIndex = ++starValueIndex;
            } else {
                return false;
            }
        }
        while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == pattern.length();
    }

    private static boolean containsWildcard(String value) {
        return value.indexOf('*') >= 0 || value.indexOf('?') >= 0;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
