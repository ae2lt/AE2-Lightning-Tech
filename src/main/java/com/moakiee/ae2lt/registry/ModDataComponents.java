package com.moakiee.ae2lt.registry;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.phase.PhaseLockProjectionLink;
import com.moakiee.ae2lt.celestweave.state.CelestweaveModuleContainer;
import com.moakiee.ae2lt.item.railgun.RailgunModuleEntries;
import com.moakiee.ae2lt.item.railgun.RailgunSettings;
import com.moakiee.ae2lt.util.ItemStackTagSupport;

/**
 * AE2LT 数据组件 —— 1.20.1 Forge 移植版。
 *
 * <p>MC 1.20.1 没有 1.21 的 {@code DataComponentType} 系统（1.20.5+ 才引入），因此
 * 1.21 版的 {@code DeferredRegister.DataComponents} 被替换为 <b>ItemStack NBT 直接读写</b>
 * 方案：每个"组件"对应一个 NBT key（统一 {@code ae2lt:} 前缀），由 {@link ComponentKey}
 * 封装 {@code get/getOrDefault/set/remove} 语义，行为对齐 1.21 的
 * {@code stack.getOrDefault(...)/set(...)/remove(...)} 调用点，调用方只需把
 * {@code stack.getOrDefault(X.get(), def)} 改写为 {@code X.getOrDefault(stack, def)}。
 *
 * <p>1.20.1 的 ItemStack NBT 会随物品完整保存与同步（无需 1.21 的 networkSynchronized）。
 */
public final class ModDataComponents {
    private ModDataComponents() {
    }

    /** NBT key 统一前缀：所有 AE2LT 私有数据都放在这里，便于投影镜像等逻辑过滤。 */
    public static final String TAG_PREFIX = "ae2lt:";

    /**
     * 组件 key：NBT tag key + 编解码函数。
     *
     * <p>用法（对齐 1.21 DataComponentType 调用点）：
     * <pre>
     * RailgunSettings s = ModDataComponents.RAILGUN_SETTINGS.getOrDefault(stack, RailgunSettings.DEFAULT);
     * ModDataComponents.RAILGUN_SETTINGS.set(stack, s.withPvp(true));
     * ModDataComponents.RAILGUN_SETTINGS.remove(stack);
     * </pre>
     */
    public static final class ComponentKey<T> {
        private final String nbtKey;
        private final Function<CompoundTag, T> decode;
        private final BiConsumer<CompoundTag, T> encode;

        private ComponentKey(String nbtKey, Function<CompoundTag, T> decode, BiConsumer<CompoundTag, T> encode) {
            this.nbtKey = nbtKey;
            this.decode = decode;
            this.encode = encode;
        }

        public String nbtKey() {
            return nbtKey;
        }

        /** 读：key 缺失或 tag 为空时返回 null（对齐 1.21 的 {@code stack.get(DataComponentType)}）。 */
        @Nullable
        public T get(ItemStack stack) {
            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains(nbtKey)) {
                return null;
            }
            return decode.apply(tag);
        }

        /** 读：key 缺失时返回 def。 */
        public T getOrDefault(ItemStack stack, T def) {
            T value = get(stack);
            return value != null ? value : def;
        }

        /** 写：内部 encode 自行决定"空值即删除"的语义。 */
        public void set(ItemStack stack, T value) {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            ItemStackTagSupport.updateTag(stack, tag -> encode.accept(tag, value));
        }

        /** 删：移除该 NBT key（tag 变空时 ItemStackTagSupport 会置 null）。 */
        public void remove(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            ItemStackTagSupport.updateTag(stack, tag -> tag.remove(nbtKey));
        }

        public boolean has(ItemStack stack) {
            CompoundTag tag = stack.getTag();
            return tag != null && tag.contains(nbtKey);
        }
    }

    /** Per-stack railgun module entries（手动 NBT：1.20.1 无 ItemStack Codec/StreamCodec）。 */
    public static final ComponentKey<RailgunModuleEntries> RAILGUN_MODULE_ENTRIES = new ComponentKey<>(
            TAG_PREFIX + "railgun_module_entries",
            tag -> RailgunModuleEntries.load(tag.getCompound(TAG_PREFIX + "railgun_module_entries")),
            (tag, v) -> tag.put(TAG_PREFIX + "railgun_module_entries", v.save()));

    /** Persistent UI toggles for the railgun (terrain destruction, PVP) —— Codec + NbtOps 编解码。 */
    public static final ComponentKey<RailgunSettings> RAILGUN_SETTINGS = codecKey(
            TAG_PREFIX + "railgun_settings", RailgunSettings.CODEC, RailgunSettings.DEFAULT);

    /** Structural core installed in an electromagnetic railgun（空栈即删除）。 */
    public static final ComponentKey<ItemStack> RAILGUN_STRUCTURAL_CORE = itemStackKey(
            TAG_PREFIX + "railgun_structural_core");

    /** Server-authoritative charge ticks while the player is holding right-click. */
    public static final ComponentKey<Long> RAILGUN_CHARGE_TICKS = longKey(TAG_PREFIX + "railgun_charge_ticks");

    /** Per-stack FE energy buffer for v4 overload-device railguns. */
    public static final ComponentKey<Long> RAILGUN_ENERGY_BUFFER = longKey(TAG_PREFIX + "railgun_energy_buffer");

    /** Structural core installed in one Celestweave armor piece（空栈即删除）。 */
    public static final ComponentKey<ItemStack> CELESTWEAVE_STRUCTURAL_CORE = itemStackKey(
            TAG_PREFIX + "celestweave_structural_core");

    /** Per-stack FE energy buffer for v4 Celestweave armor pieces. */
    public static final ComponentKey<Long> CELESTWEAVE_ENERGY_BUFFER = longKey(TAG_PREFIX + "celestweave_energy_buffer");

    /** Installed modules, toggles and per-submodule data for one Celestweave armor piece（手动 NBT）。 */
    public static final ComponentKey<CelestweaveModuleContainer> CELESTWEAVE_MODULES = new ComponentKey<>(
            TAG_PREFIX + "celestweave_modules",
            tag -> CelestweaveModuleContainer.load(tag.getCompound(TAG_PREFIX + "celestweave_modules")),
            (tag, v) -> tag.put(TAG_PREFIX + "celestweave_modules", v.save()));

    /**
     * Server-authoritative "modules are powered this tick" flag. Absent means powered (the common
     * case)；只有断电的装备才写 {@code false}。
     */
    public static final ComponentKey<Boolean> CELESTWEAVE_MODULES_POWERED = boolKey(
            TAG_PREFIX + "celestweave_modules_powered");

    /** UUID and monotonic mirror version carried only by a phase-lock projection（可空）。 */
    public static final ComponentKey<PhaseLockProjectionLink> PHASE_LOCK_PROJECTION_LINK = codecKey(
            TAG_PREFIX + "phase_lock_projection_link", PhaseLockProjectionLink.CODEC, null);

    /** Monotonic mirror version carried only by authoritative phase-locked armor. */
    public static final ComponentKey<Long> PHASE_LOCK_ARMOR_UPDATE = longKey(TAG_PREFIX + "phase_lock_armor_update");

    // ---- 组件构造助手 ----

    /** Codec 组件：NbtOps 编解码（仅用于纯数据 record，不依赖 registry 上下文）。 */
    private static <T> ComponentKey<T> codecKey(String key, Codec<T> codec, T def) {
        return new ComponentKey<>(
                key,
                tag -> codec.parse(NbtOps.INSTANCE, tag.get(key)).result().orElse(def),
                (tag, v) -> codec.encodeStart(NbtOps.INSTANCE, v).result().ifPresent(t -> tag.put(key, t)));
    }

    private static ComponentKey<Long> longKey(String key) {
        return new ComponentKey<>(key, tag -> tag.getLong(key), (tag, v) -> tag.putLong(key, v));
    }

    private static ComponentKey<Boolean> boolKey(String key) {
        return new ComponentKey<>(key, tag -> tag.getBoolean(key), (tag, v) -> tag.putBoolean(key, v));
    }

    /**
     * ItemStack 值组件：1.20.1 的 {@code ItemStack.save(CompoundTag)} / {@code ItemStack.of(CompoundTag)}
     * 不需要 HolderLookup.Provider，可直接做 NBT 往返；空栈视为"无"并删除 key。
     */
    private static ComponentKey<ItemStack> itemStackKey(String key) {
        return new ComponentKey<>(
                key,
                tag -> {
                    ItemStack stack = ItemStack.of(tag.getCompound(key));
                    return stack.isEmpty() ? ItemStack.EMPTY : stack;
                },
                (tag, v) -> {
                    if (v == null || v.isEmpty()) {
                        tag.remove(key);
                    } else {
                        tag.put(key, v.save(new CompoundTag()));
                    }
                });
    }
}
