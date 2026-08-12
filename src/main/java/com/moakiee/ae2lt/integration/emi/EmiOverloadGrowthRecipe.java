package com.moakiee.ae2lt.integration.emi;

import java.util.List;

import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.block.BuddingOverloadCrystalBlock;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class EmiOverloadGrowthRecipe extends BasicEmiRecipe {
    private static final int WIDTH = 150;
    private static final int CENTER_X = WIDTH / 2;
    private static final int LINKED_SLOT_SEED = 0xAE2_17;

    private static final List<EmiStack> BUDDING = List.of(
            EmiStack.of(ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL.get()),
            EmiStack.of(ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL.get()),
            EmiStack.of(ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL.get()),
            EmiStack.of(ModBlocks.FLAWLESS_BUDDING_OVERLOAD_CRYSTAL.get()));
    private static final List<EmiStack> IMPERFECT_BUDDING = BUDDING.subList(0, 3);
    private static final List<EmiStack> DECAY_ORDER = List.of(
            EmiStack.of(ModBlocks.OVERLOAD_CRYSTAL_BLOCK.get()),
            EmiStack.of(ModBlocks.DAMAGED_BUDDING_OVERLOAD_CRYSTAL.get()),
            EmiStack.of(ModBlocks.CRACKED_BUDDING_OVERLOAD_CRYSTAL.get()),
            EmiStack.of(ModBlocks.FLAWED_BUDDING_OVERLOAD_CRYSTAL.get()));
    private static final List<EmiStack> IMPERFECT_DECAY_ORDER = DECAY_ORDER.subList(0, 3);
    private static final List<EmiStack> BUD_STAGES = List.of(
            EmiStack.of(ModBlocks.SMALL_OVERLOAD_CRYSTAL_BUD.get()),
            EmiStack.of(ModBlocks.MEDIUM_OVERLOAD_CRYSTAL_BUD.get()),
            EmiStack.of(ModBlocks.LARGE_OVERLOAD_CRYSTAL_BUD.get()),
            EmiStack.of(ModBlocks.OVERLOAD_CRYSTAL_CLUSTER.get()));

    private final Page page;

    private EmiOverloadGrowthRecipe(Page page) {
        super(
                AE2LTEmiCategories.OVERLOAD_GROWTH,
                EmiRecipeWidgets.syntheticId("overload_growth/" + page.id),
                WIDTH,
                60);
        this.page = page;
        switch (page) {
            case BUD_GROWTH -> {
                catalysts.add(EmiIngredient.of(BUDDING));
                outputs.addAll(BUD_STAGES);
            }
            case BUD_LOOT -> {
                inputs.add(EmiIngredient.of(BUD_STAGES.subList(0, 3)));
                outputs.add(EmiStack.of(ModItems.OVERLOAD_CRYSTAL_DUST.get()));
            }
            case CLUSTER_LOOT -> {
                inputs.add(EmiStack.of(ModBlocks.OVERLOAD_CRYSTAL_CLUSTER.get()));
                outputs.add(EmiStack.of(ModItems.OVERLOAD_CRYSTAL.get(), 4));
            }
            case DECAY -> {
                inputs.add(EmiIngredient.of(IMPERFECT_BUDDING));
                outputs.addAll(IMPERFECT_DECAY_ORDER);
            }
            case MOVING -> {
                inputs.add(EmiIngredient.of(BUDDING));
                outputs.addAll(DECAY_ORDER);
            }
            case ACCELERATION -> {
                inputs.add(EmiIngredient.of(BUDDING));
                catalysts.add(EmiStack.of(AEBlocks.GROWTH_ACCELERATOR.asItem()));
            }
        }
    }

    static void registerAll(EmiRegistry registry) {
        for (Page page : Page.values()) {
            registry.addRecipe(new EmiOverloadGrowthRecipe(page));
        }
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        switch (page) {
            case BUD_GROWTH -> {
                title(widgets, "jei.ae2lt.overload_growth.bud_growth", 5);
                EmiRecipeWidgets.addSlot(widgets, catalysts.get(0), CENTER_X - 40, 25);
                widgets.addFillingArrow(CENTER_X - 12, 25, 2_000);
                EmiRecipeWidgets.addSlot(widgets, EmiIngredient.of(BUD_STAGES), CENTER_X + 22, 25)
                        .recipeContext(this);
            }
            case BUD_LOOT -> {
                title(widgets, "jei.ae2lt.overload_growth.bud_loot", 5);
                EmiRecipeWidgets.addSlot(widgets, inputs.get(0), CENTER_X - 40, 25);
                widgets.addFillingArrow(CENTER_X - 12, 25, 2_000);
                EmiRecipeWidgets.addSlot(widgets, outputs.get(0), CENTER_X + 22, 25)
                        .recipeContext(this);
            }
            case CLUSTER_LOOT -> {
                title(widgets, "jei.ae2lt.overload_growth.cluster_loot", 5);
                EmiRecipeWidgets.addSlot(widgets, inputs.get(0), CENTER_X - 40, 25);
                widgets.addFillingArrow(CENTER_X - 12, 25, 2_000);
                EmiRecipeWidgets.addSlot(widgets, outputs.get(0), CENTER_X + 22, 25)
                        .recipeContext(this);
                title(widgets, "jei.ae2lt.overload_growth.cluster_loot_fortune", 50);
            }
            case DECAY -> {
                title(widgets, "jei.ae2lt.overload_growth.decay", 5);
                linkedSlots(widgets, IMPERFECT_BUDDING, IMPERFECT_DECAY_ORDER, 30);
                widgets.addFillingArrow(CENTER_X - 12, 30, 2_000);
                EmiRecipeWidgets.centeredText(
                        widgets,
                        Component.translatable(
                                "jei.ae2lt.overload_growth.decay_chance",
                                100 / BuddingOverloadCrystalBlock.DECAY_CHANCE),
                        CENTER_X,
                        50);
            }
            case MOVING -> {
                linkedSlots(widgets, BUDDING, DECAY_ORDER, 0);
                widgets.addFillingArrow(CENTER_X - 12, 0, 2_000);
                widgets.addDrawable(0, 20, WIDTH, 40, (graphics, mouseX, mouseY, delta) -> {
                    int y = 0;
                    var font = Minecraft.getInstance().font;
                    for (String key : List.of(
                            "jei.ae2lt.overload_growth.break_decay",
                            "jei.ae2lt.overload_growth.silk_touch",
                            "jei.ae2lt.overload_growth.flawless_note")) {
                        for (var line : font.split(Component.translatable(key), WIDTH - 4)) {
                            if (y + font.lineHeight > 60) {
                                return;
                            }
                            graphics.drawString(font, line, 2, y, EmiRecipeWidgets.TEXT_COLOR, false);
                            y += font.lineHeight;
                        }
                    }
                });
            }
            case ACCELERATION -> {
                title(widgets, "jei.ae2lt.overload_growth.acceleration", 4);
                EmiRecipeWidgets.addSlot(widgets, inputs.get(0), CENTER_X - 24, 40);
                widgets.addText(Component.literal("+"), CENTER_X, 44, 0xFFFFFFFF, true)
                        .horizontalAlign(dev.emi.emi.api.widget.TextWidget.Alignment.CENTER);
                EmiRecipeWidgets.addSlot(widgets, catalysts.get(0), CENTER_X + 8, 40)
                        .catalyst(true);
            }
        }
    }

    private void linkedSlots(
            WidgetHolder widgets,
            List<EmiStack> inputVariants,
            List<EmiStack> outputVariants,
            int y) {
        widgets.addGeneratedSlot(
                random -> inputVariants.get(random.nextInt(inputVariants.size())),
                LINKED_SLOT_SEED,
                CENTER_X - 41,
                y - 1);
        widgets.addGeneratedSlot(
                        random -> outputVariants.get(random.nextInt(outputVariants.size())),
                        LINKED_SLOT_SEED,
                        CENTER_X + 21,
                        y - 1)
                .recipeContext(this);
    }

    private static void title(WidgetHolder widgets, String key, int y) {
        EmiRecipeWidgets.centeredText(widgets, Component.translatable(key), CENTER_X, y);
    }

    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    private enum Page {
        BUD_GROWTH("bud_growth"),
        BUD_LOOT("bud_loot"),
        CLUSTER_LOOT("cluster_loot"),
        DECAY("budding_overload_decay"),
        MOVING("budding_overload_moving"),
        ACCELERATION("budding_overload_acceleration");

        private final String id;

        Page(String id) {
            this.id = id;
        }
    }
}
