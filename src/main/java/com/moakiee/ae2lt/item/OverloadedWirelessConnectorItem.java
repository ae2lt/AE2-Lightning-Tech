package com.moakiee.ae2lt.item;

import com.moakiee.ae2lt.block.OverloadedInterfaceBlock;
import com.moakiee.ae2lt.block.OverloadedPatternProviderBlock;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class OverloadedWirelessConnectorItem extends Item {
    private static final String TAG_SELECTED = "SelectedProvider";
    private static final String TAG_DIM = "Dim";
    private static final String TAG_POS = "Pos";
    private static final String TAG_HOST_TYPE = "HostType";

    public static final String HOST_PROVIDER = "provider";
    public static final String HOST_INTERFACE = "interface";

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
        boolean isHost = state.getBlock() instanceof OverloadedPatternProviderBlock
                || state.getBlock() instanceof OverloadedInterfaceBlock;
        boolean isMachine = targetBe != null;

        if (!isHost && !isMachine) {
            return InteractionResult.PASS;
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!hasSelection(stack)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            var hostType = getSelectedHostType(stack);
            clearSelection(stack);
            var message = Component.translatable(getDeselectedTranslationKey(hostType)).withStyle(ChatFormatting.GREEN);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(message, true);
            } else {
                player.sendSystemMessage(message);
            }
        }
        return InteractionResult.SUCCESS;
    }

    public static void selectHost(ItemStack stack, Level level, BlockPos pos, String hostType) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            var sel = new CompoundTag();
            sel.putString(TAG_DIM, level.dimension().identifier().toString());
            sel.putLong(TAG_POS, pos.asLong());
            sel.putString(TAG_HOST_TYPE, hostType);
            tag.put(TAG_SELECTED, sel);
        });
    }

    public static boolean hasSelection(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getCompound(TAG_SELECTED).isPresent();
    }

    @Nullable
    public static String getSelectedHostType(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var sel = tag.getCompound(TAG_SELECTED).orElse(null);
        if (sel == null) {
            return null;
        }
        return sel.getStringOr(TAG_HOST_TYPE, HOST_PROVIDER);
    }

    public static boolean isSelectionInCurrentDimension(Level level, ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var sel = tag.getCompound(TAG_SELECTED).orElse(null);
        if (sel == null) {
            return true;
        }
        var dimString = sel.getStringOr(TAG_DIM, level.dimension().identifier().toString());
        try {
            return level.dimension().identifier().equals(Identifier.parse(dimString));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void clearSelection(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(TAG_SELECTED);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static String getDeselectedTranslationKey(@Nullable String hostType) {
        if (HOST_INTERFACE.equals(hostType)) {
            return "ae2lt.connector.deselected_interface";
        }
        return "ae2lt.connector.deselected";
    }

    @Nullable
    private static BlockEntity resolveSelectedHost(Level level, ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var sel = tag.getCompound(TAG_SELECTED).orElse(null);
        if (sel == null) {
            return null;
        }

        ResourceKey<Level> dimKey;
        try {
            dimKey = ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.parse(sel.getStringOr(TAG_DIM, Level.OVERWORLD.identifier().toString())));
        } catch (RuntimeException ignored) {
            return null;
        }

        var pos = BlockPos.of(sel.getLongOr(TAG_POS, 0L));
        if (!level.dimension().equals(dimKey) || !level.isLoaded(pos)) {
            return null;
        }
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
}
