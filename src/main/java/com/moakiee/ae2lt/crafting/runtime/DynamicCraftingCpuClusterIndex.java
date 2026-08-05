package com.moakiee.ae2lt.crafting.runtime;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

/**
 * Identity-based index for grid nodes that can dynamically expose an extended crafting CPU cluster.
 *
 * <p>The provider node remains indexed while it returns {@code null}. A later refresh therefore
 * observes both {@code null -> cluster} activation and replacement of one cluster instance with
 * another without requiring the grid node itself to be removed and added again.</p>
 */
public final class DynamicCraftingCpuClusterIndex<P, C> {
    private final Set<P> providers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<C> clusters = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<C> clustersView = Collections.unmodifiableSet(clusters);
    private final Set<C> refreshedClusters = Collections.newSetFromMap(new IdentityHashMap<>());

    public boolean addProvider(P provider) {
        return providers.add(provider);
    }

    public boolean removeProvider(P provider) {
        return providers.remove(provider);
    }

    public void replaceProviders(Iterable<? extends P> replacements) {
        providers.clear();
        for (var provider : replacements) {
            providers.add(provider);
        }
    }

    /**
     * Re-resolves every provider and notifies {@code onAdded} exactly once for each newly observed
     * cluster identity.
     *
     * @return whether the active cluster identity set changed
     */
    public boolean refresh(Function<? super P, @Nullable ? extends C> resolver,
                           Consumer<? super C> onAdded) {
        refreshedClusters.clear();
        for (var provider : providers) {
            var cluster = resolver.apply(provider);
            if (cluster != null) {
                refreshedClusters.add(cluster);
            }
        }

        boolean changed = !clusters.equals(refreshedClusters);
        for (var iterator = clusters.iterator(); iterator.hasNext(); ) {
            if (!refreshedClusters.contains(iterator.next())) {
                iterator.remove();
            }
        }
        for (var cluster : refreshedClusters) {
            if (clusters.add(cluster)) {
                onAdded.accept(cluster);
            }
        }
        return changed;
    }

    public Set<C> clusters() {
        return clustersView;
    }
}
