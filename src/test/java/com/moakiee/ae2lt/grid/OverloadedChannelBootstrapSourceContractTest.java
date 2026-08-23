package com.moakiee.ae2lt.grid;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OverloadedChannelBootstrapSourceContractTest {
    @Test
    void hostRegistersItsControllerFamilyAndForwardsConfiguredCapacity() throws Exception {
        String mod = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/AE2LightningTech.java"))
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        int setup = mod.indexOf("private void commonSetup(FMLCommonSetupEvent event)");
        int register = mod.indexOf(
                "ChannelSourceRegistry.registerController(\n"
                        + "                    \"ae2lt:overloaded_controller\", OverloadedControllerBlockEntity.class)",
                setup);
        int configure = mod.indexOf(
                "CoreConfig.setChannelsPerController(", setup);
        int createController = mod.indexOf(
                "controllerBlock.setBlockEntity(", setup);

        assertTrue(setup >= 0);
        assertTrue(register > setup);
        assertTrue(configure > register);
        assertTrue(createController > configure);
        assertTrue(mod.substring(configure, createController)
                .contains("AE2LTCommonConfig.overloadedControllerChannelsPerController()"));
    }
}
