package com.moakiee.ae2lt.menu.railgun;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.item.railgun.RailgunModuleItem;
import com.moakiee.ae2lt.item.railgun.RailgunModuleType;
import com.moakiee.ae2lt.item.railgun.RailgunModules;

/**
 * Railgun module configuration menu. Backed by an in-memory module container
 * synchronized with the host's data component on every server tick.
 *
 * <p>Slot layout (7 slots):
 * <pre>
 *   Left column:     Right area:
 *   slot0 CORE        slot2 COMPUTE  slot3 COMPUTE
 *   slot1 ENERGY      slot4 ACCEL    slot5 ACCEL
 *   slot6 OVERLOAD_EXEC
 * </pre>
 */
public class RailgunMenu extends AEBaseMenu {

    public static final MenuType<RailgunMenu> TYPE = MenuTypeBuilder
            .create(RailgunMenu::new, RailgunHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LightningTech.MODID, "railgun"));

    private static final int CORE_X = 26, CORE_Y = 24;
    private static final int ENERGY_X = 26, ENERGY_Y = 50;
    private static final int COMPUTE_X1 = 80, COMPUTE_Y1 = 24;
    private static final int COMPUTE_X2 = 98, COMPUTE_Y2 = 24;
    private static final int ACCEL_X1 = 80, ACCEL_Y1 = 50;
    private static final int ACCEL_X2 = 98, ACCEL_Y2 = 50;
    private static final int OVERLOAD_EXEC_X = 26, OVERLOAD_EXEC_Y = 76;
    private static final int PLAYER_INV_X = 8, PLAYER_INV_Y = 125;
    private static final int HOTBAR_X = 8, HOTBAR_Y = 183;
    private static final int SLOT_SPACING = 18;

    private final RailgunHost host;
    private final ModuleContainer moduleContainer;

    public RailgunMenu(int id, Inventory playerInventory, RailgunHost host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        this.moduleContainer = new ModuleContainer();
        loadFromHost();

        // 0=CORE, 1=ENERGY, 2-3=COMPUTE, 4-5=ACCELERATION
        addSlot(new RailgunModuleSlot(moduleContainer, 0, CORE_X, CORE_Y, RailgunModuleType.CORE),
                SlotSemantics.MACHINE_INPUT);
        addSlot(new RailgunModuleSlot(moduleContainer, 1, ENERGY_X, ENERGY_Y, RailgunModuleType.ENERGY),
                SlotSemantics.MACHINE_INPUT);
        addSlot(new RailgunModuleSlot(moduleContainer, 2, COMPUTE_X1, COMPUTE_Y1, RailgunModuleType.COMPUTE),
                SlotSemantics.MACHINE_INPUT);
        addSlot(new RailgunModuleSlot(moduleContainer, 3, COMPUTE_X2, COMPUTE_Y2, RailgunModuleType.COMPUTE),
                SlotSemantics.MACHINE_INPUT);
        addSlot(new RailgunModuleSlot(moduleContainer, 4, ACCEL_X1, ACCEL_Y1, RailgunModuleType.ACCELERATION),
                SlotSemantics.MACHINE_INPUT);
        addSlot(new RailgunModuleSlot(moduleContainer, 5, ACCEL_X2, ACCEL_Y2, RailgunModuleType.ACCELERATION),
                SlotSemantics.MACHINE_INPUT);
        addSlot(new RailgunModuleSlot(moduleContainer, 6, OVERLOAD_EXEC_X, OVERLOAD_EXEC_Y, RailgunModuleType.OVERLOAD_EXECUTION),
                SlotSemantics.MACHINE_INPUT);

        addPlayerInventorySlots(playerInventory);
    }

    public RailgunHost host() {
        return host;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (isServerSide() && moduleContainer.dirty) {
            persistToHost();
            moduleContainer.dirty = false;
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (isServerSide() && moduleContainer.dirty) {
            persistToHost();
            moduleContainer.dirty = false;
        }
    }

    private void loadFromHost() {
        RailgunModules m = host.getModules();
        moduleContainer.setItem(0, m.core().copy());
        moduleContainer.setItem(1, m.energy().copy());
        List<ItemStack> compute = new ArrayList<>(m.compute());
        while (compute.size() < 2) compute.add(ItemStack.EMPTY);
        moduleContainer.setItem(2, compute.get(0).copy());
        moduleContainer.setItem(3, compute.get(1).copy());
        List<ItemStack> accel = new ArrayList<>(m.acceleration());
        while (accel.size() < 2) accel.add(ItemStack.EMPTY);
        moduleContainer.setItem(4, accel.get(0).copy());
        moduleContainer.setItem(5, accel.get(1).copy());
        moduleContainer.setItem(6, m.overloadExecution().copy());
        moduleContainer.dirty = false;
    }

    private void persistToHost() {
        ItemStack core = sanitize(moduleContainer.getItem(0), RailgunModuleType.CORE);
        ItemStack energy = sanitize(moduleContainer.getItem(1), RailgunModuleType.ENERGY);
        List<ItemStack> compute = Arrays.asList(
                sanitize(moduleContainer.getItem(2), RailgunModuleType.COMPUTE),
                sanitize(moduleContainer.getItem(3), RailgunModuleType.COMPUTE));
        List<ItemStack> accel = Arrays.asList(
                sanitize(moduleContainer.getItem(4), RailgunModuleType.ACCELERATION),
                sanitize(moduleContainer.getItem(5), RailgunModuleType.ACCELERATION));
        host.setModules(new RailgunModules(core, compute, accel, energy, sanitize(moduleContainer.getItem(6), RailgunModuleType.OVERLOAD_EXECUTION)));
    }

    private static ItemStack sanitize(ItemStack stack, RailgunModuleType expected) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (stack.getItem() instanceof RailgunModuleItem m && m.moduleType() == expected) {
            return stack.copy();
        }
        return ItemStack.EMPTY;
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                                PLAYER_INV_X + col * SLOT_SPACING,
                                PLAYER_INV_Y + row * SLOT_SPACING),
                        SlotSemantics.PLAYER_INVENTORY);
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col,
                            HOTBAR_X + col * SLOT_SPACING, HOTBAR_Y),
                    SlotSemantics.PLAYER_HOTBAR);
        }
    }

    /** Per-slot type-restricted module slot. */
    private static final class RailgunModuleSlot extends Slot {
        private final RailgunModuleType type;

        RailgunModuleSlot(ModuleContainer container, int index, int x, int y, RailgunModuleType type) {
            super(container, index, x, y);
            this.type = type;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty()) return true;
            return stack.getItem() instanceof RailgunModuleItem m && m.moduleType() == type;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Tiny Container backing the six module slots. */
    private static final class ModuleContainer extends SimpleContainer {
        boolean dirty = false;

        ModuleContainer() {
            super(7);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            dirty = true;
        }
    }
}
