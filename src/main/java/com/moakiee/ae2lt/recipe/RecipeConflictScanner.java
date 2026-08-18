package com.moakiee.ae2lt.recipe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.moakiee.ae2lt.lightning.LightningTransformRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.machine.firmament.recipe.FirmamentConversionRecipe;
import com.moakiee.ae2lt.machine.lightningassembly.recipe.LightningAssemblyRecipe;
import com.moakiee.ae2lt.machine.lightningchamber.recipe.LightningSimulationRecipe;
import com.moakiee.ae2lt.machine.overloadfactory.recipe.OverloadProcessingRecipe;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.moakiee.ae2lt.util.RecipeManagerByTypeAccess;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fluids.FluidStack;

/**
 * Finds machine recipes whose complete input can be supplied by the inputs of
 * the other recipes competing in the same recipe pool.
 *
 * <p>The scan deliberately gives every declared input {@value #INPUT_SCALE}
 * copies. For the recipe currently being checked, its own contribution is
 * removed before matching. Excluding the current recipe from the supply list is
 * mathematically the same as adding every recipe at the configured scale and
 * then subtracting the current recipe at that scale, but avoids creating and
 * cancelling very large intermediate counters.</p>
 *
 * <p>Ingredient alternatives are matched by capacity flow instead of choosing
 * {@code Ingredient#getItems()[0]}. This prevents overlapping tags from either
 * double-spending the same supply or hiding a valid overlap behind a different
 * display candidate.</p>
 */
public final class RecipeConflictScanner {
    public static final long INPUT_SCALE = 8192L;

    private RecipeConflictScanner() {
    }

    public static List<ResourceLocation> scan(RecipeManager recipeManager) {
        TreeSet<ResourceLocation> conflicts = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));

        // 1.20.1: getAllRecipesFor returns recipes without ids, so the id map from
        // RecipeManagerByTypeAccess (Mixin bridge into the protected byType field) is used.
        scanPool(
                RecipeManagerByTypeAccess.byType(recipeManager, ModRecipeTypes.LIGHTNING_TRANSFORM_TYPE.get())
                        .entrySet().stream()
                        .map(RecipeConflictScanner::fromLightningTransform)
                        .toList(),
                conflicts);
        scanPool(
                RecipeManagerByTypeAccess.byType(recipeManager, ModRecipeTypes.FIRMAMENT_CONVERSION_TYPE.get())
                        .entrySet().stream()
                        .map(RecipeConflictScanner::fromFirmamentConversion)
                        .toList(),
                conflicts);
        scanPool(
                RecipeManagerByTypeAccess.byType(recipeManager, ModRecipeTypes.LIGHTNING_SIMULATION_TYPE.get())
                        .entrySet().stream()
                        .map(RecipeConflictScanner::fromLightningSimulation)
                        .toList(),
                conflicts);
        scanPool(
                RecipeManagerByTypeAccess.byType(recipeManager, ModRecipeTypes.LIGHTNING_ASSEMBLY_TYPE.get())
                        .entrySet().stream()
                        .map(RecipeConflictScanner::fromLightningAssembly)
                        .toList(),
                conflicts);
        scanPool(
                RecipeManagerByTypeAccess.byType(recipeManager, ModRecipeTypes.OVERLOAD_PROCESSING_TYPE.get())
                        .entrySet().stream()
                        .map(RecipeConflictScanner::fromOverloadProcessing)
                        .toList(),
                conflicts);

        // Crystal and dust modes are selected before recipe matching and cannot
        // steal work from each other, so they must be scanned as separate pools.
        var catalyzerRecipes = RecipeManagerByTypeAccess.byType(
                recipeManager, ModRecipeTypes.CRYSTAL_CATALYZER_TYPE.get());
        for (Mode mode : Mode.values()) {
            scanPool(
                    catalyzerRecipes.entrySet().stream()
                            .filter(entry -> entry.getValue().mode() == mode)
                            .map(RecipeConflictScanner::fromCrystalCatalyzer)
                            .toList(),
                    conflicts);
        }

        // Lightning-strike recipes match block structures and positions in the
        // world rather than an unordered item/fluid pool, so this algorithm is
        // not applicable to that recipe type.
        return List.copyOf(conflicts);
    }

    private static RecipeRequirements fromLightningTransform(
            Map.Entry<ResourceLocation, LightningTransformRecipe> entry) {
        return new RecipeRequirements(
                entry.getKey(),
                entry.getValue().inputs().stream()
                        .map(input -> new ItemRequirement(input.ingredient(), input.count()))
                        .toList(),
                List.of());
    }

    private static RecipeRequirements fromFirmamentConversion(
            Map.Entry<ResourceLocation, FirmamentConversionRecipe> entry) {
        return new RecipeRequirements(
                entry.getKey(),
                entry.getValue().inputs().stream()
                        .map(input -> new ItemRequirement(input.ingredient(), input.count()))
                        .toList(),
                List.of());
    }

    private static RecipeRequirements fromLightningSimulation(
            Map.Entry<ResourceLocation, LightningSimulationRecipe> entry) {
        return new RecipeRequirements(
                entry.getKey(),
                entry.getValue().inputs().stream()
                        .map(input -> new ItemRequirement(input.ingredient(), input.count()))
                        .toList(),
                List.of());
    }

    private static RecipeRequirements fromLightningAssembly(
            Map.Entry<ResourceLocation, LightningAssemblyRecipe> entry) {
        return new RecipeRequirements(
                entry.getKey(),
                entry.getValue().inputs().stream()
                        .map(input -> new ItemRequirement(input.ingredient(), input.count()))
                        .toList(),
                List.of());
    }

    private static RecipeRequirements fromOverloadProcessing(
            Map.Entry<ResourceLocation, OverloadProcessingRecipe> entry) {
        OverloadProcessingRecipe recipe = entry.getValue();
        List<FluidRequirement> fluidRequirements = recipe.fluidInput().isEmpty()
                ? List.of()
                : List.of(new FluidRequirement(recipe.fluidInput()));
        return new RecipeRequirements(
                entry.getKey(),
                recipe.itemInputs().stream()
                        .map(input -> new ItemRequirement(input.ingredient(), input.count()))
                        .toList(),
                fluidRequirements);
    }

    private static RecipeRequirements fromCrystalCatalyzer(
            Map.Entry<ResourceLocation, CrystalCatalyzerRecipe> entry) {
        CrystalCatalyzerRecipe recipe = entry.getValue();
        List<ItemRequirement> itemRequirements = recipe.catalyst()
                .map(ingredient -> List.of(new ItemRequirement(ingredient, recipe.catalystCount())))
                .orElseGet(List::of);
        return new RecipeRequirements(entry.getKey(), itemRequirements, List.of());
    }

    private static void scanPool(
            List<RecipeRequirements> recipes,
            TreeSet<ResourceLocation> conflicts) {
        for (int targetIndex = 0; targetIndex < recipes.size(); targetIndex++) {
            RecipeRequirements target = recipes.get(targetIndex);
            List<ItemRequirement> itemSupplies = new ArrayList<>();
            List<FluidRequirement> fluidSupplies = new ArrayList<>();

            for (int sourceIndex = 0; sourceIndex < recipes.size(); sourceIndex++) {
                if (sourceIndex == targetIndex) {
                    continue;
                }

                RecipeRequirements source = recipes.get(sourceIndex);
                source.items().forEach(requirement -> itemSupplies.add(requirement.scaled()));
                source.fluids().forEach(requirement -> fluidSupplies.add(requirement.scaled()));
            }

            if (canCoverItems(itemSupplies, target.items())
                    && canCoverFluids(fluidSupplies, target.fluids())) {
                conflicts.add(target.id());
            }
        }
    }

    static boolean canCoverItems(
            List<ItemRequirement> supplies,
            List<ItemRequirement> requirements) {
        return canCover(
                supplies.stream().map(ItemRequirement::count).toList(),
                requirements.stream().map(ItemRequirement::count).toList(),
                (supplyIndex, requirementIndex) -> ingredientsOverlap(
                        supplies.get(supplyIndex).ingredient(),
                        requirements.get(requirementIndex).ingredient()));
    }

    private static boolean canCoverFluids(
            List<FluidRequirement> supplies,
            List<FluidRequirement> requirements) {
        return canCover(
                supplies.stream().map(FluidRequirement::amount).toList(),
                requirements.stream().map(FluidRequirement::amount).toList(),
                // 1.20.1: isFluidStackIdentical replaces 1.21's isSameFluidSameComponents.
                (supplyIndex, requirementIndex) -> supplies.get(supplyIndex).stack()
                        .isFluidStackIdentical(requirements.get(requirementIndex).stack()));
    }

    static boolean canCover(
            List<Long> supplyCapacities,
            List<Long> requirementAmounts,
            EdgePredicate edgePredicate) {
        long totalDemand = 0L;
        for (long amount : requirementAmounts) {
            totalDemand = Math.addExact(totalDemand, amount);
        }
        if (totalDemand == 0L) {
            return true;
        }
        if (supplyCapacities.isEmpty()) {
            return false;
        }

        int source = 0;
        int firstSupply = 1;
        int firstRequirement = firstSupply + supplyCapacities.size();
        int sink = firstRequirement + requirementAmounts.size();
        CapacityFlow flow = new CapacityFlow(sink + 1);

        for (int supplyIndex = 0; supplyIndex < supplyCapacities.size(); supplyIndex++) {
            flow.addEdge(source, firstSupply + supplyIndex, supplyCapacities.get(supplyIndex));
            for (int requirementIndex = 0; requirementIndex < requirementAmounts.size(); requirementIndex++) {
                if (edgePredicate.test(supplyIndex, requirementIndex)) {
                    flow.addEdge(
                            firstSupply + supplyIndex,
                            firstRequirement + requirementIndex,
                            totalDemand);
                }
            }
        }
        for (int requirementIndex = 0; requirementIndex < requirementAmounts.size(); requirementIndex++) {
            flow.addEdge(
                    firstRequirement + requirementIndex,
                    sink,
                    requirementAmounts.get(requirementIndex));
        }

        return flow.maxFlow(source, sink, totalDemand) == totalDemand;
    }

    private static boolean ingredientsOverlap(Ingredient left, Ingredient right) {
        for (ItemStack stack : left.getItems()) {
            if (right.test(stack)) {
                return true;
            }
        }
        for (ItemStack stack : right.getItems()) {
            if (left.test(stack)) {
                return true;
            }
        }
        return false;
    }

    private static long scale(long amount) {
        return Math.multiplyExact(amount, INPUT_SCALE);
    }

    record ItemRequirement(Ingredient ingredient, long count) {
        ItemRequirement {
            if (count <= 0L) {
                throw new IllegalArgumentException("count must be positive");
            }
        }

        ItemRequirement scaled() {
            return new ItemRequirement(ingredient, scale(count));
        }
    }

    private record FluidRequirement(FluidStack stack, long amount) {
        private FluidRequirement(FluidStack stack) {
            // 1.20.1: no copyWithAmount(int); copy via the (stack, amount) ctor.
            this(new FluidStack(stack, 1), stack.getAmount());
        }

        private FluidRequirement {
            if (stack.isEmpty() || amount <= 0L) {
                throw new IllegalArgumentException("fluid requirement must be non-empty and positive");
            }
        }

        private FluidRequirement scaled() {
            return new FluidRequirement(stack, scale(amount));
        }
    }

    private record RecipeRequirements(
            ResourceLocation id,
            List<ItemRequirement> items,
            List<FluidRequirement> fluids) {
    }

    @FunctionalInterface
    interface EdgePredicate {
        boolean test(int supplyIndex, int requirementIndex);
    }

    private static final class CapacityFlow {
        private final List<List<Edge>> graph;
        private int[] levels;
        private int[] cursors;

        private CapacityFlow(int nodeCount) {
            graph = new ArrayList<>(nodeCount);
            for (int i = 0; i < nodeCount; i++) {
                graph.add(new ArrayList<>());
            }
        }

        private void addEdge(int from, int to, long capacity) {
            Edge forward = new Edge(to, graph.get(to).size(), capacity);
            Edge reverse = new Edge(from, graph.get(from).size(), 0L);
            graph.get(from).add(forward);
            graph.get(to).add(reverse);
        }

        private long maxFlow(int source, int sink, long limit) {
            long total = 0L;
            while (total < limit && buildLevels(source, sink)) {
                cursors = new int[graph.size()];
                long pushed;
                while (total < limit
                        && (pushed = push(source, sink, limit - total)) > 0L) {
                    total += pushed;
                }
            }
            return total;
        }

        private boolean buildLevels(int source, int sink) {
            levels = new int[graph.size()];
            java.util.Arrays.fill(levels, -1);
            levels[source] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(source);

            while (!queue.isEmpty()) {
                int node = queue.removeFirst();
                for (Edge edge : graph.get(node)) {
                    if (edge.capacity <= 0L || levels[edge.to] >= 0) {
                        continue;
                    }
                    levels[edge.to] = levels[node] + 1;
                    queue.addLast(edge.to);
                }
            }
            return levels[sink] >= 0;
        }

        private long push(int node, int sink, long available) {
            if (node == sink) {
                return available;
            }

            List<Edge> edges = graph.get(node);
            while (cursors[node] < edges.size()) {
                Edge edge = edges.get(cursors[node]);
                if (edge.capacity > 0L && levels[edge.to] == levels[node] + 1) {
                    long pushed = push(edge.to, sink, Math.min(available, edge.capacity));
                    if (pushed > 0L) {
                        edge.capacity -= pushed;
                        graph.get(edge.to).get(edge.reverseIndex).capacity += pushed;
                        return pushed;
                    }
                }
                cursors[node]++;
            }
            return 0L;
        }

        private static final class Edge {
            private final int to;
            private final int reverseIndex;
            private long capacity;

            private Edge(int to, int reverseIndex, long capacity) {
                this.to = to;
                this.reverseIndex = reverseIndex;
                this.capacity = capacity;
            }
        }
    }
}
