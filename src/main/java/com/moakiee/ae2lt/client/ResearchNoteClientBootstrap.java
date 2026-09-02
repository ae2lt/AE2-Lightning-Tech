package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.item.ResearchNoteItem;
import com.moakiee.ae2lt.network.ResearchNoteClientBridge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WrittenBookItem;

public final class ResearchNoteClientBootstrap {
    private static boolean installed;

    private ResearchNoteClientBootstrap() {
    }

    public static void install() {
        if (installed) {
            return;
        }
        ResearchNoteClientBridge.install(new ResearchNoteClientBridge.Hooks() {
            @Override
            public void open(ItemStack book) {
                if (!(book.getItem() instanceof ResearchNoteItem)
                        || !ResearchNoteItem.isGenerated(book)
                        || !WrittenBookItem.makeSureTagIsValid(book.getTag())) {
                    return;
                }
                Minecraft.getInstance().setScreen(
                        new BookViewScreen(new BookViewScreen.WrittenBookAccess(book)));
            }
        });
        installed = true;
    }
}
