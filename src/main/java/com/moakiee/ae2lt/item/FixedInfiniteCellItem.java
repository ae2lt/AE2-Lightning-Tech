package com.moakiee.ae2lt.item;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.items.storage.StorageCellTooltipComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModItems;

public final class FixedInfiniteCellItem extends Item {

    private static final String TAG_SEED = "CellSeed";
    private static final String TAG_TYPE = "CellType";
    private static final String TAG_RESULT_CONSUMED = "ResultConsumed";

    public enum CellOutcome {
        LIGHTNING_ROD((byte) 0, "lightning_rod"),
        HIGH_VOLTAGE((byte) 1, "high_voltage"),
        EXTREME_HIGH_VOLTAGE((byte) 2, "extreme_high_voltage"),
        LIGHTNING_COLLAPSE_MATRIX((byte) 3, "lightning_collapse_matrix"),
        RESEARCH_NOTE((byte) 4, "research_note"),
        MOAKIEE_FUMO((byte) 5, "moakiee_fumo"),
        CYSTRYSU_FUMO((byte) 6, "cystrysu_fumo");

        private final byte typeId;
        private final String suffix;

        CellOutcome(byte typeId, String suffix) {
            this.typeId = typeId;
            this.suffix = suffix;
        }

        public byte typeId() {
            return typeId;
        }

        public String suffix() {
            return suffix;
        }

        public static CellOutcome fromTypeId(byte id) {
            return switch (id) {
                case 1 -> HIGH_VOLTAGE;
                case 2 -> EXTREME_HIGH_VOLTAGE;
                case 3 -> LIGHTNING_COLLAPSE_MATRIX;
                case 4 -> RESEARCH_NOTE;
                case 5 -> MOAKIEE_FUMO;
                case 6 -> CYSTRYSU_FUMO;
                default -> LIGHTNING_ROD;
            };
        }

        public AEKey displayKey() {
            return switch (this) {
                case LIGHTNING_ROD -> AEItemKey.of(Items.LIGHTNING_ROD);
                case HIGH_VOLTAGE -> LightningKey.HIGH_VOLTAGE;
                case EXTREME_HIGH_VOLTAGE -> LightningKey.EXTREME_HIGH_VOLTAGE;
                case LIGHTNING_COLLAPSE_MATRIX -> AEItemKey.of(ModItems.LIGHTNING_COLLAPSE_MATRIX.get());
                case RESEARCH_NOTE -> AEItemKey.of(ModItems.RESEARCH_NOTE.get());
                case MOAKIEE_FUMO -> AEItemKey.of(ModFumos.MOAKIEE_FUMO_ITEM.get());
                case CYSTRYSU_FUMO -> AEItemKey.of(ModFumos.CYSTRYSU_FUMO_ITEM.get());
            };
        }
    }

    public FixedInfiniteCellItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // ── Seed (outer cell only) ──

    public static void setSeed(ItemStack stack, UUID seed) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(TAG_SEED, seed));
    }

    public static void initializeOuterCell(ItemStack stack) {
        if (hasType(stack)) {
            return;
        }
        if (!hasSeed(stack)) {
            setSeed(stack, UUID.randomUUID());
        }
    }

    @Nullable
    public static UUID getSeed(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(TAG_SEED) ? tag.getUUID(TAG_SEED) : null;
    }

    public static boolean hasSeed(ItemStack stack) {
        return getSeed(stack) != null;
    }

    public static boolean isOuterCell(ItemStack stack) {
        return !hasType(stack);
    }

    public static boolean isResultConsumed(ItemStack stack) {
        if (!isOuterCell(stack)) {
            return false;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getBoolean(TAG_RESULT_CONSUMED);
    }

    public static void setResultConsumed(ItemStack stack, boolean consumed) {
        if (!isOuterCell(stack)) {
            return;
        }
        initializeOuterCell(stack);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(TAG_RESULT_CONSUMED, consumed));
    }

    /**
     * 按 seed + 世界种子 hash 出 10000 个 roll 区间,再映射到一个 outcome:
     * <ul>
     *   <li>roll 0..49 → RESEARCH_NOTE (50/10000, 0.5%, UR 暗门;是 HV/EHV/矩阵/无限存储
     *       仪式的唯一入口)</li>
     *   <li>roll 50..2049 → MOAKIEE_FUMO (2000/10000, 20%, 作者彩蛋)</li>
     *   <li>roll 2050..4049 → CYSTRYSU_FUMO (2000/10000, 20%, 作者彩蛋)</li>
     *   <li>roll 4050..9999 → LIGHTNING_ROD (5950/10000, 59.5%, R)</li>
     * </ul>
     * HIGH_VOLTAGE / EXTREME_HIGH_VOLTAGE / LIGHTNING_COLLAPSE_MATRIX /
     * INFINITE_STORAGE_CELL 全部仅通过研究笔记仪式产出,不再由扭蛋直接抽到。
     */
    public static CellOutcome resolveOutcome(UUID seed, long worldSeed) {
        long mixed = (seed.getLeastSignificantBits() ^ worldSeed)
                   ^ (seed.getMostSignificantBits() ^ Long.reverseBytes(worldSeed));
        int roll = Math.floorMod(mixed, 10000);
        if (roll <= 49) return CellOutcome.RESEARCH_NOTE;
        if (roll <= 2049) return CellOutcome.MOAKIEE_FUMO;
        if (roll <= 4049) return CellOutcome.CYSTRYSU_FUMO;
        return CellOutcome.LIGHTNING_ROD;
    }

    public static CellOutcome getOutcomeFromSeed(ItemStack stack) {
        UUID seed = getSeed(stack);
        if (seed == null) return CellOutcome.LIGHTNING_ROD;
        long worldSeed = 0L;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            worldSeed = server.overworld().getSeed();
        }
        return resolveOutcome(seed, worldSeed);
    }

    // ── Type byte (inner cell only) ──

    public static void setType(ItemStack stack, byte type) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putByte(TAG_TYPE, type));
    }

    public static boolean hasType(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(TAG_TYPE);
    }

    public static byte getType(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getByte(TAG_TYPE);
    }

    // ── Effective key ──

    /**
     * Has CellType → inner cell: reads type byte → actual stored content.<br>
     * No CellType → outer cell: seed → outcome → produces inner cells with type byte baked in.
     */
    public static AEKey getEffectiveKey(ItemStack stack) {
        if (hasType(stack)) {
            return CellOutcome.fromTypeId(getType(stack)).displayKey();
        }
        return AEItemKey.of(createDisplayedResultStack(stack));
    }

    public static ItemStack createDisplayedResultStack(ItemStack stack) {
        return createDisplayedResultStack(getOutcomeFromSeed(stack));
    }

    public static ItemStack createDisplayedResultStack(CellOutcome outcome) {
        if (outcome == CellOutcome.RESEARCH_NOTE) {
            return new ItemStack(ModItems.RESEARCH_NOTE.get());
        }

        ItemStack innerStack = new ItemStack(ModItems.MYSTERIOUS_CELL.get());
        setType(innerStack, outcome.typeId());
        return innerStack;
    }

    // ── Display ──

    private static final String BASE_KEY = "item.ae2lt.mysterious_cell";

    @Override
    public Component getName(ItemStack stack) {
        if (hasType(stack)) {
            return Component.translatable(BASE_KEY + "." + CellOutcome.fromTypeId(getType(stack)).suffix());
        }
        return Component.translatable(BASE_KEY);
    }

    private static final String TOOLTIP_KEY = "tooltip.ae2lt.mysterious_cell";

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (hasType(stack)) {
            // 内核 cell(扭蛋解析完的成品,或创造栏里直接拿的变体)不再画任何
            // flavor 文案 —— 物品名已经说明它是什么,tooltip 重复一遍既啰嗦
            // 又会把创造栏里的变体身份完全暴露给路过的玩家。
            return;
        }

        if (!hasSeed(stack)) {
            // 创造物品栏/JEI 里显示的是无 seed 的 "空壳"。保持 tooltip 静默,
            // 避免在这些地方泄露扭蛋的内部提示。seed 会在被玩家取出或直接
            // 使用时由 initializeOuterCell() 填充,那时再正常画 tooltip。
            return;
        }

        if (isResultConsumed(stack)) {
            tooltipComponents.add(Component.translatable(TOOLTIP_KEY + ".consumed")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltipComponents.add(Component.translatable(TOOLTIP_KEY + ".consumed.hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        String suffix = getOutcomeFromSeed(stack).suffix();
        tooltipComponents.add(Component.translatable(TOOLTIP_KEY + "." + suffix)
                .withStyle(ChatFormatting.GREEN));
        tooltipComponents.add(Component.translatable(TOOLTIP_KEY + ".hint.once")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        if (!hasType(stack) && !hasSeed(stack)) {
            return Optional.empty();
        }
        if (isResultConsumed(stack)) {
            return Optional.empty();
        }
        AEKey key = getEffectiveKey(stack);
        long amount = hasType(stack)
                ? (long) Integer.MAX_VALUE * key.getAmountPerUnit()
                : key.getAmountPerUnit();
        var content = Collections.singletonList(new GenericStack(key, amount));
        return Optional.of(new StorageCellTooltipComponent(List.of(), content, false, true));
    }
}
