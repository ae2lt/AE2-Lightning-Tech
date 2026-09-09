package com.moakiee.ae2lt.item.staff;

import java.util.List;
import java.util.ArrayList;
import net.minecraft.server.level.ServerPlayer;
import com.moakiee.ae2lt.item.railgun.RailgunModuleItem;
import com.moakiee.ae2lt.item.railgun.RailgunModuleType;
import com.moakiee.ae2lt.menu.hub.DeviceStatusModel;
import com.moakiee.ae2lt.menu.hub.DeviceStatusModel.ModuleConfigInfo;
import com.moakiee.ae2lt.menu.hub.DeviceStatusModel.ModuleInfo;
import com.moakiee.ae2lt.item.railgun.RailgunExecutionMode;
import com.moakiee.ae2lt.registry.ModDataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class StaffHubSettings {
    private StaffHubSettings() {}

    public static List<ItemStack> installed(ItemStack staff) {
        return StaffModuleStorage.INSTANCE.listEntries(StaffPhaseService.resolve(staff));
    }

    public static int toggleButton(ItemStack module) {
        if (!(module.getItem() instanceof StaffModuleItem item)) return -1;
        return switch (item.module()) { case SPEED -> 5; case SMELTING -> 6; case COLLECTION -> 7; default -> -1; };
    }

    public static boolean toggleableName(String key) {
        return key.equals("item.ae2lt." + StaffModule.SPEED.id()) || key.equals("item.ae2lt." + StaffModule.SMELTING.id())
                || key.equals("item.ae2lt." + StaffModule.COLLECTION.id());
    }

    public static List<Integer> configButtons(ItemStack module) {
        if (module.getItem() instanceof RailgunModuleItem item) {
            return item.moduleType() == RailgunModuleType.CORE ? List.of() : List.of(10);
        }
        if (!(module.getItem() instanceof StaffModuleItem item)) return List.of();
        return switch (item.module()) {
            case MATTOCK -> List.of(0);
            case WRENCH -> List.of(1);
            case HARVEST -> List.of(2);
            case SPEED -> List.of(3, 4);
            case DAMAGE -> List.of(8, 9);
            default -> List.of();
        };
    }

    public static boolean cycle(ItemStack staff, Player player, int button) {
        if (button == 11) {
            return player instanceof ServerPlayer serverPlayer && StaffPhaseService.toggle(serverPlayer, staff);
        }
        staff = StaffPhaseService.resolve(staff);
        if (staff.isEmpty()) return false;
        if (button < 0 || button > 10) return false;
        StaffModule required = switch (button) {
            case 0 -> StaffModule.MATTOCK; case 1 -> StaffModule.WRENCH; case 2 -> StaffModule.HARVEST;
            case 3, 4, 5 -> StaffModule.SPEED; case 6 -> StaffModule.SMELTING; case 7 -> StaffModule.COLLECTION; case 8, 9 -> StaffModule.DAMAGE; default -> null;
        };
        if (required != null && !StaffState.has(staff, required)) return false;
        if (button == 10 && !StaffCombat.hasExecution(staff)) return false;
        var proposed = staff.copy();
        var next = StaffState.settings(staff).cycle(button);
        if (button == 9) {
            while (!StaffCombat.damageUnlocked(staff, next.damage())) next = next.cycle(9);
        }
        proposed.set(ModDataComponents.MIMICRY_SETTINGS.get(), next);
        if (!StaffEnchantments.compatible(StaffEnchantments.proposed(proposed, player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)))) {
            player.displayClientMessage(Component.translatable("ae2lt.staff.enchantment_conflict"), true);
            return false;
        }
        staff.set(ModDataComponents.MIMICRY_SETTINGS.get(), StaffState.settings(proposed));
        player.getInventory().setChanged();
        return true;
    }

    public static DeviceStatusModel snapshot(ItemStack staff, ServerPlayer player, int selected) {
        boolean locked = PhaseItemProtection.isLockedStaff(staff);
        staff = StaffPhaseService.resolve(staff);
        if (staff.isEmpty()) return DeviceStatusModel.EMPTY;
        var settings = StaffState.settings(staff);
        var installed = installed(staff);
        var modules = installed.stream().map(module -> new ModuleInfo(module.getDescriptionId(), 1,
                module.getItem() instanceof StaffModuleItem item ? switch (item.module()) {
                    case SPEED -> settings.speed(); case SMELTING -> settings.smelting();
                    case COLLECTION -> settings.collection(); default -> true;
                } : true)).toList();
        int index = installed.isEmpty() ? -1 : Math.clamp(selected, 0, installed.size() - 1);
        var configs = new ArrayList<ModuleConfigInfo>();
        if (index >= 0) {
            var selectedModule = installed.get(index);
            for (int button : configButtons(selectedModule)) configs.add(config(staff, settings, button));
            if (selectedModule.getItem() instanceof StaffModuleItem item && item.module() == StaffModule.DAMAGE
                    || selectedModule.getItem() instanceof RailgunModuleItem) {
                var cost = StaffLightning.cost(staff, true);
                configs.add(new ModuleConfigInfo("staff_lightning_ehv", "", Long.toString(cost.ehv()), false));
                configs.add(new ModuleConfigInfo("staff_lightning_hv", "", Long.toString(cost.hv()), false));
            }
        }
        configs.add(new ModuleConfigInfo("staff_phase_lock", "",
                "ae2lt.staff.value.phase_lock." + locked, true));
        boolean powered = StaffEnergy.canUse(staff) && StaffLightning.canPay(player, staff, true);
        return new DeviceStatusModel(staff.getHoverName().getString(), true, powered, modules,
                index, configs, false, false, false, false, RailgunExecutionMode.NORMAL, false);
    }

    private static ModuleConfigInfo config(ItemStack staff, StaffSettings settings, int button) {
        String key = switch (button) { case 0 -> "land"; case 1 -> "mek"; case 2 -> "harvest";
            case 3 -> "base"; case 8 -> "combat"; case 9 -> "damage"; case 10 -> "execution"; default -> "scale"; };
        String value = switch (button) {
            case 0 -> "ae2lt.staff.value.land." + settings.land();
            case 1 -> "ae2lt.staff.value.mek." + settings.mekanism();
            case 2 -> "ae2lt.staff.value.harvest." + settings.harvest();
            case 3 -> settings.baseTicks() + " t";
            case 8 -> "ae2lt.staff.value.combat." + settings.combat();
            case 9 -> StaffCombat.infinite(staff) ? "∞" : Integer.toString(StaffCombat.baseDamage(staff));
            case 10 -> "ae2lt.staff.value.execution." + settings.execution();
            default -> "×" + settings.timeScale();
        };
        return new ModuleConfigInfo("staff_" + key, "", value, true);
    }
}
