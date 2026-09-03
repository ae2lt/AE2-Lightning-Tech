package com.moakiee.ae2lt.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class GuideMetadataParityTest {
    private static final Path GUIDE = Path.of(
            "src", "main", "resources", "assets", "ae2lt", "ae2guide");
    private static final Path CHINESE_GUIDE = GUIDE.resolve("_zh_cn");
    private static final Pattern FRONTMATTER = Pattern.compile("(?s)^---\\s*\\R(.*?)\\R---");
    private static final Pattern ITEM_IDS = Pattern.compile(
            "(?m)^item_ids:[ \\t]*\\R((?:[ \\t]*-[ \\t]+[^\\r\\n]+\\R?)*)");
    private static final Pattern ITEM_IDS_DECLARATION = Pattern.compile("(?m)^item_ids[ \\t]*:");
    private static final Pattern ITEM_ID = Pattern.compile(
            "\\s*-\\s+([a-z0-9_.-]+:[a-z0-9_./-]+)\\s*");
    private static final Pattern ITEM_LINK = Pattern.compile(
            "<ItemLink\\s+[^>]*id=\\\"(ae2lt:[a-z0-9_./-]+)\\\"");
    private static final Pattern NAMESPACED_HELP_TOPIC = Pattern.compile(
            "\\\"helpTopic\\\"\\s*:\\s*\\\"[a-z0-9_.-]+:");

    @Test
    void translatedPagesPreserveItemIndexMetadata() throws IOException {
        List<String> mismatches = new ArrayList<>();

        for (Path english : guidePages(GUIDE)) {
            if (english.startsWith(CHINESE_GUIDE)) {
                continue;
            }
            Path relative = GUIDE.relativize(english);
            Path chinese = CHINESE_GUIDE.resolve(relative);
            if (Files.isRegularFile(chinese)) {
                Set<String> expected = itemIds(english);
                Set<String> actual = itemIds(chinese);
                if (!expected.equals(actual)) {
                    mismatches.add(relative + ": expected " + expected + ", got " + actual);
                }
            }
        }

        assertTrue(mismatches.isEmpty(), String.join(System.lineSeparator(), mismatches));
    }

    @Test
    void everyItemLinkHasExactlyOneOwnerInEachLanguage() throws IOException {
        assertItemLinkOwners("en_us", guidePages(GUIDE).stream()
                .filter(path -> !path.startsWith(CHINESE_GUIDE))
                .toList());
        assertItemLinkOwners("zh_cn", guidePages(CHINESE_GUIDE));
    }

    @Test
    void globalItemIndexOnlyClaimsAe2ltItems() throws IOException {
        List<String> invalid = new ArrayList<>();

        for (Path page : guidePages(GUIDE)) {
            for (String itemId : itemIds(page)) {
                if (!itemId.startsWith("ae2lt:")) {
                    invalid.add(GUIDE.relativize(page) + ": " + itemId);
                }
            }
        }

        assertEquals(List.of(), invalid,
                "Foreign items belong in ItemLink content, not GuideME item_ids ownership");
    }

    @Test
    void screenStylesDoNotUseNamespacedHelpTopics() throws IOException {
        Path screens = Path.of("src", "main", "resources", "assets", "ae2lt", "screens");
        List<Path> invalid = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(screens)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".json"))
                    .toList()) {
                if (NAMESPACED_HELP_TOPIC.matcher(Files.readString(path)).find()) {
                    invalid.add(path);
                }
            }
        }

        assertTrue(invalid.isEmpty(),
                "AE2 15.4.10 treats namespaced helpTopic values as ae2 paths: " + invalid);
    }

    private static void assertItemLinkOwners(String language, List<Path> pages) throws IOException {
        Map<String, List<Path>> owners = new HashMap<>();
        Set<String> links = new HashSet<>();

        for (Path page : pages) {
            for (String itemId : itemIds(page)) {
                owners.computeIfAbsent(itemId, ignored -> new ArrayList<>()).add(page);
            }
            Matcher linksInPage = ITEM_LINK.matcher(Files.readString(page));
            while (linksInPage.find()) {
                links.add(linksInPage.group(1));
            }
        }

        List<String> failures = new ArrayList<>();
        for (String itemId : links.stream().sorted().toList()) {
            List<Path> itemOwners = owners.getOrDefault(itemId, List.of());
            if (itemOwners.size() != 1) {
                failures.add(itemId + " has " + itemOwners.size() + " owners: " + itemOwners);
            }
        }
        for (var entry : owners.entrySet()) {
            if (entry.getValue().size() > 1) {
                failures.add(entry.getKey() + " has duplicate owners: " + entry.getValue());
            }
        }

        assertEquals(List.of(), failures, language + " GuideME item index is inconsistent");
    }

    private static Set<String> itemIds(Path page) throws IOException {
        Matcher frontmatter = FRONTMATTER.matcher(Files.readString(page));
        if (!frontmatter.find()) {
            return Set.of();
        }

        String metadata = frontmatter.group(1);
        Matcher itemIdsBlock = ITEM_IDS.matcher(metadata);
        if (!itemIdsBlock.find()) {
            if (ITEM_IDS_DECLARATION.matcher(metadata).find()) {
                throw new AssertionError(page + " must use a non-empty item_ids block list");
            }
            return Set.of();
        }

        Set<String> result = new HashSet<>();
        for (String line : itemIdsBlock.group(1).split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            Matcher itemId = ITEM_ID.matcher(line);
            if (!itemId.matches()) {
                throw new AssertionError(page + " has a non-canonical item_ids entry: " + line.trim());
            }
            if (!result.add(itemId.group(1))) {
                throw new AssertionError(page + " repeats item_ids entry " + itemId.group(1));
            }
        }
        if (result.isEmpty()) {
            throw new AssertionError(page + " has an empty item_ids block");
        }
        return result;
    }

    private static List<Path> guidePages(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .toList();
        }
    }
}
