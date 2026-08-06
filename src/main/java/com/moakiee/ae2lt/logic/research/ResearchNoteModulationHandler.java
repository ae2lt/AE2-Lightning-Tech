package com.moakiee.ae2lt.logic.research;

import com.moakiee.ae2lt.registry.ModItems;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.AnvilUpdateEvent;

/**
 * 铁砧调制:空白研究笔记(左槽) + 催化物(右槽) -> 同数量空白笔记但 NBT 里写入
 * {@link ResearchNoteData#TAG_FORCED_GOAL}。仅消耗 1 个催化物(不论左槽叠数),
 * 不消耗经验。已生成笔记不接受调制。
 */
public final class ResearchNoteModulationHandler {

    public ResearchNoteModulationHandler() {
    }

    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }
        if (!left.is(ModItems.RESEARCH_NOTE.get())) {
            return;
        }
        if (!ResearchNoteData.isBlank(left)) {
            return;
        }
        // 只接受单张空白笔记调制,避免一催化物一次性锁定整叠。
        if (left.getCount() != 1) {
            return;
        }

        RitualGoal goal = NoteModulationCatalysts.findGoal(right).orElse(null);
        if (goal == null) {
            return;
        }

        RitualGoal existing = ResearchNoteData.readForcedGoal(left);
        if (existing == goal) {
            return;
        }

        ItemStack output = left.copyWithCount(1);
        ResearchNoteData.writeForcedGoal(output, goal);

        event.setOutput(output);
        event.setMaterialCost(1);
        // cost 必须 > 0,否则 AnvilMenu 在非创造模式下会把结果槽锁死;
        // 同时 cost 不能 >= 40,否则会触发 "Too Expensive!" 同样锁死。
        // 取 39 作为"最大可能值",既保证取得出又让调制成本足够显著。
        event.setCost(39);
    }
}

