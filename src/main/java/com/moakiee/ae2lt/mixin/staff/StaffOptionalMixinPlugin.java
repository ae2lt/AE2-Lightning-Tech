package com.moakiee.ae2lt.mixin.staff;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class StaffOptionalMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        String mod = target.startsWith("vectorwing.farmersdelight.") ? "farmersdelight"
                : target.startsWith("reliquary.") ? "reliquary"
                : target.startsWith("chanceCubes.") ? "chancecubes" : "apothic_enchanting";
        return LoadingModList.get().getModFileById(mod) != null;
    }
    @Override public void acceptTargets(Set<String> targets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
