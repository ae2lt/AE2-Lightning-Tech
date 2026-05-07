package com.moakiee.ae2lt.item;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.moakiee.ae2lt.logic.research.NoteModulationCatalysts;
import com.moakiee.ae2lt.logic.research.ResearchNoteData;
import com.moakiee.ae2lt.logic.research.ResearchNoteGenerator;
import com.moakiee.ae2lt.logic.research.RitualGoal;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

public class ResearchNoteItem extends Item {
    private static final String[] ORDER_MARKERS = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};
    private static final String BOOK_TITLE_KEY = "ae2lt.research_note.book.title";
    private static final String BOOK_AUTHOR_KEY = "ae2lt.research_note.book.author";

    public ResearchNoteItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(heldStack, true);
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ResearchNoteData data = ResearchNoteData.read(heldStack);

        if (data == null) {
            // 空白笔记:只负责"消耗 1 张、产出 1 张已生成笔记",不打开书。
            // 避免整叠处理带来的 merge/复制问题,也让玩家单独右键新笔记再查看。
            if (!ResearchNoteGenerator.hasValidPool()) {
                player.displayClientMessage(Component.translatable("ae2lt.research_note.error.invalid_pool")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(heldStack);
            }

            RitualGoal forcedGoal = ResearchNoteData.readForcedGoal(heldStack);
            ResearchNoteData generated = ResearchNoteGenerator.generate(serverLevel, forcedGoal);
            ItemStack generatedStack = new ItemStack(this);
            applyGeneratedState(generatedStack, generated);

            heldStack.shrink(1);
            if (!player.addItem(generatedStack)) {
                player.drop(generatedStack, false);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), false);
        }

        // 已生成笔记:直接打开书,不动堆叠(生成笔记因组件差异天然不可堆叠;
        // 即便被强行堆,也不在此处处理)。
        applyGeneratedState(heldStack, data);

        // ServerPlayer#openItemGui 只认 vanilla Items.WRITTEN_BOOK,碰到我们这种"挂着
        // WRITTEN_BOOK_CONTENT 组件的自定义物品"会直接 return。所以这里手动走一遍
        // 官方那套:先 resolve 书页里的动态组件(实体选择器之类),再把
        // ClientboundOpenBookPacket 直接发给客户端。客户端 handleOpenBook 会用玩家
        // 当前手持物 + WRITTEN_BOOK_CONTENT 组件构造 BookViewScreen。
        if (player instanceof ServerPlayer serverPlayer) {
            WrittenBookItem.resolveBookComponents(heldStack, serverPlayer.createCommandSourceStack(), serverPlayer);
            serverPlayer.connection.send(new ClientboundOpenBookPacket(hand));
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(heldStack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        ResearchNoteData data = ResearchNoteData.read(stack);
        if (data == null) {
            RitualGoal forcedGoal = ResearchNoteData.readForcedGoal(stack);
            if (forcedGoal != null) {
                tooltipComponents.add(Component.translatable("ae2lt.research_note.tooltip.modulated",
                        forcedGoal.getDisplayName()).withStyle(ChatFormatting.AQUA));
                tooltipComponents.add(Component.translatable("ae2lt.research_note.tooltip.open_hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
            tooltipComponents.add(Component.translatable("ae2lt.research_note.tooltip.blank")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.translatable("ae2lt.research_note.tooltip.open_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltipComponents.add(Component.translatable("ae2lt.research_note.tooltip.modulation_hint")
                    .withStyle(ChatFormatting.DARK_AQUA));
            for (RitualGoal goal : RitualGoal.values()) {
                Item catalyst = NoteModulationCatalysts.getCatalyst(goal);
                if (catalyst == null) {
                    continue;
                }
                tooltipComponents.add(Component.literal("  ")
                        .append(catalyst.getDescription().copy().withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(goal.getDisplayName().copy().withStyle(ChatFormatting.GRAY)));
            }
            return;
        }

        tooltipComponents.add(Component.translatable("ae2lt.research_note.tooltip.goal", data.goal().getDisplayName())
                .withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable(
                data.consumed() ? "ae2lt.research_note.tooltip.completed" : "ae2lt.research_note.tooltip.generated")
                .withStyle(data.consumed() ? ChatFormatting.RED : ChatFormatting.GRAY));
    }

    public static boolean isUsableGeneratedNote(ItemStack stack) {
        return stack.getItem() instanceof ResearchNoteItem && isGenerated(stack) && !ResearchNoteData.isConsumed(stack);
    }

    public static boolean isGenerated(ItemStack stack) {
        return ResearchNoteData.read(stack) != null;
    }

    public static @Nullable ResearchNoteData getData(ItemStack stack) {
        return ResearchNoteData.read(stack);
    }

    public static void applyGeneratedState(ItemStack stack, ResearchNoteData data) {
        data.writeTo(stack);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, createBookContent(data));
    }

    private static WrittenBookContent createBookContent(ResearchNoteData data) {
        List<Filterable<Component>> pages = buildPages(data).stream()
                .map(Filterable::passThrough)
                .toList();
        // WrittenBookContent 的 title/author 是 String,不支持 Component 序列化到网络后
        // 客户端自动翻译。这里用服务器端 Language.getInstance() 在生成时解析一次 lang 键,
        // 之后所有玩家看到的都是这份服务端 locale 的版本;至少去掉了代码里的硬编码英文。
        String title = Component.translatable(BOOK_TITLE_KEY, data.shortCode()).getString();
        String author = Component.translatable(BOOK_AUTHOR_KEY).getString();
        return new WrittenBookContent(
                Filterable.passThrough(title),
                author,
                0,
                pages,
                true);
    }

    private static List<Component> buildPages(ResearchNoteData data) {
        List<Component> pages = new ArrayList<>(5);
        pages.add(buildCoverPage(data));
        pages.add(Component.translatable("ae2lt.research_note.page.intro", data.goal().getDisplayName()));
        pages.add(buildRecipePage(data, 0, 5));
        pages.add(buildRecipePage(data, 5, 9));
        MutableComponent warningPage = Component.empty()
                .append(Component.translatable("ae2lt.research_note.page.warning"));
        if (data.consumed()) {
            warningPage = warningPage.append(Component.literal("\n\n"))
                    .append(Component.translatable("ae2lt.research_note.page.completed")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        pages.add(warningPage);
        return pages;
    }

    private static Component buildCoverPage(ResearchNoteData data) {
        return Component.translatable("ae2lt.research_note.page.cover", data.shortCode())
                .append(Component.literal("\n"))
                .append(Component.translatable("ae2lt.research_note.page.author"))
                .append(Component.literal("\n"))
                .append(Component.translatable("ae2lt.research_note.page.goal_line", data.goal().getDisplayName()));
    }

    private static MutableComponent buildRecipePage(ResearchNoteData data, int startInclusive, int endExclusive) {
        MutableComponent page = Component.empty();
        for (int i = startInclusive; i < Math.min(endExclusive, data.descriptionKeys().size()); i++) {
            if (i > startInclusive) {
                page = page.append(Component.literal("\n"));
            }
            page = page.append(Component.literal(ORDER_MARKERS[i] + ". "))
                    .append(Component.translatable(data.descriptionKeys().get(i)));
        }
        return page;
    }
}
