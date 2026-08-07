package com.moakiee.ae2lt.crafting.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.AEKeyFilter;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.ICraftingInventory;

import com.moakiee.ae2lt.overload.runtime.pattern.OverloadPatternDetails;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.ae2lt.overload.runtime.pattern.PatternExecutionHostKind;
import com.moakiee.thunderbolt.core.crafting.support.FastCraftingPlanner;

import org.junit.jupiter.api.Test;

/**
 * Locks the planner-side supply matrix of overload ID_ONLY slots ("误报缺失" regression):
 * <ul>
 *   <li>an ID_ONLY input can be supplied by any producer pattern sharing the item id — strict
 *       plain, strict overload, or (wiped) ID_ONLY overload outputs all appear to the planner as
 *       "a craftable same-id variant" and must be discovered even with zero stock of any variant;</li>
 *   <li>a STRICT slot must NOT borrow a same-id variant whose components differ;</li>
 *   <li>stocked same-id variants keep satisfying ID_ONLY slots (pre-existing behavior).</li>
 * </ul>
 * Uses a key type whose {@code getId()} is shared across "component variants" while equality is
 * per-variant, mirroring how {@code AEItemKey} exposes one item id across NBT variants.
 */
class FastCraftingPlannerIdOnlyCraftableVariantTest {
    private static final VariantKey MAT_DECLARED = new VariantKey("mat", "captured-nbt");
    private static final VariantKey MAT_CRAFTABLE = new VariantKey("mat", "producer-nbt");
    private static final VariantKey MAT_CRAFTABLE_2 = new VariantKey("mat", "producer-2-nbt");
    private static final VariantKey MAT_STOCKED = new VariantKey("mat", "stocked-nbt");
    private static final VariantKey BASE = new VariantKey("base", null);
    private static final VariantKey C = new VariantKey("c", null);
    private static final VariantKey D = new VariantKey("d", null);
    private static final VariantKey E = new VariantKey("e", null);
    private static final VariantKey TARGET = new VariantKey("target", null);

    @Test
    void idOnlySlotDiscoversCraftableSameIdVariant() {
        IPatternDetails producesVariant = new FakePattern(MAT_CRAFTABLE, new IPatternDetails.IInput[] {
                new FakeInput(BASE, 1)
        });
        IPatternDetails consumer = new FakeOverloadPattern(TARGET, new IPatternDetails.IInput[] {
                new FakeInput(MAT_DECLARED, 1)
        }, Set.of(0));

        var service = new FakeCraftingService()
                .pattern(TARGET, consumer)
                .pattern(MAT_CRAFTABLE, producesVariant)
                .craftable(TARGET).craftable(MAT_CRAFTABLE);
        var networkInv = new ChildCraftingSimulationState(new StockInventory(Map.of(BASE, 5L)));

        var attempt = FastCraftingPlanner.tryAttempt(service, networkInv, null, TARGET, 1, false);

        assertTrue(attempt.handled());
        assertNotNull(attempt.plan(),
                "id-only slot must be satisfiable by crafting a same-id variant no stock exists for");
        assertTrue(attempt.plan().missingItems().isEmpty());
        assertEquals(1L, attempt.plan().patternTimes().get(producesVariant),
                "the same-id producer route must be planned");
        assertEquals(1L, attempt.plan().patternTimes().get(consumer));
        assertEquals(1L, attempt.plan().usedItems().get(BASE));
    }

    @Test
    void idOnlySlotSplitsAcrossCraftableVariantsWithSharedInputStock() {
        IPatternDetails makesA1 = new FakePattern(MAT_CRAFTABLE, new IPatternDetails.IInput[] {
                new FakeInput(C, 1), new FakeInput(E, 1)
        });
        IPatternDetails makesA2 = new FakePattern(MAT_CRAFTABLE_2, new IPatternDetails.IInput[] {
                new FakeInput(D, 1), new FakeInput(E, 2)
        });
        IPatternDetails makesB = new FakeOverloadPattern(TARGET, new IPatternDetails.IInput[] {
                new FakeInput(MAT_DECLARED, 1)
        }, Set.of(0));

        var service = new FakeCraftingService()
                .pattern(TARGET, makesB)
                .pattern(MAT_CRAFTABLE, makesA1)
                .pattern(MAT_CRAFTABLE_2, makesA2)
                .craftable(TARGET).craftable(MAT_CRAFTABLE).craftable(MAT_CRAFTABLE_2);
        var networkInv = new ChildCraftingSimulationState(new StockInventory(Map.of(
                C, 3L,
                D, 4L,
                E, 5L)));

        var attempt = FastCraftingPlanner.tryAttempt(service, networkInv, null, TARGET, 4, false);

        assertTrue(attempt.handled());
        assertNotNull(attempt.plan());
        assertTrue(attempt.plan().missingItems().isEmpty());
        assertEquals(4L, attempt.plan().patternTimes().get(makesB));
        assertEquals(3L, attempt.plan().patternTimes().get(makesA1));
        assertEquals(1L, attempt.plan().patternTimes().get(makesA2));
        assertEquals(3L, attempt.plan().usedItems().get(C));
        assertEquals(1L, attempt.plan().usedItems().get(D));
        assertEquals(5L, attempt.plan().usedItems().get(E));
    }

    @Test
    void strictSlotDoesNotBorrowCraftableSameIdVariant() {
        IPatternDetails producesVariant = new FakePattern(MAT_CRAFTABLE, new IPatternDetails.IInput[] {
                new FakeInput(BASE, 1)
        });
        // Same shape, but the overload slot is STRICT: components differ -> must stay missing.
        IPatternDetails consumer = new FakeOverloadPattern(TARGET, new IPatternDetails.IInput[] {
                new FakeInput(MAT_DECLARED, 1)
        }, Set.of());

        var service = new FakeCraftingService()
                .pattern(TARGET, consumer)
                .pattern(MAT_CRAFTABLE, producesVariant)
                .craftable(TARGET).craftable(MAT_CRAFTABLE);
        var networkInv = new ChildCraftingSimulationState(new StockInventory(Map.of(BASE, 5L)));

        var attempt = FastCraftingPlanner.tryAttempt(service, networkInv, null, TARGET, 1, false);

        assertTrue(attempt.handled());
        assertNull(attempt.plan(), "a strict slot must not be satisfied by a different-component variant");
        assertNotNull(attempt.simulationFallback());
        assertEquals(1L, attempt.simulationFallback().missingItems().get(MAT_DECLARED));
        assertNull(attempt.simulationFallback().patternTimes().get(producesVariant),
                "the mismatching producer must not be pulled into a strict route");
    }

    @Test
    void idOnlySlotStillUsesStockedSameIdVariant() {
        IPatternDetails consumer = new FakeOverloadPattern(TARGET, new IPatternDetails.IInput[] {
                new FakeInput(MAT_DECLARED, 1)
        }, Set.of(0));

        var service = new FakeCraftingService().pattern(TARGET, consumer).craftable(TARGET);
        var networkInv = new ChildCraftingSimulationState(new StockInventory(Map.of(MAT_STOCKED, 2L)));

        var attempt = FastCraftingPlanner.tryAttempt(service, networkInv, null, TARGET, 1, false);

        assertTrue(attempt.handled());
        assertNotNull(attempt.plan());
        assertTrue(attempt.plan().missingItems().isEmpty());
        assertEquals(1L, attempt.plan().usedItems().get(MAT_STOCKED));
    }

    private record FakePattern(AEKey output, IInput[] inputs) implements IPatternDetails {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return inputs; }
        @Override public List<appeng.api.stacks.GenericStack> getOutputs() {
            return List.of(new appeng.api.stacks.GenericStack(output, 1));
        }
    }

    /**
     * Overload pattern stand-in: exposes per-slot fuzzy flags through the narrow
     * {@link OverloadedProviderOnlyPatternDetails#isFuzzyInput(int)} accessor the planner consults,
     * without needing a registry-backed {@link OverloadPatternDetails}.
     */
    private record FakeOverloadPattern(AEKey output, IInput[] inputs, Set<Integer> idOnlySlots)
            implements IPatternDetails, OverloadedProviderOnlyPatternDetails {
        @Override public AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return inputs; }
        @Override public List<appeng.api.stacks.GenericStack> getOutputs() {
            return List.of(new appeng.api.stacks.GenericStack(output, 1));
        }
        @Override public PatternExecutionHostKind requiredHostKind() {
            return PatternExecutionHostKind.OVERLOADED_PATTERN_PROVIDER;
        }
        @Override public String overloadPatternIdentity() { return "test:" + output; }
        @Override public OverloadPatternDetails overloadPatternDetailsView() { return null; }
        @Override public boolean isFuzzyInput(int slot) { return idOnlySlots.contains(slot); }
    }

    private record FakeInput(AEKey key, long amount) implements IPatternDetails.IInput {
        @Override public appeng.api.stacks.GenericStack[] getPossibleInputs() {
            return new appeng.api.stacks.GenericStack[] {
                    new appeng.api.stacks.GenericStack(key, amount) };
        }
        @Override public long getMultiplier() { return 1; }
        @Override public boolean isValid(AEKey candidate, Level level) {
            return candidate.getId().equals(key.getId());
        }
        @Override public AEKey getRemainingKey(AEKey template) { return null; }
    }

    private static final class FakeCraftingService implements ICraftingService {
        private final Map<AEKey, List<IPatternDetails>> patterns = new LinkedHashMap<>();
        private final Set<AEKey> craftables = new java.util.LinkedHashSet<>();

        FakeCraftingService pattern(AEKey output, IPatternDetails details) {
            patterns.computeIfAbsent(output, ignored -> new ArrayList<>()).add(details);
            return this;
        }

        FakeCraftingService craftable(AEKey key) {
            craftables.add(key);
            return this;
        }

        @Override public java.util.Collection<IPatternDetails> getCraftingFor(AEKey whatToCraft) {
            return patterns.getOrDefault(whatToCraft, List.of());
        }
        @Override public void refreshNodeCraftingProvider(IGridNode node) { }
        @Override public void addGlobalCraftingProvider(ICraftingProvider provider) { }
        @Override public void removeGlobalCraftingProvider(ICraftingProvider provider) { }
        @Override public void refreshGlobalCraftingProvider(ICraftingProvider provider) { }
        @Override public AEKey getFuzzyCraftable(AEKey whatToCraft, AEKeyFilter filter) {
            for (AEKey key : craftables) {
                if (key.getId().equals(whatToCraft.getId()) && filter.matches(key)) {
                    return key;
                }
            }
            return null;
        }
        @Override public Future<ICraftingPlan> beginCraftingCalculation(
                Level level, ICraftingSimulationRequester simRequester, AEKey what, long amount,
                CalculationStrategy strategy) {
            throw new UnsupportedOperationException();
        }
        @Override public ICraftingSubmitResult submitJob(
                ICraftingPlan job, appeng.api.networking.crafting.ICraftingRequester requestingMachine,
                ICraftingCPU target, boolean prioritizePower, IActionSource src) {
            throw new UnsupportedOperationException();
        }
        @Override public ImmutableSet<ICraftingCPU> getCpus() { return ImmutableSet.of(); }
        @Override public boolean canEmitFor(AEKey what) { return false; }
        @Override public Set<AEKey> getCraftables(AEKeyFilter filter) {
            var result = new java.util.LinkedHashSet<AEKey>();
            for (AEKey key : craftables) {
                if (filter.matches(key)) {
                    result.add(key);
                }
            }
            return result;
        }
        @Override public boolean isRequesting(AEKey what) { return false; }
        @Override public long getRequestedAmount(AEKey what) { return 0; }
        @Override public boolean isRequestingAny() { return false; }
    }

    /** Base inventory: fixed stock; fuzzy lookup returns stocked keys sharing the probe's id. */
    private static final class StockInventory implements ICraftingInventory {
        private final Map<AEKey, Long> stock;

        StockInventory(Map<AEKey, Long> stock) {
            this.stock = new HashMap<>(stock);
        }

        @Override public void insert(AEKey key, long amount, Actionable mode) {
            if (mode == Actionable.MODULATE) {
                stock.merge(key, amount, Long::sum);
            }
        }

        @Override public long extract(AEKey key, long amount, Actionable mode) {
            long available = stock.getOrDefault(key, 0L);
            long taken = Math.min(available, amount);
            if (mode == Actionable.MODULATE && taken > 0) {
                stock.put(key, available - taken);
            }
            return taken;
        }

        @Override public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
            var matches = new ArrayList<AEKey>();
            for (AEKey candidate : stock.keySet()) {
                if (candidate.getType() == key.getType() && candidate.getId().equals(key.getId())) {
                    matches.add(candidate);
                }
            }
            return matches;
        }
    }

    /**
     * Same primary identity across variants of one "item", per-variant equality. Like
     * {@code AEItemKey}, {@link #dropSecondary()} removes the variant components.
     */
    private static final class VariantKey extends AEKey {
        private static final VariantKeyType TYPE = new VariantKeyType();
        private final String id;
        private final String variant;

        private VariantKey(String id, String variant) {
            this.id = id;
            this.variant = variant;
        }

        @Override public AEKeyType getType() { return TYPE; }
        @Override public AEKey dropSecondary() { return new VariantKey(id, null); }
        @Override public CompoundTag toTag(net.minecraft.core.HolderLookup.Provider registries) {
            var tag = new CompoundTag();
            tag.putString("id", id);
            if (variant != null) {
                tag.putString("variant", variant);
            }
            return tag;
        }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("thunderbolt_test", id);
        }
        @Override public void writeToPacket(RegistryFriendlyByteBuf data) { }
        @Override protected Component computeDisplayName() {
            return Component.literal(id + (variant != null ? "#" + variant : ""));
        }
        @Override public void addDrops(
                long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
        @Override public boolean hasComponents() { return variant != null; }
        @Override public boolean equals(Object obj) {
            return obj instanceof VariantKey other
                    && id.equals(other.id)
                    && java.util.Objects.equals(variant, other.variant);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, variant); }
        @Override public String toString() { return id + (variant != null ? "#" + variant : ""); }
    }

    private static final class VariantKeyType extends AEKeyType {
        private VariantKeyType() {
            super(ResourceLocation.fromNamespaceAndPath("thunderbolt_test", "variant_key"),
                    VariantKey.class, Component.literal("variant key"));
        }
        @Override public MapCodec<? extends AEKey> codec() { return null; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return null; }
    }
}
