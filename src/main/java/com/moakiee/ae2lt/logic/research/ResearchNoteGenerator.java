package com.moakiee.ae2lt.logic.research;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;

/**
 * Builds a nine-item late-game ritual from three fixed, ordered AE2LT materials and six weighted,
 * no-replacement draws from the original cross-mod pool. Only the six random materials are
 * shuffled, so the fixed opening remains stable while every note still has its own random tail.
 */
public final class ResearchNoteGenerator {
    private static final int RANDOM_ITEM_COUNT = 6;
    private static final long SALT_RESEARCH_NOTE = 0x52A8D3C1B7E4A19DL;

    public static final List<ResourceLocation> FIXED_RECIPE_ITEMS = List.of(
            item("pigmee_core"),
            item("module_undying"),
            item("module_phase_lock"));

    private static final List<Candidate> RANDOM_CANDIDATES = List.of(
            candidate("avaritia", "infinity_ingot", Tier.SSS, 100),
            candidate("mekanism_extras", "qio_drive_singularity", Tier.SSS, 99),
            candidate("modern_industrialization", "quantum_upgrade", Tier.SSS, 98),
            candidate("bigreactors", "inanite_block", Tier.SS, 95),
            candidate("draconicevolution", "chaotic_core", Tier.SS, 93),
            candidate("occultism", "celestial_chalice", Tier.SS, 91),
            candidate("appflux", "core_256m", Tier.S, 90),
            candidate("advanced_ae", "data_entangler", Tier.S, 89),
            candidate("advanced_ae", "quantum_multi_threader", Tier.S, 87),
            candidate("megacells", "cell_component_256m", Tier.S, 86),
            candidate("ae2omnicells", "quantum_omni_cell_component_256m", Tier.S, 85),
            candidate("mekanism_extras", "infinite_induction_cell", Tier.S, 81),
            candidate("mekanism_extras", "infinite_induction_provider", Tier.S, 81),
            candidate("mekanism", "pellet_antimatter", Tier.A, 84),
            candidate("mekanism_extras", "infinite_control_circuit", Tier.A, 80),
            candidate("ars_nouveau", "wilden_tribute", Tier.A, 68),
            candidate("minecraft", "elytra", Tier.A, 54),
            candidate("minecraft", "heavy_core", Tier.A, 52),
            candidate("minecraft", "dragon_head", Tier.A, 46),
            candidate("ae2", "256k_crafting_storage", Tier.B, 49),
            candidate("mekanism", "ultimate_induction_cell", Tier.B, 38),
            candidate("mekanism", "ultimate_induction_provider", Tier.B, 38),
            candidate("pneumaticcraft", "micromissiles", Tier.B, 36),
            candidate("minecraft", "dragon_egg", Tier.B, 35),
            candidate("minecraft", "heart_of_the_sea", Tier.B, 28),
            candidate("minecraft", "echo_shard", Tier.B, 26),
            candidate("minecraft", "nether_star", Tier.B, 24),
            candidate("minecraft", "torchflower", Tier.C, 16),
            candidate("minecraft", "recovery_compass", Tier.C, 15),
            candidate("minecraft", "slime_ball", Tier.C, 12));

    private ResearchNoteGenerator() {
    }

    public static boolean hasValidPool() {
        return RANDOM_CANDIDATES.stream().filter(ResearchNoteGenerator::isAvailable).count() >= RANDOM_ITEM_COUNT;
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
        List<Candidate> pool = RANDOM_CANDIDATES.stream()
                .filter(ResearchNoteGenerator::isAvailable)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<Candidate> picked = new ArrayList<>(count);
        while (picked.size() < count && !pool.isEmpty()) {
            int totalWeight = pool.stream().mapToInt(Candidate::effectiveWeight).sum();
            int roll = random.nextInt(totalWeight);
            int cursor = 0;
            for (int i = 0; i < pool.size(); i++) {
                Candidate candidate = pool.get(i);
                cursor += candidate.effectiveWeight();
                if (roll < cursor) {
                    picked.add(candidate);
                    pool.remove(i);
                    break;
                }
            }
        }
        return picked;
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
        return ResourceLocation.fromNamespaceAndPath("ae2lt", path);
    }

    private static Candidate candidate(String namespace, String path, Tier tier, int baseWeight) {
        return new Candidate(ResourceLocation.fromNamespaceAndPath(namespace, path), tier, baseWeight);
    }

    private record SelectedItem(ResourceLocation id, String descriptionKey) {
    }

    private record Candidate(ResourceLocation id, Tier tier, int baseWeight) {
        private int effectiveWeight() {
            int weight = baseWeight;
            if ("minecraft".equals(id.getNamespace())) {
                weight -= 8;
            }
            if (tier.isHighTier() && !"minecraft".equals(id.getNamespace())) {
                weight += 2;
            }
            return Mth.clamp(weight, 1, 100) * tier.multiplier;
        }

        private String pickDescriptionKey(RandomSource random) {
            return "ae2lt.research_note.desc." + id.getNamespace() + "."
                    + id.getPath().replace('/', '.') + "." + random.nextInt(2);
        }
    }

    private enum Tier {
        SSS(12),
        SS(8),
        S(5),
        A(3),
        B(2),
        C(1);

        private final int multiplier;

        Tier(int multiplier) {
            this.multiplier = multiplier;
        }

        private boolean isHighTier() {
            return this == SSS || this == SS || this == S;
        }
    }
}
