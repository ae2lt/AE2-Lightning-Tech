package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TianshuUploadTargetMatcherTest {
    @Test
    void iconIdUsesWholeValueGlobMatching() {
        assertTrue(TianshuUploadTargetMatcher.idMatches(
                "extendedae:ex_pattern_provider", "extendedae:ex_pattern_provider"));
        assertTrue(TianshuUploadTargetMatcher.idMatches(
                "extendedae:ex_pattern_provider", "extendedae:*_provider"));
        assertTrue(TianshuUploadTargetMatcher.idMatches(
                "EXTENDEDAE:EX_PATTERN_PROVIDER", "extendedae:ex_pattern_????ider"));
        assertTrue(TianshuUploadTargetMatcher.idMatches(
                "extendedae:ex_pattern_provider", "*:ex_pattern_provider"));

        assertFalse(TianshuUploadTargetMatcher.idMatches(
                "extendedae:ex_pattern_provider", "pattern_provider"));
        assertFalse(TianshuUploadTargetMatcher.idMatches(
                "extendedae:ex_pattern_provider", "extendedae:pattern"));
        assertFalse(TianshuUploadTargetMatcher.idMatches(
                "extendedae:ex_pattern_provider", "extendedae:ex_pattern"));
    }

    @Test
    void machineNameUsesContainsAndSubstringGlobMatching() {
        assertTrue(TianshuUploadTargetMatcher.nameMatches(
                "Advanced Pattern Provider", "pattern pro", (name, query) -> false));
        assertTrue(TianshuUploadTargetMatcher.nameMatches(
                "Advanced Pattern Provider", "adv*provider", (name, query) -> false));
        assertTrue(TianshuUploadTargetMatcher.nameMatches(
                "Advanced Pattern Provider", "pattern ?rovider", (name, query) -> false));
        assertFalse(TianshuUploadTargetMatcher.nameMatches(
                "Advanced Pattern Provider", "basic*provider", (name, query) -> false));
    }

    @Test
    void pinyinSearchWorksForPlainAndWildcardAliases() {
        Set<String> pinyinFragments = Set.of("gaoji", "yangban", "gongyingqi");
        var fakePinyin = (java.util.function.BiPredicate<String, String>)
                (name, query) -> pinyinFragments.contains(query);

        assertTrue(TianshuUploadTargetMatcher.nameMatches("高级样板供应器", "gaoji", fakePinyin));
        assertTrue(TianshuUploadTargetMatcher.nameMatches(
                "高级样板供应器", "gaoji*gongyingqi", fakePinyin));
        assertFalse(TianshuUploadTargetMatcher.nameMatches(
                "高级样板供应器", "gaoji*machine", fakePinyin));
    }

    @Test
    void registryIdsNeverFallBackToPinyinMatching() {
        var overlyPermissivePinyin = (java.util.function.BiPredicate<String, String>)
                (name, query) -> true;

        assertFalse(TianshuUploadTargetMatcher.nameMatches(
                "净化无限粉碎工厂",
                "ae2lt:overload_processing_factory",
                overlyPermissivePinyin));
        assertFalse(TianshuUploadTargetMatcher.nameMatches(
                "精英富集工厂", "ae2lt*", overlyPermissivePinyin));
    }

    @Test
    void globMatcherHandlesEmptyAndBacktrackingEdges() {
        assertTrue(TianshuUploadTargetMatcher.globMatches("", "*"));
        assertTrue(TianshuUploadTargetMatcher.globMatches("abc", "a**?c"));
        assertFalse(TianshuUploadTargetMatcher.globMatches("abc", "a*d"));
        assertFalse(TianshuUploadTargetMatcher.globMatches("", "?"));
    }

    @Test
    void quickBindingUsesTheIdForDefaultNamesAndTheCustomNameForRenamedMachines() {
        assertEquals("extendedae:ex_pattern_provider", TianshuUploadTargetMatcher.preferredAlias(
                "extendedae:ex_pattern_provider",
                "Extended Pattern Provider",
                "Extended Pattern Provider"));
        assertEquals("Ore Processing", TianshuUploadTargetMatcher.preferredAlias(
                "extendedae:ex_pattern_provider",
                "Extended Pattern Provider",
                "Ore Processing"));
        assertEquals("Remote Provider", TianshuUploadTargetMatcher.preferredAlias(
                "", "", "Remote Provider"));
    }

    @Test
    void directUploadUsesTheSameUniqueCandidateSetAsTheVisibleFilter() {
        var provider = target("Advanced Provider", 4);
        var unrelated = target("Basic Provider", 4);

        assertSame(provider, TianshuUploadTargetMatcher.findUniqueCandidate(
                List.of(provider, unrelated),
                target -> target.name().contains("Advanced"),
                target -> target.availableSlots() > 0));
        assertSame(provider, TianshuUploadTargetMatcher.findUniqueCandidate(
                List.of(provider),
                target -> true,
                target -> target.availableSlots() > 0));
        assertNull(TianshuUploadTargetMatcher.findUniqueCandidate(
                List.of(provider, unrelated),
                target -> target.name().contains("Provider"),
                target -> target.availableSlots() > 0));
    }

    @Test
    void fullTargetsStillCountAsCandidatesButCannotReceiveDirectUploads() {
        var writable = target("Advanced Provider", 4);
        var full = target("Advanced Provider Backup", 0);

        assertNull(TianshuUploadTargetMatcher.findUniqueCandidate(
                List.of(writable, full),
                target -> target.name().contains("Advanced"),
                target -> target.availableSlots() > 0));
        assertNull(TianshuUploadTargetMatcher.findUniqueCandidate(
                List.of(full),
                target -> target.name().contains("Advanced"),
                target -> target.availableSlots() > 0));
    }

    @Test
    void initialAliasSelectionPrefersThePositiveMatchCountClosestToOne() {
        var providers = List.of("mod:a", "mod:b", "type:a", "machine");
        var matches = (java.util.function.BiPredicate<String, String>)
                (target, alias) -> target.startsWith(alias);

        assertEquals("type:", TianshuUploadTargetMatcher.findClosestUniqueAlias(
                providers,
                "mod:",
                List.of("type:", "machine"),
                matches));
        assertEquals("type:", TianshuUploadTargetMatcher.findClosestUniqueAlias(
                List.of("mod:a", "mod:b", "mod:c", "type:a", "type:b"),
                "mod:",
                List.of("missing", "type:"),
                matches));
        assertEquals("mod:", TianshuUploadTargetMatcher.findClosestUniqueAlias(
                List.of("mod:a", "mod:b"),
                "mod:",
                List.of("missing"),
                matches));
        assertEquals("saved", TianshuUploadTargetMatcher.findClosestUniqueAlias(
                List.of(),
                "saved",
                List.of("missing"),
                matches));
    }

    private static Candidate target(String name, int availableSlots) {
        return new Candidate(name, availableSlots);
    }

    private record Candidate(String name, int availableSlots) {
    }

}
