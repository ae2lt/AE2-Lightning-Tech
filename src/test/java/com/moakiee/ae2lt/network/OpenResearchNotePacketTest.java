package com.moakiee.ae2lt.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class OpenResearchNotePacketTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void codecPreservesTheServerBookSnapshot() {
        ItemStack book = new ItemStack(Items.PAPER, 3);
        book.getOrCreateTag().putString("title", "Research Note #A1B2");
        book.getOrCreateTag().putBoolean("resolved", true);
        OpenResearchNotePacket packet = new OpenResearchNotePacket(book);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        try {
            packet.write(buffer);
            OpenResearchNotePacket decoded = OpenResearchNotePacket.decode(buffer);

            assertEquals(book.getCount(), decoded.book().getCount());
            assertTrue(ItemStack.isSameItemSameTags(book, decoded.book()));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
