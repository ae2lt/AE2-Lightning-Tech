package com.moakiee.ae2lt.item;

import com.moakiee.ae2lt.block.OverloadedInterfaceBlock;
import com.moakiee.ae2lt.block.OverloadedPatternProviderBlock;
import com.moakiee.ae2lt.block.OverloadedPowerSupplyBlock;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.network.NetworkInit;
import com.moakiee.ae2lt.network.WirelessConnectorUsePacket;
import com.moakiee.ae2lt.util.MixinReflectionSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

/**
 * A tool item for establishing and managing wireless connections between an
 * Overloaded Pattern Provider / Overloaded ME Interface and remote machines.
 */
public class OverloadedWirelessConnectorItem extends AE2LTItem {

    private static final String TAG_SELECTED = "SelectedProvider";
    private static final String TAG_DIM = "Dim";
    private static final String TAG_POS = "Pos";
    private static final String TAG_HOST_TYPE = "HostType";
    private static final @Nullable Class<?> EXTENDED_AE_WIRELESS_CONNECTOR_CLASS =
            MixinReflectionSupport.findClassSafe("com.glodblock.github.extendedae.common.blocks.BlockWirelessConnector");
    private static final @Nullable Class<?> EXTENDED_AE_WIRELESS_HUB_CLASS =
            MixinReflectionSupport.findClassSafe("com.glodblock.github.extendedae.common.blocks.BlockWirelessHub");

    public static final String HOST_PROVIDER = "provider";
    public static final String HOST_INTERFACE = "interface";
    public static final String HOST_POWER_SUPPLY = "power_supply";

    public OverloadedWirelessConnectorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return handleBlockUse(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return handleBlockUse(context);
    }

    private InteractionResult handleBlockUse(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        var targetBe = level.getBlockEntity(pos);
        if (isExtendedAeWirelessBlock(state.getBlock())) {
            return InteractionResult.PASS;
        }
        boolean isHost = state.getBlock() instanceof OverloadedPatternProviderBlock
                || state.getBlock() instanceof OverloadedInterfaceBlock
                || state.getBlock() instanceof OverloadedPowerSupplyBlock;
        boolean isMachine = targetBe != null;

        if (!isHost && !isMachine) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            NetworkInit.sendToServer(new WirelessConnectorUsePacket(
                    context.getHand(),
                    pos,
                    context.getClickedFace(),
                    net.minecraft.client.gui.screens.Screen.hasControlDown()));
            return InteractionResult.sidedSuccess(true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.pass(stack);
        }

        if (hasSelection(stack)) {
            var hostType = getSelectedHostType(stack);
            clearSelection(stack);
            player.displayClientMessage(
                    Component.translatable(getDeselectedTranslationKey(hostType)).withStyle(ChatFormatting.GREEN),
                    true);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    // ── Selection management ─────────────────────────────────────────────

    public static void selectHost(ItemStack stack, Level level, BlockPos pos, String hostType) {
        com.moakiee.ae2lt.util.ItemStackTagSupport.updateTag(stack, tag -> {
            var sel = new CompoundTag();
            sel.putString(TAG_DIM, level.dimension().location().toString());
            sel.putLong(TAG_POS, pos.asLong());
            sel.putString(TAG_HOST_TYPE, hostType);
            tag.put(TAG_SELECTED, sel);
        });
    }

    public static boolean hasSelection(ItemStack stack) {
        var tag = com.moakiee.ae2lt.util.ItemStackTagSupport.getTagCopy(stack);
        return tag.contains(TAG_SELECTED, CompoundTag.TAG_COMPOUND);
    }

    @Nullable
    public static String getSelectedHostType(ItemStack stack) {
        var tag = com.moakiee.ae2lt.util.ItemStackTagSupport.getTagCopy(stack);
        if (!tag.contains(TAG_SELECTED)) return null;
        var sel = tag.getCompound(TAG_SELECTED);
        return sel.contains(TAG_HOST_TYPE) ? sel.getString(TAG_HOST_TYPE) : HOST_PROVIDER;
    }

    public static boolean isSelectionInCurrentDimension(Level level, ItemStack stack) {
        var tag = com.moakiee.ae2lt.util.ItemStackTagSupport.getTagCopy(stack);
        if (!tag.contains(TAG_SELECTED)) return true;
        var sel = tag.getCompound(TAG_SELECTED);
        if (!sel.contains(TAG_DIM)) return true;
        return level.dimension().location().equals(new ResourceLocation(sel.getString(TAG_DIM)));
    }

    public static void clearSelection(ItemStack stack) {
        var tag = com.moakiee.ae2lt.util.ItemStackTagSupport.getTagCopy(stack);
        tag.remove(TAG_SELECTED);
        if (tag.isEmpty()) {
            stack.setTag(null);
        } else {
            com.moakiee.ae2lt.util.ItemStackTagSupport.setTag(stack, tag);
        }
    }

    private static String getDeselectedTranslationKey(@Nullable String hostType) {
        if (HOST_INTERFACE.equals(hostType)) {
            return "ae2lt.connector.deselected_interface";
        }
        if (HOST_POWER_SUPPLY.equals(hostType)) {
            return "ae2lt.connector.deselected_power_supply";
        }
        return "ae2lt.connector.deselected";
    }

    @Nullable
    private static BlockEntity resolveSelectedHost(Level level, ItemStack stack) {
        var tag = com.moakiee.ae2lt.util.ItemStackTagSupport.getTagCopy(stack);
        if (!tag.contains(TAG_SELECTED)) return null;

        var sel = tag.getCompound(TAG_SELECTED);
        var dimKey = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(sel.getString(TAG_DIM)));
        var pos = BlockPos.of(sel.getLong(TAG_POS));

        if (!level.dimension().equals(dimKey) || !level.isLoaded(pos)) return null;

        return level.getBlockEntity(pos);
    }

    @Nullable
    public static OverloadedPatternProviderBlockEntity getSelectedProvider(Level level, ItemStack stack) {
        var be = resolveSelectedHost(level, stack);
        return be instanceof OverloadedPatternProviderBlockEntity provider ? provider : null;
    }

    @Nullable
    public static OverloadedInterfaceBlockEntity getSelectedInterface(Level level, ItemStack stack) {
        var be = resolveSelectedHost(level, stack);
        return be instanceof OverloadedInterfaceBlockEntity iface ? iface : null;
    }

    @Nullable
    public static OverloadedPowerSupplyBlockEntity getSelectedPowerSupply(Level level, ItemStack stack) {
        var be = resolveSelectedHost(level, stack);
        return be instanceof OverloadedPowerSupplyBlockEntity powerSupply ? powerSupply : null;
    }

    private static boolean isExtendedAeWirelessBlock(Object block) {
        return isInstanceOf(block, EXTENDED_AE_WIRELESS_CONNECTOR_CLASS)
                || isInstanceOf(block, EXTENDED_AE_WIRELESS_HUB_CLASS);
    }

    private static boolean isInstanceOf(Object value, @Nullable Class<?> targetClass) {
        return targetClass != null && targetClass.isInstance(value);
    }
}
