package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.client.hub.DeviceHubScreen;
import com.moakiee.ae2lt.menu.hub.DeviceHubHost;
import com.moakiee.ae2lt.network.hub.DeviceHubActionPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import com.moakiee.ae2lt.item.staff.*;
import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in isolated fixture: validates real menu packets and captures the rendered screen. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class MimicryStaffClientProbe {
    private static int ticks;
    private static int phase;
    private static boolean finished;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.staffClientProbe") || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null || ++ticks % 40 != 0) return;
        try {
            if (phase == 0) {
                mc.getSingleplayerServer().execute(() -> {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    player.setGameMode(GameType.SURVIVAL);
                    StaffPhaseService.tick(player);
                    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                        var existing = player.getInventory().getItem(slot);
                        if (StaffPhaseService.isProjection(existing)) StaffPhaseService.unlock(player, existing);
                    }
                    player.getInventory().clearContent();
                    player.getInventory().selected = 0;
                    var staff = StaffEnergy.charged(new ItemStack(ModItems.MIMICRY_STAFF.get()));
                    var modules = StaffState.modules(staff);
                    for (var module : StaffModule.values()) {
                        if (module != StaffModule.NETHERITE) modules.set(module.slot(), new ItemStack(ModItems.MIMICRY_MODULES.get(module).get()));
                    }
                    modules.set(10, new ItemStack(ModItems.RAILGUN_MODULE_CORE.get()));
                    modules.set(11, new ItemStack(ModItems.RAILGUN_MODULE_MULTIDIMENSIONAL_EXECUTION.get()));
                    StaffState.setModules(staff, modules);
                    player.getInventory().setItem(0, staff);
                    for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
                        player.serverLevel().setBlockAndUpdate(new BlockPos(x, 98, z), Blocks.STONE.defaultBlockState());
                    player.teleportTo(player.serverLevel(), .5, 99, .5, java.util.Set.of(), 180, 10);
                    DeviceHubHost.open(player, DeviceHubMenu.TAB_STAFF);
                });
            } else {
                require(mc.screen instanceof DeviceHubScreen, "Existing equipment hub must reach the client");
                var menu = ((DeviceHubScreen) mc.screen).getMenu();
                int speed = menu.getModuleNameKeys().indexOf("item.ae2lt." + StaffModule.SPEED.id());
                switch (phase) {
                    case 1 -> {
                        require(menu.getSelectedTab() == DeviceHubMenu.TAB_STAFF && menu.getModuleNameKeys().size() == 12,
                                "New staff tab synchronizes twelve installed modules");
                        PacketDistributor.sendToServer(new DeviceHubActionPacket(DeviceHubActionPacket.ACTION_SELECT_MODULE, speed));
                        PacketDistributor.sendToServer(new DeviceHubActionPacket(DeviceHubActionPacket.ACTION_TOGGLE_MODULE, speed));
                    }
                    case 2 -> {
                        require(menu.getSelectedModuleIndex() == speed && menu.getModuleEnabled().get(speed), "Existing module selection and toggle packets synchronize");
                        require(menu.getModuleConfigKeys().size() == 3, "Speed options use the old parameter list");
                        PacketDistributor.sendToServer(new DeviceHubActionPacket(DeviceHubActionPacket.ACTION_CYCLE_MODULE_CONFIG, 0));
                    }
                    case 3 -> {
                        require(menu.getModuleConfigValues().getFirst().equals("3 t"), "Base time edit synchronized from server");
                        Screenshot.grab(mc.gameDirectory, "mimicry-staff-hub-zh.png", mc.getMainRenderTarget(), message -> {});
                        mc.getSingleplayerServer().execute(() -> {
                            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                            player.closeContainer();
                            var staff = player.getMainHandItem();
                            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, staff);
                            DeviceHubHost.open(player, DeviceHubMenu.TAB_STAFF);
                        });
                    }
                    case 4 -> {
                        require(menu.getSelectedTab() == DeviceHubMenu.TAB_STAFF && menu.getModuleNameKeys().size() == 12,
                                "Offhand staff opens and synchronizes through the same equipment hub");
                        require(menu.getDeviceName().contains("拟态") || menu.getDeviceName().contains("Mimicry"), "Correct device name");
                        int damage = menu.getModuleNameKeys().indexOf("item.ae2lt." + StaffModule.DAMAGE.id());
                        PacketDistributor.sendToServer(new DeviceHubActionPacket(DeviceHubActionPacket.ACTION_SELECT_MODULE, damage));
                    }
                    case 5 -> {
                        require(menu.getModuleConfigKeys().subList(0, 2).equals(java.util.List.of("staff_combat", "staff_damage")), "Damage module exposes area and damage options");
                        for (int i = 0; i < 4; i++) {
                            PacketDistributor.sendToServer(new DeviceHubActionPacket(DeviceHubActionPacket.ACTION_CYCLE_MODULE_CONFIG, 0));
                            PacketDistributor.sendToServer(new DeviceHubActionPacket(DeviceHubActionPacket.ACTION_CYCLE_MODULE_CONFIG, 1));
                        }
                    }
                    case 6 -> {
                        require(menu.getModuleConfigValues().subList(0, 2).equals(java.util.List.of("ae2lt.staff.value.combat.4", "∞")), "Area 7x7 and unlocked infinity synchronize");
                        Screenshot.grab(mc.gameDirectory, "mimicry-staff-combat-zh.png", mc.getMainRenderTarget(), message -> {});
                        int execution = menu.getModuleNameKeys().indexOf(ModItems.RAILGUN_MODULE_MULTIDIMENSIONAL_EXECUTION.get().getDescriptionId());
                        PacketDistributor.sendToServer(new DeviceHubActionPacket(DeviceHubActionPacket.ACTION_SELECT_MODULE, execution));
                    }
                    case 7 -> {
                        require(menu.getModuleConfigKeys().getFirst().equals("staff_execution"), "Existing execution module exposes staff execution mode");
                        PacketDistributor.sendToServer(new DeviceHubActionPacket(DeviceHubActionPacket.ACTION_CYCLE_MODULE_CONFIG, 0));
                    }
                    case 8 -> {
                        require(menu.getModuleConfigValues().getFirst().equals("ae2lt.staff.value.execution.2"), "Forced execution mode synchronizes");
                        require(menu.getModuleConfigValues().subList(1, 3).equals(java.util.List.of("256", "1")), "Live lightning bill synchronizes");
                        require(!menu.isPowered(), "Unbound high-power staff reports combat resources unavailable");
                        require(!menu.getModuleConfigEditable().get(1) && !menu.getModuleConfigEditable().get(2), "Bill rows are read-only");
                        Screenshot.grab(mc.gameDirectory, "mimicry-staff-execution-zh.png", mc.getMainRenderTarget(), message -> {});
                        PacketDistributor.sendToServer(new DeviceHubActionPacket(DeviceHubActionPacket.ACTION_CYCLE_MODULE_CONFIG, menu.getModuleConfigKeys().size() - 1));
                    }
                    case 9 -> {
                        require(menu.getModuleConfigValues().getLast().equals("ae2lt.staff.value.phase_lock.true"), "Phase lock toggle synchronizes");
                        require(StaffPhaseService.isProjection(mc.player.getOffhandItem()), "Offhand synchronizes the dedicated projection");
                        require(!mc.player.getOffhandItem().has(com.moakiee.ae2lt.registry.ModDataComponents.MIMICRY_MODULES.get()), "Public client stack contains no real modules");
                        require(StaffEnergy.stored(mc.player.getOffhandItem()) == StaffEnergy.CAPACITY, "Client view retains energy prediction without a real buffer");
                        require(mc.screen.mouseScrolled(mc.screen.width / 2.0 + 45, mc.screen.height - 50, 0, -5), "Native settings scrollbar accepts wheel input");
                    }
                    case 10 -> {
                        Screenshot.grab(mc.gameDirectory, "mimicry-staff-phase-lock-zh.png", mc.getMainRenderTarget(), message -> {});
                        PacketDistributor.sendToServer(new DeviceHubActionPacket(DeviceHubActionPacket.ACTION_CYCLE_MODULE_CONFIG, menu.getModuleConfigKeys().size() - 1));
                    }
                    case 11 -> {
                        require(menu.getModuleConfigValues().getLast().equals("ae2lt.staff.value.phase_lock.false"), "Unlock remains available without FE or lightning");
                        System.out.println("MIMICRY_STAFF_CLIENT_PROBE_PASS: hub, modules, combat, lightning bill, phase lock/unlock and offhand sync");
                        finished = true;
                    }
                }
            }
            phase++;
        } catch (Throwable failure) {
            finished = true;
            System.err.println("MIMICRY_STAFF_CLIENT_PROBE_FAIL phase=" + phase);
            failure.printStackTrace();
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
