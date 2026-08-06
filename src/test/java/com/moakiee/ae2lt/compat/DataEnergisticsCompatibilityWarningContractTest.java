package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DataEnergisticsCompatibilityWarningContractTest {
    @Test
    void cancelsOnlyDataEnergisticsMixinsTargetingOwnedClasses() throws Exception {
        String canceller = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/compat/DataEnergisticsMixinCanceller.java"));
        String service = Files.readString(Path.of(
                "src/main/resources/META-INF/services/com.bawnorton.mixinsquared.api.MixinCanceller"));

        assertTrue(canceller.contains("targetClassNames"));
        assertTrue(canceller.contains("DataEnergisticsTargetPolicy.shouldCancel"));
        assertTrue(canceller.contains("dataEnergisticsMixinProtectionEnabled()"));
        assertTrue(service.contains(
                "com.moakiee.ae2lt.mixin.compat.DataEnergisticsMixinCanceller"));
    }

    @Test
    void addsExplicitLoadingWarningWithoutBrokenTomlDiscouragedWrapper() throws Exception {
        String bootstrap = Files.readString(
                Path.of("src/main/java/com/moakiee/ae2lt/AE2LightningTech.java"));
        String modMetadata = Files.readString(
                Path.of("src/main/templates/META-INF/neoforge.mods.toml"));
        String chinese = Files.readString(
                Path.of("src/main/resources/assets/ae2lt/lang/zh_cn.json"));
        String english = Files.readString(
                Path.of("src/main/resources/assets/ae2lt/lang/en_us.json"));

        assertTrue(bootstrap.contains("addDataEnergisticsLoadingWarning(modContainer)"));
        assertTrue(bootstrap.contains("ModLoader.addLoadingIssue(ModLoadingIssue.warning("));
        assertTrue(bootstrap.contains("\"ae2lt.modloadingissue.data_energistics.unsupported\""));
        assertTrue(bootstrap.contains("\"ae2lt.modloadingissue.data_energistics.protection_disabled\""));
        assertTrue(bootstrap.contains("dataEnergistics.getModInfo().getVersion()"));
        assertTrue(bootstrap.contains("\"ae2lt.compat.data_energistics.feedback_scope\""));
        assertTrue(bootstrap.contains(".withAffectedMod(ae2ltContainer.getModInfo())"));
        assertTrue(chinese.contains("\"ae2lt.compat.data_energistics.unsupported\""));
        assertTrue(chinese.contains("AE2LT 已采取必要手段尽量修复兼容性"));
        assertTrue(chinese.contains("\\u00a7c\\u00a7l{0,i18n,ae2lt.compat.data_energistics.unsupported}"));
        assertTrue(chinese.contains("\\u00a7e{1,i18ntranslate}"));
        assertTrue(english.contains("\"ae2lt.compat.data_energistics.unsupported\""));
        assertTrue(english.contains("AE2LT has taken the necessary measures"));
        assertTrue(english.contains("\\u00a7c\\u00a7l{0,i18n,ae2lt.compat.data_energistics.unsupported}"));
        assertTrue(english.contains("\\u00a7e{1,i18ntranslate}"));
        assertFalse(modMetadata.contains("modId = \"data_energistics\""));
        assertFalse(modMetadata.contains("type = \"discouraged\""));
    }

    @Test
    void warnsEachPlayerAtLoginWhenDataEnergisticsIsLoaded() throws Exception {
        String bootstrap = Files.readString(
                Path.of("src/main/java/com/moakiee/ae2lt/AE2LightningTech.java"));
        String chinese = Files.readString(
                Path.of("src/main/resources/assets/ae2lt/lang/zh_cn.json"));
        String english = Files.readString(
                Path.of("src/main/resources/assets/ae2lt/lang/en_us.json"));

        assertTrue(bootstrap.contains("NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn)"));
        assertTrue(bootstrap.contains("PlayerEvent.PlayerLoggedInEvent"));
        assertTrue(bootstrap.contains("ModList.get().isLoaded(\"data_energistics\")"));
        assertTrue(bootstrap.contains("getModContainerById(\"data_energistics\")"));
        assertTrue(bootstrap.contains("\"ae2lt.compat.data_energistics.unsupported\""));
        assertTrue(bootstrap.contains("\"ae2lt.compat.data_energistics.feedback_scope\""));
        assertTrue(bootstrap.contains("https://github.com/fish1145/DataEnergistics/issues/144"));
        assertTrue(bootstrap.contains("ChatFormatting.RED, ChatFormatting.BOLD"));
        assertTrue(bootstrap.contains("ChatFormatting.YELLOW"));
        assertTrue(bootstrap.contains("warnAboutDataEnergistics()"));
        assertTrue(bootstrap.contains("has taken the necessary measures to mitigate"));
        assertTrue(bootstrap.contains("compatibility.dataEnergisticsMixinProtection=false"));
        assertTrue(bootstrap.contains("ae2lt-common.toml"));
        assertTrue(bootstrap.contains("ae2lt.compat.data_energistics.protection_disabled"));

        assertTrue(chinese.contains("\"ae2lt.compat.data_energistics.unsupported\""));
        assertTrue(chinese.contains("\"ae2lt.compat.data_energistics.feedback_scope\""));
        assertTrue(chinese.contains("已采取必要手段尽量修复兼容性"));
        assertTrue(chinese.contains("compatibility.dataEnergisticsMixinProtection"));
        assertTrue(chinese.contains("完整重启客户端或服务端"));
        assertTrue(chinese.contains("兼容保护已被配置禁用"));
        assertTrue(chinese.contains("请勿向 AE2 Lightning Tech 或 Data Energistics"));
        assertTrue(english.contains("\"ae2lt.compat.data_energistics.unsupported\""));
        assertTrue(english.contains("\"ae2lt.compat.data_energistics.feedback_scope\""));
        assertTrue(english.contains("taken the necessary measures to mitigate"));
        assertTrue(english.contains("set compatibility.dataEnergisticsMixinProtection to false"));
        assertTrue(english.contains("Compatibility protection is disabled by configuration"));
        assertTrue(english.contains("either the AE2 Lightning Tech or Data Energistics issue tracker"));
    }
}
