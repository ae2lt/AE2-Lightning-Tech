package com.moakiee.ae2lt.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PigmeeCoreAcquisitionContractTest {
    @Test
    void onlyAdultPigsCrushedOnOverloadCrystalAreConvertedWithoutNormalDrops() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/event/PigmeeCoreAcquisitionHandler.java"));

        assertTrue(source.contains("instanceof Pig pig"));
        assertTrue(source.contains("pig.isBaby()"));
        assertTrue(source.contains("DamageTypes.FALLING_ANVIL"));
        assertTrue(source.contains("isAnvilLandingOnOverloadCrystal(pig)"));
        assertTrue(source.contains("pig.getBlockStateOn().is(ModBlocks.OVERLOAD_CRYSTAL_BLOCK.get())"));
        assertTrue(source.contains("instanceof ServerLevel level"));
        assertTrue(source.contains("new ItemStack(ModItems.PIGMEE_CORE.get())"));
        assertTrue(source.contains("if (!level.addFreshEntity(drop))"));
        assertTrue(source.contains("event.setCanceled(true)"));
        assertTrue(source.contains("pig.discard()"));
        assertFalse(source.contains("LivingDeathEvent"));
    }
}
