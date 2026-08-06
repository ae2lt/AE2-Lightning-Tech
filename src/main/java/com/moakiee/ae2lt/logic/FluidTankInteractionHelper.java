package com.moakiee.ae2lt.logic;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * 玩家 GUI 菜单上"流体槽 ↔ 手持容器"的通用交互:
 * <ul>
 *   <li>右键(insert): 把容器内的流体倒入 tank;</li>
 *   <li>左键(extract): 从 tank 抽出流体填入容器;</li>
 *   <li>shift+任意键(clear): 清空 tank。</li>
 * </ul>
 *
 * <p>容器来源按优先级:光标携带(carried)→ 玩家主手选中的热键栏槽位。
 * 这样既支持 AE2 风格的"先 pickup 到光标再点击",也支持 Adv AE 风格的"直接拿着桶点击"。</p>
 */
public final class FluidTankInteractionHelper {
    private FluidTankInteractionHelper() {
    }

    /** 右键:把容器里的流体倒入 tank。成功返回 true。 */
    public static boolean insertFromCarried(Player player, FluidTank tank) {
        return interactSource(player, source -> tryEmptyOne(player, tank, source));
    }

    /** 左键:从 tank 抽出流体到容器。成功返回 true。 */
    public static boolean extractToCarried(Player player, IFluidHandler tankHandler) {
        return interactSource(player, source -> tryFillOne(player, tankHandler, source));
    }

    /** shift+click:直接清空 tank;调用方负责后续 setChanged / notify。 */
    public static void clear(FluidTank tank) {
        tank.setFluid(FluidStack.EMPTY);
    }

    /**
     * 选择一个容器来源执行 action,成功则回写(carried / hotbar)。
     * 光标非空优先走 carried;否则退化到玩家主手选中的热键栏槽位(含副手的选中槽不参与)。
     */
    private static boolean interactSource(Player player, java.util.function.Function<ItemStack, ItemStack> action) {
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            ItemStack result = action.apply(carried.copyWithCount(1));
            if (result == null) {
                return false;
            }
            consumeOneFromCarried(player);
            stashIntoCarriedOrInventory(player, result);
            return true;
        }

        int selected = player.getInventory().selected;
        if (selected < 0 || selected >= player.getInventory().items.size()) {
            return false;
        }
        ItemStack hotbar = player.getInventory().items.get(selected);
        if (hotbar.isEmpty()) {
            return false;
        }

        ItemStack result = action.apply(hotbar.copyWithCount(1));
        if (result == null) {
            return false;
        }
        // 消耗主手 1 个,尝试把结果放回同一槽位(同类型则直接增量);放不下就 stash 到背包 / 光标。
        hotbar.shrink(1);
        if (hotbar.isEmpty()) {
            player.getInventory().items.set(selected, ItemStack.EMPTY);
        }
        if (!result.isEmpty()) {
            ItemStack slotStack = player.getInventory().items.get(selected);
            if (slotStack.isEmpty()) {
                player.getInventory().items.set(selected, result);
            } else if (ItemStack.isSameItemSameComponents(slotStack, result)
                    && slotStack.getCount() + result.getCount() <= slotStack.getMaxStackSize()) {
                slotStack.grow(result.getCount());
            } else if (!player.getInventory().add(result)) {
                player.drop(result, false);
            }
        }
        return true;
    }

    /** 尝试从单个容器把流体倒入 tank;成功返回执行后的容器 stack,失败返回 null。 */
    private static ItemStack tryEmptyOne(Player player, FluidTank tank, ItemStack singleUnit) {
        var result = FluidUtil.tryEmptyContainer(singleUnit, tank, Integer.MAX_VALUE, player, true);
        return result.isSuccess() ? result.getResult() : null;
    }

    /** 尝试从 tank 抽流体填入单个容器;成功返回执行后的容器 stack,失败返回 null。 */
    private static ItemStack tryFillOne(Player player, IFluidHandler tankHandler, ItemStack singleUnit) {
        var result = FluidUtil.tryFillContainer(singleUnit, tankHandler, Integer.MAX_VALUE, player, true);
        return result.isSuccess() ? result.getResult() : null;
    }

    private static void consumeOneFromCarried(Player player) {
        ItemStack carried = player.containerMenu.getCarried();
        carried.shrink(1);
        player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
    }

    /**
     * 优先把 result 叠回光标(同 item + 同组件才行);否则塞玩家背包,
     * 塞不下就丢到玩家脚下。避免出现"桶卡在 carried 和 inventory 之间消失"的情况。
     */
    private static void stashIntoCarriedOrInventory(Player player, ItemStack result) {
        if (result.isEmpty()) {
            return;
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            player.containerMenu.setCarried(result);
            return;
        }

        if (ItemStack.isSameItemSameComponents(carried, result)
                && carried.getCount() + result.getCount() <= carried.getMaxStackSize()) {
            carried.grow(result.getCount());
            player.containerMenu.setCarried(carried);
            return;
        }

        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
    }
}
