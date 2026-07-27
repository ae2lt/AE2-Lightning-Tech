package com.moakiee.ae2lt.command;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.ResearchNoteItem;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Development-run helpers. This command tree is never registered in a production environment.
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class Ae2ltDevCommands {
    private Ae2ltDevCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }

        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ae2lt")
                .then(Commands.literal("giveRitual")
                        .executes(context -> giveHeldRitualItems(context.getSource()))));
    }

    private static int giveHeldRitualItems(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (!ResearchNoteItem.isUsableGeneratedNote(held)) {
            source.sendFailure(Component.literal("主手需要持有一张已生成且尚未完成的研究笔记。"));
            return 0;
        }

        var note = ResearchNoteItem.getData(held);
        if (note == null) {
            source.sendFailure(Component.literal("无法读取主手研究笔记。"));
            return 0;
        }

        List<ItemStack> ritualItems = new ArrayList<>(note.recipeItems().size());
        for (var itemId : note.recipeItems()) {
            var item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR);
            if (item == Items.AIR) {
                source.sendFailure(Component.literal("研究材料不存在：" + itemId));
                return 0;
            }
            ritualItems.add(new ItemStack(item));
        }

        for (ItemStack ritualItem : ritualItems) {
            if (!player.addItem(ritualItem)) {
                player.drop(ritualItem, false);
            }
        }
        source.sendSuccess(() -> Component.literal("已按笔记顺序给予 9 项仪式材料。"), false);
        return ritualItems.size();
    }
}
