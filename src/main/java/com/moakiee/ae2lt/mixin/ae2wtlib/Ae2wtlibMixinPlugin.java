package com.moakiee.ae2lt.mixin.ae2wtlib;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.neoforged.fml.loading.LoadingModList;

/**
 * Gates the ae2wtlib integration mixins so they only apply when ae2wtlib is
 * actually present. The mixin classes reference ae2wtlib API types (only on the
 * compile classpath), so applying them without the mod present would fail to
 * load the target class. We check the loading mod list rather than {@code ModList}
 * because mixins are applied before {@code ModList} is initialized.
 */
public final class Ae2wtlibMixinPlugin implements IMixinConfigPlugin {

    private boolean ae2wtlibPresent;

    @Override
    public void onLoad(String mixinPackage) {
        ae2wtlibPresent = LoadingModList.get().getModFileById("ae2wtlib") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
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
