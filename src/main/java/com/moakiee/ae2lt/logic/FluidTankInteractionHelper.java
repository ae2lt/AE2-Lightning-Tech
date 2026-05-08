package com.moakiee.ae2lt.logic;

import com.moakiee.ae2lt.machine.overloadfactory.NotifyingFluidTank;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

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
    public static boolean insertFromCarried(Player player, ResourceHandler<FluidResource> tank) {
        return interactSource(player, itemAccess -> {
            var itemHandler = itemAccess.getCapability(Capabilities.Fluid.ITEM);
            return itemHandler != null && moveFluid(itemHandler, tank, player, false);
        });
    }

    /** 左键:从 tank 抽出流体到容器。成功返回 true。 */
    public static boolean extractToCarried(Player player, ResourceHandler<FluidResource> tank) {
        return interactSource(player, itemAccess -> {
            var itemHandler = itemAccess.getCapability(Capabilities.Fluid.ITEM);
            return itemHandler != null && moveFluid(tank, itemHandler, player, true);
        });
    }

    /** shift+click:直接清空 tank;调用方负责后续 setChanged / notify。 */
    public static void clear(NotifyingFluidTank tank) {
        tank.setFluid(FluidStack.EMPTY);
    }

    /**
     * 选择一个容器来源执行 action,成功则回写(carried / hotbar)。
     * 光标非空优先走 carried;否则退化到玩家主手选中的热键栏槽位(含副手的选中槽不参与)。
     */
    private static boolean interactSource(Player player, java.util.function.Function<ItemAccess, Boolean> action) {
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            return action.apply(ItemAccess.forPlayerCursor(player, player.containerMenu).oneByOne());
        }

        var inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (selected < 0 || selected >= Inventory.getSelectionSize()) {
            return false;
        }
        ItemStack hotbar = inventory.getItem(selected);
        if (hotbar.isEmpty()) {
            return false;
        }

        return action.apply(ItemAccess.forPlayerSlot(player, selected).oneByOne());
    }

    private static boolean moveFluid(
            ResourceHandler<FluidResource> from,
            ResourceHandler<FluidResource> to,
            Player player,
            boolean pickup) {
        try (var transaction = Transaction.openRoot()) {
            var moved = ResourceHandlerUtil.moveFirst(from, to, resource -> true, Integer.MAX_VALUE, transaction);
            if (moved == null) {
                return false;
            }
            transaction.commit();
            net.neoforged.neoforge.transfer.fluid.FluidUtil.triggerSoundAndGameEvent(
                    moved.resource(),
                    player.level(),
                    player.position(),
                    player,
                    pickup);
            return true;
        }
    }
}
