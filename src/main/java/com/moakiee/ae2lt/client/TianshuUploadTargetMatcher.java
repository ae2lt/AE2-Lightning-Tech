package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuUploadTargetData;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Matching rules for recipe aliases against Tianshu pattern-provider groups. */
final class TianshuUploadTargetMatcher {
    private TianshuUploadTargetMatcher() {
    }

    static boolean matches(TianshuUploadTargetData target, String query) {
        if (target == null || query == null || query.isBlank()) return true;
        String normalizedQuery = normalize(query.strip());
        var group = target.group();
        if (group.icon() != null
                && idMatches(group.icon().getId().toString(), normalizedQuery)) {
            return true;
        }
        return nameMatches(group.name().getString(), normalizedQuery);
    }

    /**
     * Returns the only candidate produced by the visible picker's filter when it is writable.
     * A full candidate still counts toward ambiguity, so direct upload never silently skips a
     * visible target in favor of another one.
     */
    static TianshuUploadTargetData findUniqueCandidate(
            List<TianshuUploadTargetData> targets, String query) {
        return findUniqueCandidate(
                targets,
                target -> matches(target, query),
                target -> target.availableSlots() > 0);
    }

    static <T> T findUniqueCandidate(
            List<T> candidates, Predicate<T> matches, Predicate<T> writable) {
        if (candidates == null) return null;
        T selected = null;
        for (var target : candidates) {
            if (!matches.test(target)) continue;
            if (selected != null) return null;
            selected = target;
        }
        return selected != null && writable.test(selected) ? selected : null;
    }

    static String findClosestUniqueAlias(
            List<TianshuUploadTargetData> targets,
            String preferredAlias,
            List<String> defaultAliases) {
        return findClosestUniqueAlias(
                targets, preferredAlias, defaultAliases, TianshuUploadTargetMatcher::matches);
    }

    /**
     * Selects the alias whose positive match count is closest to one. Ties retain display order,
     * and no matches retain the original alias. Callers suppress this selection entirely when the
     * original alias was saved.
     */
    static <T> String findClosestUniqueAlias(
            List<T> targets,
            String preferredAlias,
            List<String> defaultAliases,
            BiPredicate<T, String> matches) {
        String fallback = preferredAlias == null ? "" : preferredAlias;
        String selected = fallback;
        int selectedMatches = countMatches(targets, fallback, matches);
        if (selectedMatches == 1) return selected;
        if (defaultAliases != null) {
            for (String alias : defaultAliases) {
                if (alias == null || alias.isBlank() || alias.equalsIgnoreCase(fallback)) continue;
                int aliasMatches = countMatches(targets, alias, matches);
                if (aliasMatches <= 0
                        || (selectedMatches > 0 && aliasMatches >= selectedMatches)) {
                    continue;
                }
                selected = alias;
                selectedMatches = aliasMatches;
                if (selectedMatches == 1) return selected;
            }
        }
        return selectedMatches > 0 ? selected : fallback;
    }

    private static <T> int countMatches(
            List<T> targets, String alias, BiPredicate<T, String> matches) {
        if (targets == null || alias == null || alias.isBlank() || matches == null) return 0;
        int count = 0;
        for (var target : targets) {
            if (matches.test(target, alias)) count++;
        }
        return count;
    }

    /** Icon IDs use a whole-value glob. A partial ID therefore needs an explicit wildcard. */
    static boolean idMatches(String machineId, String query) {
        if (machineId == null || query == null || query.isBlank()) return false;
        return globMatches(normalize(machineId), normalize(query));
    }

    /**
     * Machine names use one-way, case-insensitive contains matching: the name must contain the
     * alias expression. The alias may contain glob characters or pinyin fragments.
     */
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
            return isPinyinFragment(normalizedQuery)
                    && pinyinContains.test(normalizedName, normalizedQuery);
        }
        // JEC exposes contains rather than match positions. For a wildcard query, require every
        // fixed fragment to match the name's pinyin representation; '*'/'?' remain separators.
        boolean hasFragment = false;
        for (String fragment : normalizedQuery.split("[?*]+")) {
            if (fragment.isBlank()) continue;
            // PinIn ignores punctuation, underscores and digits in a query. Passing a registry
            // ID such as "ae2lt:overload_processing_factory" can therefore degenerate into an
            // empty pinyin search that matches every Chinese provider name.
            if (!isPinyinFragment(fragment)) return false;
            hasFragment = true;
            if (!pinyinContains.test(normalizedName, fragment)) return false;
        }
        return hasFragment;
    }

    private static boolean isPinyinFragment(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 'a' || character > 'z') return false;
        }
        return true;
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
