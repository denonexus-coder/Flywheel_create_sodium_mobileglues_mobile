package dev.djefrey.colorwheel.fabric;

import dev.djefrey.colorwheel.Colorwheel;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class ClrwlFabric implements ModInitializer
{
    @Override
    public void onInitialize()
    {
        if (!hasIris())
        {
            Colorwheel.LOGGER.info("Iris is not installed; the fused Iris shader backend is inactive");
            return;
        }

        // Colorwheel is vendored into Flywheel in this build, so the two can never be
        // mismatched. The upstream check read a "colorwheel" mod container, which no longer
        // exists once both share the flywheel mod id.
        boolean hasFlywheel = hasFlywheel();

        Colorwheel.init(hasFlywheel);

        if (hasFlywheel)
        {
            ClrwlConfigFabric.INSTANCE.load();
        }
    }

    public static boolean hasIris()
    {
        return FabricLoader.getInstance().isModLoaded("iris");
    }

    public static boolean hasFlywheel()
    {
        return FabricLoader.getInstance().isModLoaded("flywheel");
    }

    public static boolean hasPonder()
    {
        return FabricLoader.getInstance().isModLoaded("ponder");
    }

    public static boolean isFlywheelVersionSupported()
    {
        return true;
    }
}
