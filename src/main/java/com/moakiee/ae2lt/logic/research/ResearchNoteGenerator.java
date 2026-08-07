package com.moakiee.ae2lt.logic.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;

public final class ResearchNoteGenerator {
    private static final Logger LOG = LogUtils.getLogger();
    private static final long SALT_RESEARCH_NOTE = 0x52A8D3C1B7E4A19DL;

    private static final List<Candidate> CONFIGURED_CANDIDATES = List.of(
            new Candidate(id("avaritia", "infinity_ingot"), Tier.SSS, 100, 2),
            new Candidate(id("mekanism_extras", "qio_drive_singularity"), Tier.SSS, 99, 2),
            new Candidate(id("modern_industrialization", "quantum_upgrade"), Tier.SSS, 98, 2),
            new Candidate(id("bigreactors", "inanite_block"), Tier.SS, 95, 2),
            new Candidate(id("draconicevolution", "chaotic_core"), Tier.SS, 93, 2),
            new Candidate(id("occultism", "ritual_dummy/misc_celestial_chalice"), Tier.SS, 91, 2),
            new Candidate(id("appflux", "core_256m"), Tier.S, 90, 2),
            new Candidate(id("advanced_ae", "data_entangler"), Tier.S, 89, 2),
            new Candidate(id("advanced_ae", "quantum_multi_threader"), Tier.S, 87, 2),
            new Candidate(id("megacells", "cell_component_256m"), Tier.S, 86, 2),
            new Candidate(id("ae2omnicells", "quantum_omni_cell_component_256m"), Tier.S, 85, 2),
            new Candidate(id("mekanism_extras", "infinite_induction_cell"), Tier.S, 81, 2),
            new Candidate(id("mekanism_extras", "infinite_induction_provider"), Tier.S, 81, 2),
            new Candidate(id("mekanism", "pellet_antimatter"), Tier.A, 84, 2),
            new Candidate(id("mekanism_extras", "infinite_control_circuit"), Tier.A, 80, 2),
            new Candidate(id("ars_nouveau", "wilden_tribute"), Tier.A, 68, 2),
            new Candidate(id("minecraft", "elytra"), Tier.A, 54, 2),
            new Candidate(id("minecraft", "heavy_core"), Tier.A, 52, 2),
            new Candidate(id("minecraft", "dragon_head"), Tier.A, 46, 2),
            new Candidate(id("ae2", "256k_crafting_storage"), Tier.B, 49, 2),
            new Candidate(id("mekanism", "ultimate_induction_cell"), Tier.B, 38, 2),
            new Candidate(id("mekanism", "ultimate_induction_provider"), Tier.B, 38, 2),
            new Candidate(id("pneumaticcraft", "micromissiles"), Tier.B, 36, 2),
            new Candidate(id("minecraft", "dragon_egg"), Tier.B, 35, 2),
            new Candidate(id("minecraft", "heart_of_the_sea"), Tier.B, 28, 2),
            new Candidate(id("minecraft", "echo_shard"), Tier.B, 26, 2),
            new Candidate(id("minecraft", "nether_star"), Tier.B, 24, 2),
            new Candidate(id("minecraft", "torchflower"), Tier.C, 16, 2),
            new Candidate(id("minecraft", "recovery_compass"), Tier.C, 15, 2),
            new Candidate(id("minecraft", "slime_ball"), Tier.C, 12, 2));

    private static volatile CandidateCache cache = CandidateCache.empty();

    private ResearchNoteGenerator() {
    }

    public static void onServerStarting() {
        rebuildCache();
    }

    public static void onServerStopped() {
        cache = CandidateCache.empty();
    }

    public static void rebuildCache() {
        EnumMap<Tier, List<ResolvedCandidate>> byTier = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.values()) {
            byTier.put(tier, new ArrayList<>());
        }

        List<ResolvedCandidate> unified = new ArrayList<>();
        for (Candidate candidate : CONFIGURED_CANDIDATES) {
            if (!isItemAvailable(candidate.id())) {
                continue;
            }
            // 最终抽取权重 = 物品基础权重(模组/稀有度调整后) × 档位乘数。
            // 档位乘数让高档(SSS/SS/S)的物品在统一池里仍占多数,但不会再"必被抽中"。
            int finalWeight = Mth.clamp(effectiveWeight(candidate) * candidate.tier().multiplier(), 1, Integer.MAX_VALUE);
            ResolvedCandidate resolved = new ResolvedCandidate(candidate.id(), candidate.tier(), finalWeight,
                    candidate.descriptionVariants());
            byTier.get(candidate.tier()).add(resolved);
            unified.add(resolved);
        }

        int total = 0;
        for (Tier tier : Tier.values()) {
            List<ResolvedCandidate> tierEntries = byTier.get(tier);
            tierEntries.sort(Comparator.comparing(entry -> entry.id().toString()));
            byTier.put(tier, List.copyOf(tierEntries));
            total += tierEntries.size();
        }
        unified.sort(Comparator.<ResolvedCandidate, Tier>comparing(ResolvedCandidate::tier)
                .thenComparing(entry -> entry.id().toString()));

        cache = new CandidateCache(Map.copyOf(byTier), List.copyOf(unified), total);
        if (total < 9) {
            LOG.error("[ae2lt/note] Research note candidate pool is invalid: only {} available entries after registry filtering.",
                    total);
        } else {
            LOG.info("[ae2lt/note] Research note candidate pool ready: {} total entries.", total);
        }
        for (Tier tier : Tier.values()) {
            List<ResolvedCandidate> entries = byTier.get(tier);
            LOG.info("[ae2lt/note]   tier={} mult={} size={} items={}", tier, tier.multiplier(), entries.size(),
                    entries.stream().map(e -> e.id() + "(w=" + e.weight() + ")").toList());
        }
    }

    public static boolean hasValidPool() {
        return ensureCache().totalEntries() >= 9;
    }

    public static ResearchNoteData generate(ServerLevel level) {
        return generate(level, null);
    }

    /**
     * 生成一本笔记。若 {@code forcedGoal} 非空,强制使用该目标(用于铁砧调制);否则
     * 基于 {@link RandomSource} 随机抽取。随机路径仍消费一次 rng,保证不同 forcedGoal
     * 选择不会让玩家通过"看候选物顺序"反推世界种子/算法偏置。
     */
    public static ResearchNoteData generate(ServerLevel level, @org.jetbrains.annotations.Nullable RitualGoal forcedGoal) {
        CandidateCache activeCache = ensureCache();
        if (activeCache.totalEntries() < 9) {
            throw new IllegalStateException("Research note candidate pool has fewer than 9 available entries.");
        }

        UUID ritualSeed = UUID.randomUUID();
        long mixedSeed = mixSeed(ritualSeed, level.getServer().overworld().getSeed());
        RandomSource rng = RandomSource.create(mixedSeed);
        RitualGoal rolled = RitualGoal.values()[rng.nextInt(RitualGoal.values().length)];
        RitualGoal goal = forcedGoal != null ? forcedGoal : rolled;
        LOG.debug("[ae2lt/note] generate: ritualSeed={} mixedSeed={} goal={} forced={}", ritualSeed, mixedSeed, goal,
                forcedGoal != null);

        // 统一池加权抽取:所有候选一次性参与,高档通过 tier 乘数在同池中胜出,
        // 不再出现"某档池子被一次性吃光"的退化。每次调用 rng 状态独立,物品集合与顺序都会变化。
        List<ResolvedCandidate> selected = weightedPick(activeCache.unifiedPool(), 9, rng);
        if (selected.size() != 9) {
            throw new IllegalStateException(
                    "Research note generation failed to produce exactly 9 ritual items (got " + selected.size() + ").");
        }
        if (LOG.isDebugEnabled()) {
            EnumMap<Tier, Integer> tierHistogram = new EnumMap<>(Tier.class);
            for (Tier t : Tier.values()) {
                tierHistogram.put(t, 0);
            }
            for (ResolvedCandidate c : selected) {
                tierHistogram.merge(c.tier(), 1, Integer::sum);
            }
            LOG.debug("[ae2lt/note]   tierHistogram={}", tierHistogram);
        }

        shuffle(selected, rng);
        List<ResourceLocation> recipeItems = selected.stream().map(ResolvedCandidate::id).toList();
        List<String> descriptionKeys = selected.stream().map(entry -> entry.pickDescriptionKey(rng)).toList();
        LOG.info("[ae2lt/note] generated: goal={} items(ordered)={}", goal, recipeItems);
        return new ResearchNoteData(ritualSeed, goal, recipeItems, descriptionKeys, false);
    }

    private static CandidateCache ensureCache() {
        CandidateCache activeCache = cache;
        if (activeCache.totalEntries() > 0) {
            return activeCache;
        }

        rebuildCache();
        return cache;
    }

    private static List<ResolvedCandidate> weightedPick(List<ResolvedCandidate> source, int count, RandomSource rng) {
        if (count <= 0 || source.isEmpty()) {
            return List.of();
        }

        List<ResolvedCandidate> pool = new ArrayList<>(source);
        List<ResolvedCandidate> picked = new ArrayList<>(Math.min(count, source.size()));
        while (!pool.isEmpty() && picked.size() < count) {
            int totalWeight = 0;
            for (ResolvedCandidate candidate : pool) {
                totalWeight += candidate.weight();
            }
            if (totalWeight <= 0) {
                break;
            }

            int roll = rng.nextInt(totalWeight);
            int cursor = 0;
            for (int i = 0; i < pool.size(); i++) {
                ResolvedCandidate candidate = pool.get(i);
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

    private static void shuffle(List<ResolvedCandidate> candidates, RandomSource rng) {
        for (int i = candidates.size() - 1; i > 0; i--) {
            int swapIndex = rng.nextInt(i + 1);
            ResolvedCandidate temp = candidates.get(i);
            candidates.set(i, candidates.get(swapIndex));
            candidates.set(swapIndex, temp);
        }
    }

    private static boolean isItemAvailable(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR).isPresent();
    }

    private static int effectiveWeight(Candidate candidate) {
        int weight = candidate.baseWeight();
        if ("minecraft".equals(candidate.id().getNamespace())) {
            weight -= 8;
        }
        if (candidate.tier().isHighTier() && !"minecraft".equals(candidate.id().getNamespace())) {
            weight += 2;
        }
        return Mth.clamp(weight, 1, 100);
    }

    private static long mixSeed(UUID ritualSeed, long worldSeed) {
        return ritualSeed.getMostSignificantBits()
                ^ ritualSeed.getLeastSignificantBits()
                ^ Long.rotateLeft(worldSeed, 17)
                ^ SALT_RESEARCH_NOTE;
    }

    private static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    private record Candidate(ResourceLocation id, Tier tier, int baseWeight, int descriptionVariants) {
    }

    private record ResolvedCandidate(ResourceLocation id, Tier tier, int weight, int descriptionVariants) {
        private String pickDescriptionKey(RandomSource rng) {
            int variant = Math.max(1, descriptionVariants);
            // lang key 形式: ae2lt.research_note.desc.<mod_id>.<path>.<variant>
            // path 内的 '/' 被替换为 '.',避免 ResourceLocation 里的子目录破坏 lang key 的分段。
            String pathSegment = id.getPath().replace('/', '.');
            return "ae2lt.research_note.desc." + id.getNamespace() + "." + pathSegment + "." + rng.nextInt(variant);
        }
    }

    private record CandidateCache(Map<Tier, List<ResolvedCandidate>> byTier, List<ResolvedCandidate> unifiedPool,
            int totalEntries) {
        private static CandidateCache empty() {
            EnumMap<Tier, List<ResolvedCandidate>> byTier = new EnumMap<>(Tier.class);
            for (Tier tier : Tier.values()) {
                byTier.put(tier, List.of());
            }
            return new CandidateCache(Map.copyOf(byTier), List.of(), 0);
        }
    }

    /**
     * 档位 × 对应的抽取倍率。SSS 的期望出现次数 ~ SSS 总权重 / 全池总权重 × 9。
     * 当前调参下,满编候选池(30 项)一张笔记的期望分布约为:
     * SSS≈3.1, SS≈1.9, S≈2.5, A≈1.0, B≈0.45, C≈0.04。
     */
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

        private int multiplier() {
            return multiplier;
        }

        private boolean isHighTier() {
            return this == SSS || this == SS || this == S;
        }
    }
}

