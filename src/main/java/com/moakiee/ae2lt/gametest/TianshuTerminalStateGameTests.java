package com.moakiee.ae2lt.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;

import de.mari_023.ae2wtlib.AE2wtlib;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.wut.WUTHandler;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.integration.ae2wtlib.Ae2wtlibIntegration;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWTMenuHost;
import com.moakiee.ae2lt.integration.ae2wtlib.WirelessTerminalFrequencyLink;
import com.moakiee.ae2lt.integration.ae2wtlib.WirelessTerminalFrequencyLink.RouteKind;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopDraftSync;
import com.moakiee.ae2lt.logic.tianshu.terminal.ClosedLoopTerminalDraft;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternEncodingType;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternTerminalDraft;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternTerminalHost;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWirelessPatternEncodingTermMenuHost;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardData;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.part.TianshuPatternEncodingTerminalPart;
import com.moakiee.ae2lt.registry.ModItems;

/** Exercises real hosts and their parent inventories, not source-code spelling. */
@GameTestHolder(AE2LightningTech.MODID)
@PrefixGameTestTemplate(false)
public final class TianshuTerminalStateGameTests {
    private TianshuTerminalStateGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void wiredHostPreservesLegacyState(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, AEBlocks.CABLE_BUS.block());
        var bus = (CableBusBlockEntity) helper.getBlockEntity(BlockPos.ZERO);
        var part = new TianshuPatternEncodingTerminalPart(ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
        part.setPartHostInfo(Direction.NORTH, bus, bus);
        populate(part);
        part.getLogic().setSubstitution(true);

        var saved = new CompoundTag();
        part.writeToNBT(saved);
        helper.assertTrue(saved.getString("TianshuEncodingMode").equals("CLOSED_LOOP"),
                "Wired terminals must retain their original root NBT keys");
        helper.assertFalse(saved.contains("patternEncodingLogic"), "Do not migrate wired state into item NBT");

        var restored = new TianshuPatternEncodingTerminalPart(ModItems.TIANSHU_PATTERN_ENCODING_TERMINAL.get());
        restored.setPartHostInfo(Direction.SOUTH, bus, bus);
        restored.readFromNBT(saved.copy());
        assertPopulated(helper, restored);
        helper.assertTrue(restored.getLogic().isSubstitution(), "Native pattern settings must survive reload");
        helper.assertTrue(restored.getLogic().getBlankPatternInv().getSlotLimit(0) == 0,
                "Physical blank-pattern storage must remain disabled");
        restored.setClosedLoopTerminalDraft(null);
        restored.setProcessingPatternTerminalDraft(null);
        restored.writeToNBT(saved);
        helper.assertFalse(saved.contains("ClosedLoopDraft"), "Cleared closed-loop drafts must be removed");
        helper.assertFalse(saved.contains("ProcessingDraft"), "Cleared processing drafts must be removed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void wirelessHostsFollowOptionalDependency(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ae2wtlib")) {
            var id = new ResourceLocation(AE2LightningTech.MODID, "wireless_tianshu_pattern_encoding_terminal");
            helper.assertFalse(ForgeRegistries.ITEMS.containsKey(id),
                    "The wireless item must stay absent without AE2WTLib");
            helper.assertFalse(ForgeRegistries.MENU_TYPES.containsKey(id),
                    "The wireless menu must stay absent without AE2WTLib");
        } else {
            WirelessHosts.verify(helper);
        }
        helper.succeed();
    }

    @SuppressWarnings("deprecation")
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void legacyHostRetainsNestedViewCellStorage(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "LegacyTerminal"));
        var stack = AEItems.WIRELESS_TERMINAL.stack();
        player.getInventory().setItem(0, stack);
        var host = new TianshuWirelessPatternEncodingTermMenuHost(player, 0, stack, (p, menu) -> { });
        host.getViewCellStorage().setItemDirect(0, AEItems.VIEW_CELL.stack());
        populate(host);
        var restored = new TianshuWirelessPatternEncodingTermMenuHost(player, 0, stack, (p, menu) -> { });
        assertPopulated(helper, restored);
        helper.assertTrue(restored.getViewCellStorage().getStackInSlot(0).is(AEItems.VIEW_CELL.asItem()),
                "The compatibility host must still restore its own view cells");
        helper.assertTrue(stack.getOrCreateTagElement("patternEncodingLogic").contains("viewcells"),
                "Legacy view cells must retain their nested location");
        helper.assertFalse(stack.getOrCreateTag().contains("viewcells"),
                "Do not migrate legacy view cells to AE2WTLib's root location");
        player.getInventory().clearContent();
        helper.succeed();
    }

    private static void populate(TianshuPatternTerminalHost host) {
        host.setTianshuEncodingMode(TianshuEncodingMode.CLOSED_LOOP);
        host.setMaintainableView(true);
        host.setClosedLoopTerminalDraft(closedLoopDraft());
        host.setProcessingPatternTerminalDraft(processingDraft());
    }

    private static void assertPopulated(GameTestHelper helper, TianshuPatternTerminalHost host) {
        helper.assertTrue(host.getTianshuEncodingMode() == TianshuEncodingMode.CLOSED_LOOP, "Mode must survive reload");
        helper.assertTrue(host.isMaintainableView(), "View selection must survive reload");
        helper.assertTrue(ClosedLoopTerminalDraft.sameState(closedLoopDraft(), host.getClosedLoopTerminalDraft()),
                "Closed-loop stacks, roles and multipliers must survive reload");
        helper.assertTrue(ProcessingPatternTerminalDraft.sameState(processingDraft(), host.getProcessingPatternTerminalDraft()),
                "Processing configuration must survive reload");
    }

    private static ClosedLoopTerminalDraft closedLoopDraft() {
        var members = new ArrayList<>(Collections.nCopies(ClosedLoopDraftSync.MEMBER_SLOTS, ItemStack.EMPTY));
        members.set(2, new ItemStack(Items.STONE, 3));
        var copies = new ArrayList<>(Collections.nCopies(ClosedLoopDraftSync.MEMBER_SLOTS, 0L));
        copies.set(2, 5L);
        var outputs = new ArrayList<>(Collections.nCopies(ClosedLoopDraftSync.OUTPUT_SLOTS, ItemStack.EMPTY));
        outputs.set(1, new ItemStack(Items.COBBLESTONE, 7));
        var roles = new ArrayList<>(Collections.nCopies(ClosedLoopDraftSync.OUTPUT_SLOTS, 0));
        roles.set(1, 2);
        return new ClosedLoopTerminalDraft(new ItemStack(Items.PAPER), members, copies, outputs, roles, 3, 4, true);
    }

    private static ProcessingPatternTerminalDraft processingDraft() {
        return ProcessingPatternTerminalDraft.configured(
                Collections.nCopies(3, null), Collections.nCopies(2, null),
                new ProcessingPatternEncodingType.AdvancedConfig(new int[] {2, 0, 6}),
                new ProcessingPatternEncodingType.OverloadConfig(new int[] {2}, new int[] {1}));
    }

    /** Keep optional library classes out of the no-AE2WTLib test's loading path. */
    private static final class WirelessHosts {
        private static void verify(GameTestHelper helper) {
            var item = Ae2wtlibIntegration.terminal();
            helper.assertTrue(item == ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get(),
                    "Item registration and the terminal definition must share an instance");
            var name = Ae2wtlibIntegration.TIANSHU_TERMINAL_NAME;
            helper.assertTrue(WUTHandler.wirelessTerminals.get(name).item() == item,
                    "WUT must use the registered item instance");
            var universal = new ItemStack(AE2wtlib.UNIVERSAL_TERMINAL);
            for (var installed : WUTHandler.terminalNames) {
                universal.getOrCreateTag().putBoolean(installed, true);
            }
            universal.getOrCreateTag().putString("currentTerminal", name);
            for (var stack : List.of(new ItemStack(item), universal)) {
                verifyStack(helper, stack, name);
            }
        }

        private static void verifyStack(GameTestHelper helper, ItemStack stack, String terminalName) {
            var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TerminalState"));
            player.getInventory().setItem(0, stack);
            var host = new TianshuWTMenuHost(player, 0, stack, (p, menu) -> { });
            host.getViewCellStorage().setItemDirect(0, AEItems.VIEW_CELL.stack());
            host.getSubInventory(WTMenuHost.INV_SINGULARITY)
                    .setItemDirect(0, AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack());
            host.getLogic().setSubstitution(true);
            populate(host);
            var data = stack.getOrCreateTagElement("patternEncodingLogic");
            data.putString("foreignState", "preserve");
            helper.assertTrue(data.getString("tianshuMode").equals("CLOSED_LOOP"),
                    "Wireless setters must save immediately using legacy keys");
            helper.assertFalse(stack.getOrCreateTag().contains("TianshuEncodingMode"),
                    "Wireless state must not use the part's root keys");

            // A nullable slot is the supported non-inventory/Curios constructor path.
            var restored = new TianshuWTMenuHost(player, null, stack, (p, menu) -> { });
            assertPopulated(helper, restored);
            helper.assertTrue(restored.getLogic().isSubstitution(), "Native pattern settings must survive reload");
            helper.assertTrue(restored.getLogic().getBlankPatternInv().getSlotLimit(0) == 0,
                    "Physical blank-pattern storage must remain disabled");
            helper.assertTrue(restored.getViewCellStorage().getStackInSlot(0).is(AEItems.VIEW_CELL.asItem()),
                    "AE2WTLib must retain ownership of view-cell persistence");
            helper.assertTrue(restored.getSubInventory(WTMenuHost.INV_SINGULARITY)
                            .getStackInSlot(0).is(AEItems.QUANTUM_ENTANGLED_SINGULARITY.asItem()),
                    "AE2WTLib must retain ownership of singularity persistence");
            restored.setClosedLoopTerminalDraft(null);
            restored.setProcessingPatternTerminalDraft(null);
            helper.assertFalse(data.contains("tianshuClosedLoopDraft"), "Cleared wireless drafts must be removed");
            helper.assertFalse(data.contains("tianshuProcessingDraft"), "Cleared processing drafts must be removed");
            helper.assertTrue(data.getString("foreignState").equals("preserve"), "Unrelated NBT must be preserved");
            helper.assertTrue(WUTHandler.getCurrentTerminal(stack).equals(terminalName),
                    "Saving state must not change WUT terminal selection");
            helper.assertTrue(restored.getMainMenuIcon().is(ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get()),
                    "The main menu must retain the Tianshu terminal icon");
            verifyFrequencyInventory(helper, player, restored, stack);
            player.getInventory().clearContent();
        }

        private static void verifyFrequencyInventory(GameTestHelper helper, Player player,
                TianshuWTMenuHost host, ItemStack stack) {
            var noCard = WirelessTerminalFrequencyLink.resolveRoute(player, stack);
            helper.assertTrue(noCard.kind() == RouteKind.NO_FREQUENCY,
                    "An empty upgrade inventory must defer to native connections");
            var upgrades = host.getUpgrades();
            var unbound = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
            upgrades.setItemDirect(0, unbound);
            helper.assertFalse(WirelessTerminalFrequencyLink.resolveRoute(player, stack)
                    .usesFrequencyRoute(), "An unbound card must defer to native connections");

            // Historical inventory fixture: an unbound card precedes a bound card in a later slot.
            // Direct writes intentionally bypass the normal one-card installation limit.
            int lastSlot = upgrades.size() - 1;
            if (host.isUniversalWirelessTerminal()) {
                helper.assertTrue(lastSlot >= 2, "The multi-terminal WUT must exercise slots beyond the vanilla two-slot view");
            } else {
                helper.assertTrue(upgrades.size() == 2, "Standalone terminals must retain their native two-slot host view");
            }
            var bound = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
            OverloadedFrequencyCardItem.setData(bound, OverloadedFrequencyCardData.empty()
                    .bindFrequency(Integer.MAX_VALUE, null, 0, UUID.randomUUID()).withAutoConnect(false));
            upgrades.setItemDirect(lastSlot, bound);
            var itemRoute = WirelessTerminalFrequencyLink.resolveRoute(player, stack);
            var hostRoute = WirelessTerminalFrequencyLink.resolveRoute(player, upgrades);
            helper.assertTrue(itemRoute.kind() == RouteKind.UNAVAILABLE,
                    "Resolve the first bound card across all host slots, without adding owner/auto-connect gates");
            helper.assertTrue(itemRoute.kind() == hostRoute.kind(), "Item and host views must resolve the same route");
            helper.assertFalse(itemRoute.usesFrequencyRoute(), "An unavailable frequency must retain native fallback");
        }
    }
}
