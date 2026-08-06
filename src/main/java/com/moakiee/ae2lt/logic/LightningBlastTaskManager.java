package com.moakiee.ae2lt.logic;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class LightningBlastTaskManager {
    private static final List<LightningBlastTask> ACTIVE_TASKS = new ArrayList<>();

    private LightningBlastTaskManager() {
    }

    public static void schedule(LightningBlastTask task) {
        if (task.isCompleted()) {
            return;
        }
        ACTIVE_TASKS.add(task);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_TASKS.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE_TASKS.isEmpty()) {
            return;
        }

        int remainingBlockBudget = AE2LTCommonConfig.overloadTntGlobalBlockBudgetPerTick();
        int remainingLightningBudget = AE2LTCommonConfig.overloadTntGlobalLightningBudgetPerTick();
        Iterator<LightningBlastTask> iterator = ACTIVE_TASKS.iterator();
        int remainingTasks = ACTIVE_TASKS.size();
        while (iterator.hasNext()) {
            LightningBlastTask task = iterator.next();
            int blockShare = remainingBlockBudget <= 0
                    ? 0
                    : (remainingTasks > 0 ? Math.max(1, remainingBlockBudget / remainingTasks) : remainingBlockBudget);
            int lightningShare = remainingTasks > 0
                    ? Math.max(0, remainingLightningBudget / remainingTasks)
                    : remainingLightningBudget;

            var tickResult = task.tick(blockShare, lightningShare);
            remainingBlockBudget = Math.max(0, remainingBlockBudget - tickResult.consumedBlocks());
            remainingLightningBudget = Math.max(0, remainingLightningBudget - tickResult.consumedLightning());
            remainingTasks--;

            if (task.isCompleted()) {
                iterator.remove();
            }
        }
    }
}
