package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.integration.useless.UselessModCompat;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.part.TianshuPatternEncodingTerminalPart;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Manual visual probe for the isolated development client only. */
@EventBusSubscriber(modid = "ae2lt")
public final class OmniversalTerminalClientProbe {
    private static ServerPlayer pendingPlayer;
    private static TianshuPatternEncodingTerminalPart pendingTerminal;
    private static int delay;

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (!UselessModCompat.isLoaded()) return;
        event.getDispatcher().register(Commands.literal("ae2lt_omni_probe")
                .requires(source -> source.hasPermission(2))
                .executes(context -> setup(context.getSource().getPlayerOrException())));
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (Boolean.getBoolean("ae2lt.omniversalClientProbe")
                && UselessModCompat.isLoaded() && event.getEntity() instanceof ServerPlayer player) setup(player);
    }

    public static int setup(ServerPlayer player) {
        return Native.setup(player);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (pendingPlayer == null || --delay > 0) return;
        var player = pendingPlayer;
        var terminal = pendingTerminal;
        pendingPlayer = null;
        pendingTerminal = null;
        Native.openMenu(player, terminal);
    }

    /** Keeps optional classes out of methods inspected by the event-bus scanner. */
    private static final class Native {
        private static int setup(ServerPlayer player) {
            var level = player.serverLevel();
            BlockPos cable = player.blockPosition().offset(2, 0, 0);
            PartHelper.setPart(level, cable, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
            var terminal = PartHelper.setPart(level, cable, Direction.SOUTH, player,
                    ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
            level.setBlockAndUpdate(cable.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            level.setBlockAndUpdate(cable.west(), AEBlocks.DRIVE.block().defaultBlockState());
            var drive = (DriveBlockEntity) level.getBlockEntity(cable.west());
            drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
            level.setBlockAndUpdate(cable.east(),
                    com.sorrowmist.useless.init.ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get().defaultBlockState());
            pendingPlayer = player;
            pendingTerminal = terminal;
            delay = 40;
            return 1;
        }

        private static void openMenu(ServerPlayer player, TianshuPatternEncodingTerminalPart terminal) {
            var grid = terminal.getActionableNode().getGrid();
            grid.getStorageService().getInventory().insert(AEItemKey.of(AEItems.BLANK_PATTERN),
                    64, Actionable.MODULATE, IActionSource.empty());
            var choice = com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog.entries(player.level()).stream()
                    .filter(recipe -> recipe.identity().recipeId().toString().equals("useless_mod:advanced_alloy/gear/useless_gear_tier_1"))
                    .findFirst().orElseThrow();
            MenuOpener.open(TianshuPatternEncodingTermMenu.TYPE, player, MenuLocators.forPart(terminal));
            if (player.containerMenu instanceof TianshuPatternEncodingTermMenu menu) {
                menu.selectOmniversalPattern(UselessModCompat.encodeViewerRecipe(choice, player.level()));
            }
        }
    }
}
