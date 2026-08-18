package com.moakiee.ae2lt.logic.research;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.moakiee.ae2lt.config.AE2LTCommonConfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;

/**
 * Builds a nine-item late-game ritual from three fixed, ordered AE2LT materials and six weighted,
 * no-replacement draws from the configured cross-mod pool. Only the six random materials are
 * shuffled, so the fixed opening remains stable while every note still has its own random tail.
 */
public final class ResearchNoteGenerator {
    private static final int RANDOM_ITEM_COUNT = 6;
    private static final long SALT_RESEARCH_NOTE = 0x52A8D3C1B7E4A19DL;

    public static final List<ResourceLocation> FIXED_RECIPE_ITEMS = List.of(
            item("pigmee_core"),
            item("module_undying"),
            item("module_phase_lock"));

    private ResearchNoteGenerator() {
    }

    public static boolean hasValidPool() {
        return configuredAvailableCandidates().size() >= RANDOM_ITEM_COUNT;
    }

    public static ResearchNoteData generate(ServerLevel level) {
        UUID ritualSeed = UUID.randomUUID();
        RandomSource random = RandomSource.create(mixSeed(ritualSeed, level.getServer().overworld().getSeed()));

        List<SelectedItem> selected = new ArrayList<>(9);
        for (ResourceLocation fixed : FIXED_RECIPE_ITEMS) {
            selected.add(new SelectedItem(fixed, itemTranslationKey(fixed)));
        }

        List<SelectedItem> randomSelected = new ArrayList<>(RANDOM_ITEM_COUNT);
        for (Candidate candidate : weightedPickAvailable(RANDOM_ITEM_COUNT, random)) {
            randomSelected.add(new SelectedItem(candidate.id(), candidate.pickDescriptionKey(random)));
        }
        if (randomSelected.size() != RANDOM_ITEM_COUNT) {
            throw new IllegalStateException("Research note random pool has fewer than six available entries.");
        }

        shuffle(randomSelected, random);
        selected.addAll(randomSelected);
        return new ResearchNoteData(
                ritualSeed,
                RitualGoal.HYPERDIMENSIONAL_PIGMEE,
                selected.stream().map(SelectedItem::id).toList(),
                selected.stream().map(SelectedItem::descriptionKey).toList(),
                false);
    }

    private static List<Candidate> weightedPickAvailable(int count, RandomSource random) {
        List<Candidate> pool = new ArrayList<>(configuredAvailableCandidates());
        List<Candidate> picked = new ArrayList<>(count);
        while (picked.size() < count && !pool.isEmpty()) {
            long totalWeight = pool.stream().mapToLong(Candidate::weight).sum();
            long roll = Math.floorMod(random.nextLong(), totalWeight);
            long cursor = 0L;
            for (int i = 0; i < pool.size(); i++) {
                Candidate candidate = pool.get(i);
                cursor += candidate.weight();
                if (roll < cursor) {
                    picked.add(candidate);
                    pool.remove(i);
                    break;
                }
            }
        }
        return picked;
    }

    private static List<Candidate> configuredAvailableCandidates() {
        return AE2LTCommonConfig.easterEggWeights().entrySet().stream()
                .map(entry -> new Candidate(entry.getKey(), entry.getValue()))
                .filter(ResearchNoteGenerator::isAvailable)
                .toList();
    }

    private static boolean isAvailable(Candidate candidate) {
        return BuiltInRegistries.ITEM.getOptional(candidate.id())
                .filter(item -> item != Items.AIR)
                .isPresent();
    }

    private static void shuffle(List<SelectedItem> selected, RandomSource random) {
        for (int i = selected.size() - 1; i > 0; i--) {
            int other = random.nextInt(i + 1);
            SelectedItem temporary = selected.get(i);
            selected.set(i, selected.get(other));
            selected.set(other, temporary);
        }
    }

    private static String itemTranslationKey(ResourceLocation id) {
        return "item." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    private static long mixSeed(UUID ritualSeed, long worldSeed) {
        return ritualSeed.getMostSignificantBits()
                ^ ritualSeed.getLeastSignificantBits()
                ^ Long.rotateLeft(worldSeed, 17)
                ^ SALT_RESEARCH_NOTE;
    }

    private static ResourceLocation item(String path) {
        return new ResourceLocation("ae2lt", path);
    }

    private record SelectedItem(ResourceLocation id, String descriptionKey) {
    }

    private record Candidate(ResourceLocation id, int weight) {
        private String pickDescriptionKey(RandomSource random) {
            if (!AE2LTCommonConfig.isDefaultEasterEggCandidate(id)) {
                return itemTranslationKey(id);
            }
            return "ae2lt.research_note.desc." + id.getNamespace() + "."
                    + id.getPath().replace('/', '.') + "." + random.nextInt(2);
        }
    }
}
