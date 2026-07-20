package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TianshuUploadTargetMatcherTest {
    @Test
    void machineIdIsExactUnlessTheQueryContainsWildcards() {
        assertTrue(TianshuUploadTargetMatcher.idMatches(
                "extendedae:ex_pattern_provider", "extendedae:ex_pattern_provider"));
        assertTrue(TianshuUploadTargetMatcher.idMatches(
                "extendedae:ex_pattern_provider", "extendedae:*_provider"));
        assertTrue(TianshuUploadTargetMatcher.idMatches(
                "EXTENDEDAE:EX_PATTERN_PROVIDER", "extendedae:ex_pattern_????ider"));

        assertFalse(TianshuUploadTargetMatcher.idMatches(
                "extendedae:ex_pattern_provider", "pattern_provider"));
        assertFalse(TianshuUploadTargetMatcher.idMatches(
                "extendedae:ex_pattern_provider", "extendedae:ex_pattern"));
        assertFalse(TianshuUploadTargetMatcher.idMatches(
                "extendedae:ex_pattern_provider", "*:provider"));
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
}
