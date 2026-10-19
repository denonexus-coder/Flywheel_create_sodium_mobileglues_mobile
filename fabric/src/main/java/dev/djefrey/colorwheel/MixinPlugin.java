package dev.djefrey.colorwheel;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class MixinPlugin implements IMixinConfigPlugin
{
    Version FLW_VERSION;
    Version FLW_V10006;
    boolean IRIS_LOADED;

    @Override
    public void onLoad(String mixinPackage)
    {
         IRIS_LOADED = net.fabricmc.loader.api.FabricLoader.getInstance()
                 .isModLoaded("iris");
         FLW_VERSION = ClrwlXplat.INSTANCE.getFlywheelVersion();
         FLW_V10006 = new Version(1, 0, 6);
    }

    @Override
    @Nullable
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        // Colorwheel is vendored into Flywheel, so these configs ship even for players who
        // never install Iris. Without Iris none of the mixin targets exist and the configs
        // are marked "required", which would abort the game, so keep the whole set inert.
        if (!IRIS_LOADED)
        {
            return false;
        }

        if (mixinClassName.contains(".flw."))
        {
            if (FLW_VERSION == null)
            {
                return false;
            }

            if (mixinClassName.contains("v10000"))
            {
                return FLW_VERSION.compareTo(FLW_V10006) < 0;
            }
            else if (mixinClassName.contains("v10006"))
            {
                return FLW_VERSION.compareTo(FLW_V10006) >= 0;
            }
        }
        else if (mixinClassName.contains(".create."))
        {
            return ClrwlXplat.INSTANCE.doesHaveCreate();
        }
        else if (mixinClassName.contains(".ponder."))
        {
            return ClrwlXplat.INSTANCE.doesHavePonder();
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets)
    {
    }

    @Override
    @Nullable
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }
}
