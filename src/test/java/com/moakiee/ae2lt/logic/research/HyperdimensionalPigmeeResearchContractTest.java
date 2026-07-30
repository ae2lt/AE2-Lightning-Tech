package com.moakiee.ae2lt.logic.research;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class HyperdimensionalPigmeeResearchContractTest {
    @Test
    void noteCombinesThreeGuaranteedAe2ltItemsWithSixWeightedDraws() throws Exception {
        String generator = readJava("ResearchNoteGenerator.java");

        assertTrue(generator.contains("RitualGoal.HYPERDIMENSIONAL_PIGMEE"));
        assertTrue(generator.contains("item(\"pigmee_core\")"));
        assertTrue(generator.contains("item(\"module_undying\")"));
        assertTrue(generator.contains("item(\"module_phase_lock\")"));
        assertTrue(generator.contains("private static final int RANDOM_ITEM_COUNT = 6"));
        assertTrue(generator.contains("AE2LTCommonConfig.easterEggWeights()"));
        assertTrue(generator.contains("configuredAvailableCandidates()"));
        assertTrue(generator.contains("weightedPickAvailable(RANDOM_ITEM_COUNT, random)"));
        assertTrue(generator.contains("shuffle(randomSelected, random)"));
        assertTrue(generator.contains("selected.addAll(randomSelected)"));
        assertFalse(generator.contains("shuffle(selected, random)"));
        assertFalse(generator.contains("RANDOM_CANDIDATES"));
    }

    @Test
    void successfulLightningRitualAlwaysCreatesTheHyperdimensionalPigmee() throws Exception {
        String ritual = readJava("ResearchRitualService.java");
        String ionizer = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/blockentity/AtmosphericIonizerBlockEntity.java"));
        String rewardEntity = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/entity/RitualHyperdimensionalPigmeeEntity.java"));
        String renderer = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/RitualHyperdimensionalPigmeeRenderer.java"));
        String burstPacket = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/network/RitualItemBurstPacket.java"));
        String burstClient = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/network/RitualItemBurstClientBridge.java"));
        String networking = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/network/NetworkInit.java"));
        String bootstrap = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/AE2LightningTech.java"));

        assertTrue(ritual.contains("new ItemStack(ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO_ITEM.get())"));
        assertTrue(ritual.contains("new RitualHyperdimensionalPigmeeEntity("));
        assertFalse(ritual.contains("FixedInfiniteCellItem"));
        assertTrue(ritual.contains("markRitualLightning(LightningBolt lightningBolt, BlockPos ionizerPos)"));
        assertTrue(ritual.contains("data.getBoolean(TAG_RITUAL_LIGHTNING)"));
        assertTrue(ritual.contains("BlockPos.of(data.getLong(TAG_RITUAL_IONIZER_POS))"));
        assertTrue(ionizer.contains("ResearchRitualService.markRitualLightning(bolt, worldPosition)"));
        assertTrue(ritual.contains("matchesDropOrder(candidates, note.recipeItems())"));
        assertTrue(ritual.contains("actualTickGroup"));
        assertTrue(ritual.contains("sameMultiset(actualTickGroup, expectedOrder.subList(start, end))"));
        assertFalse(ritual.contains("thenComparingInt(ItemEntity::getId)"));
        assertTrue(rewardEntity.contains("public static final int CEREMONY_TICKS = 100"));
        assertTrue(rewardEntity.contains("FIRST_BURST_REMAINING = 80"));
        assertTrue(rewardEntity.contains("SECOND_BURST_REMAINING = 50"));
        assertTrue(rewardEntity.contains("THIRD_BURST_REMAINING = 20"));
        assertTrue(rewardEntity.contains("new RitualItemBurstPacket(getId(), stage)"));
        assertFalse(rewardEntity.contains("broadcastEntityEvent(this, (byte) 35)"));
        assertTrue(networking.contains("RitualItemBurstPacket::handle"));
        assertTrue(burstPacket.contains("public static final byte PIGMEE_CORE = 0"));
        assertTrue(burstPacket.contains("public static final byte UNDYING_MODULE = 1"));
        assertTrue(burstPacket.contains("public static final byte PHASE_LOCK_MODULE = 2"));
        assertTrue(burstClient.contains("new ItemStack(ModItems.PIGMEE_CORE.get())"));
        assertTrue(burstClient.contains("new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_UNDYING.get())"));
        assertTrue(burstClient.contains("new ItemStack(ModItems.CELESTWEAVE_SUBMODULE_PHASE_LOCK.get())"));
        assertTrue(burstClient.contains("displayItemActivation(activationItem)"));
        assertTrue(burstClient.contains("ParticleTypes.TOTEM_OF_UNDYING"));
        assertTrue(rewardEntity.contains("setPickUpDelay(CEREMONY_TICKS)"));
        assertTrue(rewardEntity.contains("setNoPickUpDelay()"));
        assertTrue(rewardEntity.contains("public void playerTouch(Player player)"));
        assertTrue(rewardEntity.contains("getItem().getCount() < countBefore"));
        assertTrue(rewardEntity.contains("awardPickupAdvancement(serverPlayer)"));
        assertTrue(renderer.contains("getCeremonyScale(partialTick)"));
        assertFalse(bootstrap.contains("ResearchNoteModulationHandler"));
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/research/ResearchNoteModulationHandler.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/research/NoteModulationCatalysts.java")));
    }

    @Test
    void claimingTheRitualRewardGrantsAHiddenAdvancement() throws Exception {
        String advancement = Files.readString(Path.of(
                "src/main/resources/data/ae2lt/advancement/main/hyperdimensional_pigmee.json"));
        String chinese = Files.readString(Path.of(
                "src/main/resources/assets/ae2lt/lang/zh_cn.json"));

        assertTrue(advancement.contains("\"parent\": \"ae2lt:main/pig_zip\""));
        assertTrue(advancement.contains("\"trigger\": \"minecraft:impossible\""));
        assertTrue(advancement.contains("\"hidden\": true"));
        assertTrue(advancement.contains("\"claim_ritual_pigmee\""));
        assertTrue(chinese.contains("\"advancements.ae2lt.hyperdimensional_pigmee.title\": \"这是……猪咪？！\""));
    }

    @Test
    void hiddenGuideHasNoNavigationButUsesTheNativeGuideHotkeyItemIndex() throws Exception {
        Path englishPath = Path.of(
                "src/main/resources/assets/ae2lt/ae2guide/hidden/hyperdimensional-pigmee.md");
        Path chinesePath = Path.of(
                "src/main/resources/assets/ae2lt/ae2guide/_zh_cn/hidden/hyperdimensional-pigmee.md");
        String english = Files.readString(englishPath);
        String chinese = Files.readString(chinesePath);
        String fumos = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/registry/ModFumos.java"));

        assertTrue(english.startsWith("---"));
        assertTrue(chinese.startsWith("---"));
        assertTrue(english.contains("item_ids:"));
        assertTrue(chinese.contains("item_ids:"));
        assertTrue(english.contains("ae2lt:hyperdimensional_pigmee_fumo"));
        assertTrue(chinese.contains("ae2lt:hyperdimensional_pigmee_fumo"));
        assertFalse(english.contains("navigation:"));
        assertFalse(chinese.contains("navigation:"));
        assertFalse(english.contains("<ItemLink"));
        assertFalse(chinese.contains("<ItemLink"));
        assertFalse(english.contains("item entities"));
        assertFalse(english.contains("game tick"));
        assertFalse(chinese.contains("掉落物"));
        assertFalse(chinese.contains("游戏刻"));
        assertTrue(chinese.contains("完成一次跨越界限的转换"));
        assertTrue(chinese.contains("变回普通猪咪"));
        assertTrue(fumos.contains("new FumoBlockItem("));
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/moakiee/ae2lt/item/HyperdimensionalPigmeeFumoItem.java")));
    }

    private static String readJava(String filename) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/logic/research", filename));
    }
}
