package com.exiledradio.cavesandcliffsbackport.height.asm;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * The plugin of {@code mixins.cavesandcliffsbackport.stellarcore.json}, added only when StellarCore is installed
 * ({@link StellarCoreCompat}). Mixin calls {@link #postApply} after every mixin on {@code World} is applied, StellarCore's
 * {@code @Overwrite} included; that is where its build-height check is pointed at the world's height.
 */
public final class StellarCoreMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
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
        if ("net.minecraft.world.World".equals(targetClassName)) {
            StellarCoreCompat.postApply(targetClass);
        }
    }
}
