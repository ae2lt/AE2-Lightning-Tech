package com.moakiee.ae2lt.menu;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.menu.locator.ItemMenuHostLocator;

import com.moakiee.ae2lt.api.frequency.FrequencyBindingHost;
import com.moakiee.ae2lt.blockentity.WirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardData;
import com.moakiee.ae2lt.item.TerminalCardAccess;
import com.moakiee.ae2lt.network.SyncFrequencyDetailPacket;
import com.moakiee.ae2lt.network.SyncFrequencyListPacket;

/**
 * Shared menu for wireless controllers and receiver-style frequency-bound devices.
 * Carries the block entity position and device type to the client; the
 * currently bound frequency id is auto-synced via a {@link DataSlot}.
 */
public class FrequencyMenu extends AbstractContainerMenu {

    public static final MenuType<FrequencyMenu> TYPE = IMenuTypeExtension.create(FrequencyMenu::clientCreate);

    private final BlockPos blockPos;
    private final boolean isController;
    private final boolean isAdvanced;
    private final String deviceName;

    @Nullable
    private final BlockEntity backingBlockEntity;

    /**
     * Card mode: the menu targets an overloaded frequency card installed in a
     * wireless terminal's upgrade slot (located via {@link #terminalLocator})
     * instead of a block entity. The whole frequency screen and the registry /
     * sync packets are reused; only open + select are routed differently.
     */
    private final boolean cardMode;
    @Nullable
    private final ItemMenuHostLocator terminalLocator;
    @Nullable
    private final ServerPlayer cardPlayer;

    private final DataSlot freqIdSlot = DataSlot.standalone();
    private final DataSlot linkActiveSlot = DataSlot.standalone();
    private final DataSlot usedChannelsSlot = DataSlot.standalone();
    private final DataSlot maxChannelsSlot = DataSlot.standalone();
    private final DataSlot autoConnectSlot = DataSlot.standalone();

    // channel counts iterate all grid nodes; throttle to once per 10 server ticks
    private static final int CHANNEL_REFRESH_INTERVAL = 10;
    private int channelRefreshCountdown = 0;

    // server constructor
    public FrequencyMenu(int containerId, Inventory playerInv, BlockEntity be) {
        super(TYPE, containerId);
        this.blockPos = be.getBlockPos();
        this.backingBlockEntity = be;
        this.cardMode = false;
        this.terminalLocator = null;
        this.cardPlayer = null;

        if (be instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            this.isController = true;
            this.isAdvanced = ctrl.isAdvanced();
            this.deviceName = ctrl.isAdvanced()
                    ? "block.ae2lt.advanced_wireless_overloaded_controller"
                    : "block.ae2lt.wireless_overloaded_controller";
            this.freqIdSlot.set(ctrl.getFrequencyId());
            this.linkActiveSlot.set(ctrl.isFrequencyActive() ? 1 : 0);
            this.usedChannelsSlot.set(ctrl.getGridUsedChannels());
            this.maxChannelsSlot.set(ctrl.getGridMaxChannels());
        } else if (be instanceof FrequencyBindingHost bindingHost) {
            this.isController = false;
            this.isAdvanced = false;
            this.deviceName = bindingHost.getFrequencyBindingDeviceName();
            this.freqIdSlot.set(bindingHost.getFrequencyId());
            this.linkActiveSlot.set(bindingHost.isFrequencyConnected() ? 1 : 0);
            this.usedChannelsSlot.set(bindingHost.getGridUsedChannels());
            this.maxChannelsSlot.set(bindingHost.getGridMaxChannels());
        } else {
            this.isController = false;
            this.isAdvanced = false;
            this.deviceName = "block.ae2lt.wireless_receiver";
            this.freqIdSlot.set(-1);
            this.linkActiveSlot.set(0);
            this.usedChannelsSlot.set(0);
            this.maxChannelsSlot.set(0);
        }
        this.autoConnectSlot.set(0);

        registerDataSlots();

        // initial sync to the player who just opened this menu
        if (playerInv.player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, SyncFrequencyListPacket.fromServer());
            SyncFrequencyDetailPacket.sendInitialMembersIfNeeded(sp, freqIdSlot.get());
            SyncFrequencyDetailPacket.sendInitialConnectionsIfNeeded(sp, freqIdSlot.get());
        }
    }

    // server constructor (card mode: targets a frequency card in a terminal upgrade slot)
    public FrequencyMenu(int containerId, Inventory playerInv, ItemMenuHostLocator terminalLocator) {
        super(TYPE, containerId);
        this.blockPos = BlockPos.ZERO;
        this.backingBlockEntity = null;
        this.cardMode = true;
        this.terminalLocator = terminalLocator;
        this.cardPlayer = playerInv.player instanceof ServerPlayer sp ? sp : null;
        this.isController = false;
        this.isAdvanced = false;
        this.deviceName = "item.ae2lt.overloaded_frequency_card";

        var cardData = readCardData();
        int freqId = cardData.isBound() ? cardData.frequencyId() : -1;
        this.freqIdSlot.set(freqId);
        this.linkActiveSlot.set(readCardLinkActive(freqId));
        this.usedChannelsSlot.set(0);
        this.maxChannelsSlot.set(0);
        this.autoConnectSlot.set(cardData.autoConnect() ? 1 : 0);

        registerDataSlots();

        if (cardPlayer != null) {
            PacketDistributor.sendToPlayer(cardPlayer, SyncFrequencyListPacket.fromServer());
            SyncFrequencyDetailPacket.sendInitialMembersIfNeeded(cardPlayer, freqId);
            SyncFrequencyDetailPacket.sendInitialConnectionsIfNeeded(cardPlayer, freqId);
        }
    }

    // client constructor (from network)
    private static FrequencyMenu clientCreate(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        boolean cardMode = buf.readBoolean();
        BlockPos pos = buf.readBlockPos();
        boolean controller = buf.readBoolean();
        boolean advanced = buf.readBoolean();
        String deviceName = buf.readUtf(256);
        int freqId = buf.readInt();
        boolean linkActive = buf.readBoolean();
        int used = buf.readInt();
        int max = buf.readInt();
        boolean autoConnect = buf.readBoolean();
        return new FrequencyMenu(containerId, cardMode, pos, controller, advanced, deviceName,
                freqId, linkActive, used, max, autoConnect);
    }

    // client-side constructor
    private FrequencyMenu(int containerId, boolean cardMode, BlockPos pos, boolean isController, boolean isAdvanced,
                          String deviceName, int freqId, boolean linkActive, int used, int max, boolean autoConnect) {
        super(TYPE, containerId);
        this.blockPos = pos;
        this.isController = isController;
        this.isAdvanced = isAdvanced;
        this.deviceName = deviceName;
        this.backingBlockEntity = null;
        this.cardMode = cardMode;
        this.terminalLocator = null;
        this.cardPlayer = null;
        this.freqIdSlot.set(freqId);
        this.linkActiveSlot.set(linkActive ? 1 : 0);
        this.usedChannelsSlot.set(used);
        this.maxChannelsSlot.set(max);
        this.autoConnectSlot.set(autoConnect ? 1 : 0);
        registerDataSlots();
    }

    private void registerDataSlots() {
        addDataSlot(freqIdSlot);
        addDataSlot(linkActiveSlot);
        addDataSlot(usedChannelsSlot);
        addDataSlot(maxChannelsSlot);
        addDataSlot(autoConnectSlot);
    }

    public static void writeExtraData(FriendlyByteBuf buf, BlockEntity be) {
        buf.writeBoolean(false);
        buf.writeBlockPos(be.getBlockPos());
        if (be instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            buf.writeBoolean(true);
            buf.writeBoolean(ctrl.isAdvanced());
            buf.writeUtf(ctrl.isAdvanced()
                    ? "block.ae2lt.advanced_wireless_overloaded_controller"
                    : "block.ae2lt.wireless_overloaded_controller", 256);
            buf.writeInt(ctrl.getFrequencyId());
            buf.writeBoolean(ctrl.isFrequencyActive());
            buf.writeInt(ctrl.getGridUsedChannels());
            buf.writeInt(ctrl.getGridMaxChannels());
        } else if (be instanceof FrequencyBindingHost bindingHost) {
            buf.writeBoolean(false);
            buf.writeBoolean(false);
            buf.writeUtf(bindingHost.getFrequencyBindingDeviceName(), 256);
            buf.writeInt(bindingHost.getFrequencyId());
            buf.writeBoolean(bindingHost.isFrequencyConnected());
            buf.writeInt(bindingHost.getGridUsedChannels());
            buf.writeInt(bindingHost.getGridMaxChannels());
        } else {
            buf.writeBoolean(false);
            buf.writeBoolean(false);
            buf.writeUtf("block.ae2lt.wireless_receiver", 256);
            buf.writeInt(-1);
            buf.writeBoolean(false);
            buf.writeInt(0);
            buf.writeInt(0);
        }
        buf.writeBoolean(false);
    }

    /**
     * Writes the open-menu payload for card mode. The data is read from the
     * frequency card currently installed in {@code terminalStack}'s upgrade
     * inventory.
     */
    public static void writeCardExtraData(FriendlyByteBuf buf, ItemStack terminalStack) {
        var cardData = TerminalCardAccess.readCardData(terminalStack);
        int freqId = cardData.isBound() ? cardData.frequencyId() : -1;
        buf.writeBoolean(true);
        buf.writeBlockPos(BlockPos.ZERO);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeUtf("item.ae2lt.overloaded_frequency_card", 256);
        buf.writeInt(freqId);
        buf.writeBoolean(false);
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeBoolean(cardData.autoConnect());
    }

    @Override
    public void broadcastChanges() {
        if (cardMode) {
            var cardData = readCardData();
            int real = cardData.isBound() ? cardData.frequencyId() : -1;
            if (freqIdSlot.get() != real) {
                freqIdSlot.set(real);
                if (cardPlayer != null && cardPlayer.containerMenu == this) {
                    SyncFrequencyDetailPacket.sendInitialMembersIfNeeded(cardPlayer, real);
                    SyncFrequencyDetailPacket.sendInitialConnectionsIfNeeded(cardPlayer, real);
                }
            }
            int autoConnect = cardData.autoConnect() ? 1 : 0;
            if (autoConnectSlot.get() != autoConnect) {
                autoConnectSlot.set(autoConnect);
            }
            if (--channelRefreshCountdown <= 0) {
                channelRefreshCountdown = CHANNEL_REFRESH_INTERVAL;
                int active = readCardLinkActive(real);
                if (linkActiveSlot.get() != active) {
                    linkActiveSlot.set(active);
                }
            }
            super.broadcastChanges();
            return;
        }
        if (backingBlockEntity != null) {
            int real = readFreqIdFromBE();
            if (freqIdSlot.get() != real) {
                freqIdSlot.set(real);
                // player's new frequency: push fresh member + connection snapshots
                var lvl = backingBlockEntity.getLevel();
                if (lvl != null && !lvl.isClientSide()) {
                    for (var p : lvl.players()) {
                        if (p instanceof ServerPlayer sp && sp.containerMenu == this) {
                            SyncFrequencyDetailPacket.sendInitialMembersIfNeeded(sp, real);
                            SyncFrequencyDetailPacket.sendInitialConnectionsIfNeeded(sp, real);
                        }
                    }
                }
            }

            int active = readLinkActiveFromBE();
            if (linkActiveSlot.get() != active) {
                linkActiveSlot.set(active);
            }

            if (--channelRefreshCountdown <= 0) {
                channelRefreshCountdown = CHANNEL_REFRESH_INTERVAL;
                int used = readUsedChannelsFromBE();
                if (usedChannelsSlot.get() != used) {
                    usedChannelsSlot.set(used);
                }
                int max = readMaxChannelsFromBE();
                if (maxChannelsSlot.get() != max) {
                    maxChannelsSlot.set(max);
                }
            }
        }
        super.broadcastChanges();
    }

    private int readFreqIdFromBE() {
        if (backingBlockEntity instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            return ctrl.getFrequencyId();
        }
        if (backingBlockEntity instanceof FrequencyBindingHost bindingHost) {
            return bindingHost.getFrequencyId();
        }
        return -1;
    }

    private int readLinkActiveFromBE() {
        if (backingBlockEntity instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            return ctrl.isFrequencyActive() ? 1 : 0;
        }
        if (backingBlockEntity instanceof FrequencyBindingHost bindingHost) {
            return bindingHost.isFrequencyConnected() ? 1 : 0;
        }
        return 0;
    }

    private int readUsedChannelsFromBE() {
        if (backingBlockEntity instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            return ctrl.getGridUsedChannels();
        }
        if (backingBlockEntity instanceof FrequencyBindingHost bindingHost) {
            return bindingHost.getGridUsedChannels();
        }
        return 0;
    }

    private int readMaxChannelsFromBE() {
        if (backingBlockEntity instanceof WirelessOverloadedControllerBlockEntity ctrl) {
            return ctrl.getGridMaxChannels();
        }
        if (backingBlockEntity instanceof FrequencyBindingHost bindingHost) {
            return bindingHost.getGridMaxChannels();
        }
        return 0;
    }

    @Override
    public boolean stillValid(@Nonnull Player player) {
        if (cardMode) {
            return TerminalCardAccess.hasCard(resolveTerminalStack());
        }
        if (backingBlockEntity != null) {
            if (backingBlockEntity.isRemoved() || backingBlockEntity.getLevel() == null) {
                return false;
            }

            if (player.level() != backingBlockEntity.getLevel()) {
                return false;
            }

            if (backingBlockEntity.getLevel().getBlockEntity(blockPos) != backingBlockEntity) {
                return false;
            }
        }

        return player.distanceToSqr(
                blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    @Nonnull
    public ItemStack quickMoveStack(@Nonnull Player player, int index) {
        return ItemStack.EMPTY;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public boolean isController() {
        return isController;
    }

    public boolean isAdvanced() {
        return isAdvanced;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public int getCurrentFrequencyId() {
        return freqIdSlot.get();
    }

    public boolean isCardMode() {
        return cardMode;
    }

    public boolean isAutoConnect() {
        return autoConnectSlot.get() != 0;
    }

    public boolean isLinkActive() {
        return linkActiveSlot.get() != 0;
    }

    /**
     * Server-side: resolves the wireless terminal item stack this card menu is
     * bound to, or {@link ItemStack#EMPTY} when unavailable.
     */
    public ItemStack resolveTerminalStack() {
        if (terminalLocator == null || cardPlayer == null) {
            return ItemStack.EMPTY;
        }
        return terminalLocator.locateItem(cardPlayer);
    }

    private OverloadedFrequencyCardData readCardData() {
        return TerminalCardAccess.readCardData(resolveTerminalStack());
    }

    private int readCardLinkActive(int freqId) {
        if (freqId <= 0 || cardPlayer == null) {
            return 0;
        }
        var manager = WirelessFrequencyManager.get();
        if (manager == null) {
            return 0;
        }
        return manager.resolveNode(freqId, cardPlayer.serverLevel().getServer()) != null ? 1 : 0;
    }

    public int getUsedChannels() {
        return usedChannelsSlot.get();
    }

    public int getMaxChannels() {
        return maxChannelsSlot.get();
    }

    /**
     * Validate that the player has a {@link FrequencyMenu} open with the given containerId token.
     * Returns the menu when valid, otherwise null.
     */
    @Nullable
    public static FrequencyMenu validateToken(ServerPlayer player, int token) {
        if (player.containerMenu instanceof FrequencyMenu fm && fm.containerId == token) {
            return fm.stillValid(player) ? fm : null;
        }
        return null;
    }
}
