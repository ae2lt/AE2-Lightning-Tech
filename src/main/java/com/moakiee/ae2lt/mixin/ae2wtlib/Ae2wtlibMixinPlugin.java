package com.moakiee.ae2lt.mixin.ae2wtlib;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.neoforged.fml.loading.LoadingModList;

/**
 * Gates optional implementation integrations on ae2wtlib, while the quantum
 * bridge fix also applies to the embedded API used by standalone Tianshu terminals.
 * We check the loading mod list rather than {@code ModList}
 * because mixins are applied before {@code ModList} is initialized.
 */
public final class Ae2wtlibMixinPlugin implements IMixinConfigPlugin {

    private boolean ae2wtlibPresent;
    private boolean ae2wtlibApiPresent;

    @Override
    public void onLoad(String mixinPackage) {
        ae2wtlibPresent = LoadingModList.get().getModFileById("ae2wtlib") != null;
        ae2wtlibApiPresent = LoadingModList.get().getModFileById("ae2wtlib_api") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".WTMenuHostQuantumBridgeMixin")) {
            return ae2wtlibApiPresent;
        }
        return ae2wtlibPresent;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
