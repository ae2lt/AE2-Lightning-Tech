package com.moakiee.ae2lt.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.WrittenBookItem;

import com.moakiee.ae2lt.logic.research.ResearchNoteData;
import com.moakiee.ae2lt.logic.research.RitualGoal;

class ResearchNoteItemTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void generatedStateProducesAValidFivePageWrittenBookPayload() {
        ItemStack stack = new ItemStack(Items.PAPER);
        ResearchNoteData data = noteData(false);

        ResearchNoteItem.applyGeneratedState(stack, data);

        assertEquals(data, ResearchNoteData.read(stack));
        var tag = stack.getTag();
        assertNotNull(tag);
        assertTrue(WrittenBookItem.makeSureTagIsValid(tag));
        assertTrue(tag.getString(WrittenBookItem.TAG_TITLE).length() <= 32);
        assertTrue(tag.contains(WrittenBookItem.TAG_AUTHOR, Tag.TAG_STRING));
        assertEquals(0, tag.getInt(WrittenBookItem.TAG_GENERATION));
        assertTrue(tag.getBoolean(WrittenBookItem.TAG_RESOLVED));

        var pages = tag.getList(WrittenBookItem.TAG_PAGES, Tag.TAG_STRING);
        assertEquals(5, pages.size());
        for (int i = 0; i < pages.size(); i++) {
            assertNotNull(Component.Serializer.fromJson(pages.getString(i)));
        }
    }

    @Test
    void rebuildingACompletedNoteReplacesPagesInsteadOfAccumulatingThem() {
        ItemStack stack = new ItemStack(Items.PAPER);
        ResearchNoteItem.applyGeneratedState(stack, noteData(false));

        ResearchNoteData completed = noteData(true);
        ResearchNoteItem.applyGeneratedState(stack, completed);

        assertEquals(completed, ResearchNoteData.read(stack));
        var pages = stack.getTag().getList(WrittenBookItem.TAG_PAGES, Tag.TAG_STRING);
        assertEquals(5, pages.size());
        assertTrue(pages.getString(4).contains("ae2lt.research_note.page.completed"));
    }

    private static ResearchNoteData noteData(boolean consumed) {
        List<ResourceLocation> recipeItems = IntStream.range(0, 9)
                .mapToObj(index -> new ResourceLocation("minecraft", "stone_" + index))
                .toList();
        List<String> descriptions = IntStream.range(0, 9)
                .mapToObj(index -> "ae2lt.research_note.test." + index)
                .toList();
        return new ResearchNoteData(
                UUID.fromString("12345678-1234-5678-9abc-def012345678"),
                RitualGoal.HYPERDIMENSIONAL_PIGMEE,
                recipeItems,
                descriptions,
                consumed);
    }
}
