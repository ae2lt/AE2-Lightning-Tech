package com.moakiee.ae2lt.item.staff;

import java.util.List;
import com.moakiee.ae2lt.menu.hub.DeviceHubHost;
import com.moakiee.ae2lt.menu.hub.DeviceHubMenu;
import com.moakiee.ae2lt.device.DeviceItem;
import com.moakiee.ae2lt.device.DeviceKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

public class MimicryStaffItem extends TieredItem implements DeviceItem {
    @Override public DeviceKind deviceKind() { return DeviceKind.MIMICRY_STAFF; }
    private static final Tier STAFF_TIER = new Tier() {
        @Override public int getUses() { return 1; }
        @Override public float getSpeed() { return 8; }
        @Override public float getAttackDamageBonus() { return 3; }
        @Override public net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> getIncorrectBlocksForDrops() {
            return BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
        }
        @Override public int getEnchantmentValue() { return 0; }
        @Override public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
            return net.minecraft.world.item.crafting.Ingredient.EMPTY;
        }
    };

    public MimicryStaffItem(Properties properties) {
        super(STAFF_TIER, properties.fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        // Values are total base damage, including the player and diamond-tier base.
        return SwordItem.createAttributes(Tiers.DIAMOND, StaffCombat.baseDamage(stack) - 4, -2.4F);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        return StaffEnergy.canUse(stack) && supportsAction(stack, ability);
    }

    public boolean supportsAction(ItemStack stack, ItemAbility ability) {
        if (ability == ItemAbilities.SWORD_SWEEP) return StaffCombat.mode(stack) == 1;
        if (ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(ability)
                || ItemAbilities.DEFAULT_SWORD_ACTIONS.contains(ability)
                || ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability)) return true;
        var settings = StaffState.settings(stack);
        if (StaffState.has(stack, StaffModule.MATTOCK)) {
            if (ability == ItemAbilities.HOE_TILL) return settings.land() == 1;
            if (ability == ItemAbilities.SHOVEL_FLATTEN) return settings.land() == 2;
            if (ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability)
                    || ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability)) return true;
        }
        if (StaffState.has(stack, StaffModule.SHEARS) && ItemAbilities.DEFAULT_SHEARS_ACTIONS.contains(ability)) return true;
        if (StaffState.has(stack, StaffModule.KNIFE)
                && (ability == ItemAbility.get("knife_dig") || ability == ItemAbility.get("knife_harvest"))) return true;
        if (!StaffState.has(stack, StaffModule.WRENCH)) return false;
        if (ability == ItemAbility.get("wrench_configure")) return settings.mekanism() < 5;
        if (ability == ItemAbility.get("wrench_rotate")) return settings.mekanism() == 5;
        if (ability == ItemAbility.get("wrench_dismantle")) return settings.mekanism() == 6;
        return settings.mekanism() < 5 && ability == ItemAbility.get(switch (settings.mekanism()) {
            case 0 -> "wrench_configure_items";
            case 1 -> "wrench_configure_fluids";
            case 2 -> "wrench_configure_chemicals";
            case 3 -> "wrench_configure_energy";
            case 4 -> "wrench_configure_heat";
            default -> "ae2lt_no_wrench_action";
        });
    }

    private boolean miningType(ItemStack stack, BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_AXE)
                || StaffState.has(stack, StaffModule.MATTOCK)
                    && (state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.MINEABLE_WITH_HOE));
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (!StaffEnergy.canUse(stack)) return 0;
        float speed = state.is(Blocks.COBWEB) ? 15 : state.is(BlockTags.SWORD_EFFICIENT) ? 1.5F : 1;
        if (miningType(stack, state)) speed = Math.max(speed,
                StaffState.has(stack, StaffModule.NETHERITE) || StaffState.has(stack, StaffModule.UNRESTRICTED) ? 9 : 8);
        if (StaffState.has(stack, StaffModule.SHEARS)) speed = Math.max(speed, ShearsItem.createToolProperties().getMiningSpeed(state));
        return speed;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        if (state.is(Blocks.COBWEB)) return true;
        if (!miningType(stack, state)) return false;
        if (StaffState.has(stack, StaffModule.UNRESTRICTED)) return true;
        return !state.is(StaffState.has(stack, StaffModule.NETHERITE)
                ? BlockTags.INCORRECT_FOR_NETHERITE_TOOL : BlockTags.INCORRECT_FOR_DIAMOND_TOOL);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide) stack.hurtAndBreak(1, miner, EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) { return true; }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) { return false; }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) { return false; }

    @Override
    public boolean isEnchantable(ItemStack stack) { return false; }

    @Override
    public ItemEnchantments getAllEnchantments(ItemStack stack, RegistryLookup<Enchantment> lookup) {
        return StaffEnchantments.effective(stack, lookup);
    }

    @Override
    public int getEnchantmentLevel(ItemStack stack, Holder<Enchantment> enchantment) {
        var lookup = CommonHooks.resolveLookup(Registries.ENCHANTMENT);
        return lookup == null ? 0 : getAllEnchantments(stack, lookup).getLevel(enchantment);
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        if (!StaffState.has(stack, StaffModule.WRENCH)) return false;
        String namespace = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getNamespace();
        return !namespace.equals("mekanism") || StaffState.settings(stack).mekanism() == 6;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var stack = context.getItemInHand();
        if (!StaffPhaseService.mayUse(context.getPlayer(), stack)) return InteractionResult.FAIL;
        if (!StaffEnergy.canUse(stack)) return InteractionResult.FAIL;
        var state = context.getLevel().getBlockState(context.getClickedPos());
        var namespace = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace();
        // Native block interaction runs first. Do not consume a cutting board's fallback path.
        if (namespace.equals("farmersdelight") && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().equals("cutting_board")) return InteractionResult.PASS;
        if (StaffState.has(stack, StaffModule.WRENCH) && ModList.get().isLoaded("mekanism")
                && (namespace.equals("mekanism") || namespace.startsWith("mekanism"))) {
            return StaffMekanism.useOn(context);
        }
        // Select using simulated transformations, then invoke exactly one native modifying action.
        for (var action : List.of(ItemAbilities.AXE_STRIP, ItemAbilities.AXE_SCRAPE, ItemAbilities.AXE_WAX_OFF)) {
            if (state.getToolModifiedState(context, action, true) != null) return Items.DIAMOND_AXE.useOn(context);
        }
        if (StaffState.has(stack, StaffModule.SHEARS) && state.getToolModifiedState(context, ItemAbilities.SHEARS_TRIM, true) != null) {
            return Items.SHEARS.useOn(context);
        }
        if (StaffState.has(stack, StaffModule.MATTOCK)) {
            if (state.getToolModifiedState(context, ItemAbilities.SHOVEL_DOUSE, true) != null) return Items.DIAMOND_SHOVEL.useOn(context);
            return switch (StaffState.settings(stack).land()) {
                case 1 -> Items.DIAMOND_HOE.useOn(context);
                case 2 -> Items.DIAMOND_SHOVEL.useOn(context);
                default -> InteractionResult.PASS;
            };
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (!StaffPhaseService.mayUse(player, stack)) return InteractionResult.FAIL;
        if (!StaffEnergy.canUse(stack) || !StaffState.has(stack, StaffModule.SHEARS) || !(entity instanceof IShearable target)) return InteractionResult.PASS;
        var pos = entity.blockPosition();
        if (!target.isShearable(player, stack, entity.level(), pos)) return InteractionResult.PASS;
        var drops = target.onSheared(player, stack, entity.level(), pos);
        if (entity.level() instanceof ServerLevel level) {
            for (var drop : drops) {
                var outputs = StaffDrops.active(stack) ? StaffDrops.process(level, player, stack, drop) : List.of(drop);
                for (var output : outputs) target.spawnShearedDrop(level, pos, output);
            }
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        entity.gameEvent(GameEvent.SHEAR, player);
        return InteractionResult.sidedSuccess(entity.level().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || player.pick(player.blockInteractionRange(), 0, false).getType() != HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            DeviceHubHost.open(serverPlayer, DeviceHubMenu.TAB_STAFF);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // Keep a damageable marker so native and modded hurtAndBreak callbacks reach damageItem.
    // Actual damage remains zero: neither breakage nor Mending can mutate the FE buffer.
    @Override public int getDamage(ItemStack stack) { return 0; }
    @Override public void setDamage(ItemStack stack, int damage) {}
    @Override public boolean isDamaged(ItemStack stack) { return false; }
    @Override public boolean isRepairable(ItemStack stack) { return false; }
    @Override public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) { return false; }
    @Override public boolean isBarVisible(ItemStack stack) { return StaffEnergy.stored(stack) < StaffEnergy.CAPACITY; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13F * StaffEnergy.stored(stack) / StaffEnergy.CAPACITY); }
    @Override public int getBarColor(ItemStack stack) { return 0x9C7BFF; }
    @Override public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity,
            java.util.function.Consumer<Item> onBroken) {
        if (entity == null || !entity.level().isClientSide) StaffEnergy.consume(stack, amount);
        return 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (PhaseItemProtection.isLockedStaff(stack)) tooltip.add(Component.translatable("ae2lt.staff.phase_locked").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("ae2lt.staff.energy", String.format(java.util.Locale.ROOT, "%,d", StaffEnergy.stored(stack)), "100,000,000").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(com.moakiee.ae2lt.util.DeviceHubTooltip.openConfigHint());
        tooltip.add(Component.translatable("ae2lt.staff.install_workbench").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("ae2lt.staff.lightning_binding").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("ae2lt.staff.modules", StaffPhaseService.isProjection(stack)
                ? Integer.bitCount(StaffPhaseService.view(stack).modules()) : StaffState.modules(stack).stream().filter(s -> !s.isEmpty()).count())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("ae2lt.staff.enchant_modules").withStyle(ChatFormatting.DARK_GRAY));
    }
}
