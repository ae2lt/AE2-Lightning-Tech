package com.moakiee.ae2lt.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class Ae2ltDevCommandsContractTest {
    @Test
    void giveRitualExistsOnlyInDevelopmentAndUsesTheHeldGeneratedNote() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/command/Ae2ltDevCommands.java"));

        assertTrue(source.contains("if (FMLEnvironment.production)"));
        assertTrue(source.contains("Commands.literal(\"ae2lt\")"));
        assertTrue(source.contains("Commands.literal(\"giveRitual\")"));
        assertTrue(source.contains("player.getMainHandItem()"));
        assertTrue(source.contains("ResearchNoteItem.isUsableGeneratedNote(held)"));
        assertTrue(source.contains("note.recipeItems()"));
        assertTrue(source.contains("player.addItem(ritualItem)"));
        assertTrue(source.contains("player.drop(ritualItem, false)"));
    }
}
