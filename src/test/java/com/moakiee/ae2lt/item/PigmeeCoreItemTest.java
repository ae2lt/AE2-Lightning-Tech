package com.moakiee.ae2lt.item;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PigmeeCoreItemTest {
    @Test
    void returnsOneExactCopyThroughTheStackSensitiveRemainderApi() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/item/PigmeeCoreItem.java"));

        assertTrue(source.contains("boolean hasCraftingRemainingItem(ItemStack stack)"));
        assertTrue(source.contains("ItemStack getCraftingRemainingItem(ItemStack stack)"));
        assertTrue(source.contains("ItemStack remainder = stack.copy()"));
        assertTrue(source.contains("remainder.setCount(1)"));
        assertTrue(source.contains("return remainder"));
    }
}
