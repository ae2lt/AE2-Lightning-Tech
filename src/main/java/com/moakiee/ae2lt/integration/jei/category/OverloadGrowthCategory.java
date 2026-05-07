package com.moakiee.ae2lt.integration.jei.category;

import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.block.BuddingOverloadCrystalBlock;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class OverloadGrowthCategory extends AbstractRecipeCategory<OverloadGrowthCategory.Page> {
    public static final RecipeType<Page> TYPE =
            RecipeType.create(AE2LightningTech.MODID, "overload_growth", Page.class);

    private static final int WIDTH = 150;
    private static final int HEIGHT = 60;
    private static final int BODY_COLOR = 0xFF404040;

    private final List<ItemStack> buddingOverloadVariants = List.of(
            new ItemStack(ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.FLAWLESS_BUDDING_OVERLOAD_CRYSTAL.get()));

    private final List<ItemStack> imperfectBuddingOverloadVariants = List.of(
            new ItemStack(ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL.get()));

    private final List<ItemStack> buddingOverloadDecayOrder = List.of(
            new ItemStack(ModBlocks.OVERLOAD_CRYSTAL_BLOCK.get()),
            new ItemStack(ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL.get()));

    private final List<ItemStack> imperfectBuddingOverloadDecayOrder = List.of(
            new ItemStack(ModBlocks.OVERLOAD_CRYSTAL_BLOCK.get()),
            new ItemStack(ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL.get()),
            new ItemStack(ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL.get()));

    private final List<ItemStack> budGrowthStages = List.of(
            new ItemStack(ModBlocks.SMALL_OVERLOAD_CRYSTAL_BUD.get()),
            new ItemStack(ModBlocks.MEDIUM_OVERLOAD_CRYSTAL_BUD.get()),
            new ItemStack(ModBlocks.LARGE_OVERLOAD_CRYSTAL_BUD.get()),
            new ItemStack(ModBlocks.OVERLOAD_CRYSTAL_CLUSTER.get()));

    private final int centerX = WIDTH / 2;

    public OverloadGrowthCategory(IGuiHelper guiHelper) {
        super(
                TYPE,
                Component.translatable("jei.ae2lt.overload_growth.title"),
                guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.OVERLOAD_CRYSTAL_CLUSTER.get())),
                WIDTH,
                HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Page recipe, IFocusGroup focuses) {
        getView(recipe).buildSlots(builder);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Page recipe, IFocusGroup focuses) {
        getView(recipe).createRecipeExtras(builder, focuses);
    }

    private View getView(Page page) {
        return switch (page) {
            case BUD_GROWTH -> new BudGrowthView();
            case BUD_LOOT, CLUSTER_LOOT -> new LootView(page);
            case BUDDING_OVERLOAD_DECAY -> new BuddingOverloadDecayView();
            case BUDDING_OVERLOAD_MOVING -> new BuddingOverloadMovingView();
            case BUDDING_OVERLOAD_ACCELERATION -> new BuddingOverloadAccelerationView();
        };
    }

    public enum Page {
        BUD_GROWTH,
        BUD_LOOT,
        CLUSTER_LOOT,
        BUDDING_OVERLOAD_DECAY,
        BUDDING_OVERLOAD_MOVING,
        BUDDING_OVERLOAD_ACCELERATION
    }

    private interface View {
        default void buildSlots(IRecipeLayoutBuilder builder) {
        }

        default void createRecipeExtras(IRecipeExtrasBuilder builder, IFocusGroup focuses) {
        }
    }

    private class BudGrowthView implements View {
        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, IFocusGroup focuses) {
            builder.addText(Component.translatable("jei.ae2lt.overload_growth.bud_growth"), WIDTH, 20)
                    .setTextAlignment(HorizontalAlignment.CENTER)
                    .setColor(BODY_COLOR);
            builder.addRecipeArrow().setPosition(centerX - 12, 25);
        }

        @Override
        public void buildSlots(IRecipeLayoutBuilder builder) {
            builder.addSlot(RecipeIngredientRole.CATALYST, centerX - 40, 25)
                    .setStandardSlotBackground()
                    .addItemStacks(buddingOverloadVariants);

            builder.addOutputSlot(centerX + 22, 25)
                    .setStandardSlotBackground()
                    .addItemStacks(budGrowthStages);
        }
    }

    private class LootView implements View {
        private final Page page;

        private LootView(Page page) {
            this.page = page;
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, IFocusGroup focuses) {
            var key = page == Page.BUD_LOOT
                    ? "jei.ae2lt.overload_growth.bud_loot"
                    : "jei.ae2lt.overload_growth.cluster_loot";
            builder.addText(Component.translatable(key), WIDTH, 20)
                    .setTextAlignment(HorizontalAlignment.CENTER)
                    .setColor(BODY_COLOR);
            builder.addRecipeArrow().setPosition(centerX - 12, 25);

            if (page == Page.CLUSTER_LOOT) {
                builder.addText(Component.translatable("jei.ae2lt.overload_growth.cluster_loot_fortune"), WIDTH, 10)
                        .setPosition(0, 50)
                        .setTextAlignment(HorizontalAlignment.CENTER)
                        .setColor(BODY_COLOR);
            }
        }

        @Override
        public void buildSlots(IRecipeLayoutBuilder builder) {
            if (page == Page.BUD_LOOT) {
                builder.addInputSlot(centerX - 40, 25)
                        .setStandardSlotBackground()
                        .addItemStacks(List.of(
                                new ItemStack(ModBlocks.SMALL_OVERLOAD_CRYSTAL_BUD.get()),
                                new ItemStack(ModBlocks.MEDIUM_OVERLOAD_CRYSTAL_BUD.get()),
                                new ItemStack(ModBlocks.LARGE_OVERLOAD_CRYSTAL_BUD.get())));

                builder.addOutputSlot(centerX + 22, 25)
                        .setStandardSlotBackground()
                        .addItemStack(new ItemStack(ModItems.OVERLOAD_CRYSTAL_DUST.get()));
            } else {
                builder.addInputSlot(centerX - 40, 25)
                        .setStandardSlotBackground()
                        .addItemStack(new ItemStack(ModBlocks.OVERLOAD_CRYSTAL_CLUSTER.get()));

                builder.addOutputSlot(centerX + 22, 25)
                        .setStandardSlotBackground()
                        .addItemStack(new ItemStack(ModItems.OVERLOAD_CRYSTAL.get(), 4));
            }
        }
    }

    private class BuddingOverloadDecayView implements View {
        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, IFocusGroup focuses) {
            builder.addText(Component.translatable("jei.ae2lt.overload_growth.decay"), WIDTH, 20)
                    .setTextAlignment(HorizontalAlignment.CENTER)
                    .setColor(BODY_COLOR);
            builder.addRecipeArrow().setPosition(centerX - 12, 30);

            int decayChancePct = 100 / BuddingOverloadCrystalBlock.DECAY_CHANCE;
            builder.addText(
                            Component.translatable("jei.ae2lt.overload_growth.decay_chance", decayChancePct),
                            WIDTH,
                            10)
                    .setPosition(0, 50)
                    .setTextAlignment(HorizontalAlignment.CENTER)
                    .setColor(BODY_COLOR);
        }

        @Override
        public void buildSlots(IRecipeLayoutBuilder builder) {
            var input = builder.addInputSlot(centerX - 40, 30)
                    .setStandardSlotBackground()
                    .addItemStacks(imperfectBuddingOverloadVariants);

            var output = builder.addOutputSlot(centerX + 22, 30)
                    .setStandardSlotBackground()
                    .addItemStacks(imperfectBuddingOverloadDecayOrder);

            builder.createFocusLink(input, output);
        }
    }

    private class BuddingOverloadMovingView implements View {
        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, IFocusGroup focuses) {
            builder.addRecipeArrow().setPosition(centerX - 12, 0);
            builder.addScrollBoxWidget(WIDTH, HEIGHT - 20, 0, 20)
                    .setContents(List.of(
                            Component.translatable("jei.ae2lt.overload_growth.break_decay").withColor(BODY_COLOR),
                            Component.translatable("jei.ae2lt.overload_growth.silk_touch").withColor(BODY_COLOR),
                            Component.translatable("jei.ae2lt.overload_growth.flawless_note").withColor(BODY_COLOR)));
        }

        @Override
        public void buildSlots(IRecipeLayoutBuilder builder) {
            var input = builder.addInputSlot(centerX - 40, 0)
                    .setStandardSlotBackground()
                    .addItemStacks(buddingOverloadVariants);

            var output = builder.addOutputSlot(centerX + 22, 0)
                    .setStandardSlotBackground()
                    .addItemStacks(buddingOverloadDecayOrder);

            builder.createFocusLink(input, output);
        }
    }

    private class BuddingOverloadAccelerationView implements View {
        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, IFocusGroup focuses) {
            builder.addText(Component.translatable("jei.ae2lt.overload_growth.acceleration"), WIDTH, 38)
                    .setTextAlignment(HorizontalAlignment.CENTER)
                    .setLineSpacing(0)
                    .setColor(BODY_COLOR);

            builder.addText(Component.literal("+"), 16, 16)
                    .setPosition(centerX - 8, 40)
                    .setTextAlignment(HorizontalAlignment.CENTER)
                    .setTextAlignment(VerticalAlignment.CENTER)
                    .setColor(0xFFFFFFFF)
                    .setShadow(true);
        }

        @Override
        public void buildSlots(IRecipeLayoutBuilder builder) {
            builder.addInputSlot(centerX - 24, 40)
                    .setStandardSlotBackground()
                    .addItemStacks(buddingOverloadVariants);

            builder.addSlot(RecipeIngredientRole.CATALYST, centerX + 8, 40)
                    .setStandardSlotBackground()
                    .addItemLike(AEBlocks.GROWTH_ACCELERATOR);
        }
    }
}
