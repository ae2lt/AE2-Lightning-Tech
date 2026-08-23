package com.moakiee.ae2lt.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LightningTickDispatchSourceContractTest {
    @Test
    void dispatchesAllLightningSubclassesOutsideTheirVirtualTickMethod() throws Exception {
        String serverLevelMixin = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/ServerLevelMixin.java"));
        String mixinConfig = Files.readString(Path.of("src/main/resources/ae2lt.mixins.json"));

        assertTrue(serverLevelMixin.contains("@ModifyReceiver("));
        assertTrue(serverLevelMixin.contains("method = \"tickNonPassenger\""));
        assertTrue(serverLevelMixin.contains(
                "target = \"Lnet/minecraft/world/entity/Entity;tick()V\""));
        assertTrue(serverLevelMixin.contains(
                "private Entity ae2lt$handleLightningTick(Entity entity)"));
        assertTrue(serverLevelMixin.contains("return entity;"),
                "The receiver modifier must preserve the original entity tick target");
        assertTrue(serverLevelMixin.contains(
                "NaturalLightningTransformationHandler.handleLightningTick(lightningBolt)"));
        assertTrue(serverLevelMixin.contains(
                "LightningItemTransformationHandler.handleLightningTick(lightningBolt)"));
        assertFalse(mixinConfig.contains("LightningBoltMixin"),
                "The parent-method injection would still miss subclasses that replace tick()");
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/moakiee/ae2lt/mixin/LightningBoltMixin.java")));
    }
}
