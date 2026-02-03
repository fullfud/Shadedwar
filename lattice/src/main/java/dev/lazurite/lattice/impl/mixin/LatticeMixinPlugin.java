package dev.lazurite.lattice.impl.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class LatticeMixinPlugin implements IMixinConfigPlugin {
    private static final String FIX_RENDER_LEVEL_MIXIN =
        "dev.lazurite.lattice.impl.client.mixin.fix.render.LevelRendererMixin";

    private boolean embeddiumPresent;

    @Override
    public void onLoad(final String mixinPackage) {
        // Embeddium (Sodium) rewrites LevelRenderer; our redirect in fix.render.LevelRendererMixin
        // collides with their mixin. If Embeddium/Oculus is present, skip that mixin.
        embeddiumPresent =
            isModLoaded("embeddium")
                || isModLoaded("oculus")
                || isClassPresent("me.jellysquid.mods.sodium.mixin.core.render.world.WorldRendererMixin");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        if (embeddiumPresent && FIX_RENDER_LEVEL_MIXIN.equals(mixinClassName)) {
            return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
        final String targetClassName,
        final ClassNode targetClass,
        final String mixinClassName,
        final IMixinInfo mixinInfo
    ) {
    }

    @Override
    public void postApply(
        final String targetClassName,
        final ClassNode targetClass,
        final String mixinClassName,
        final IMixinInfo mixinInfo
    ) {
    }

    private static boolean isClassPresent(final String className) {
        try {
            Class.forName(className, false, LatticeMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isModLoaded(final String modId) {
        try {
            final Class<?> modListClass = Class.forName("net.minecraftforge.fml.ModList");
            final Object modList = modListClass.getMethod("get").invoke(null);
            return (boolean) modListClass.getMethod("isLoaded", String.class).invoke(modList, modId);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
