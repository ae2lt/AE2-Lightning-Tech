package com.moakiee.ae2lt.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.minecraftforge.fml.loading.LoadingModList;

/** Keeps mixins that target optional addons out of Mixin's target-resolution pass. */
public final class AE2LTMixinConfigPlugin implements IMixinConfigPlugin {
    private static final Map<String, String> REQUIRED_MODS = Map.of(
            "AdvCraftingCpuAccessor", "advanced_ae",
            "AdvCraftingCpuLogicMixin", "advanced_ae",
            "BroadcastFrequencyBandMixin", "ae2cs");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        int separator = mixinClassName.lastIndexOf('.');
        String simpleName = separator >= 0 ? mixinClassName.substring(separator + 1) : mixinClassName;
        String requiredMod = REQUIRED_MODS.get(simpleName);
        return requiredMod == null || isModLoaded(requiredMod);
    }

    private static boolean isModLoaded(String modId) {
        try {
            var loadingMods = LoadingModList.get();
            return loadingMods != null && loadingMods.getModFileById(modId) != null;
        } catch (RuntimeException ignored) {
            // Mixin runs during early startup. If discovery is unavailable, skipping an optional
            // target is safer than loading addon-owned classes that may not exist.
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
    }
}
