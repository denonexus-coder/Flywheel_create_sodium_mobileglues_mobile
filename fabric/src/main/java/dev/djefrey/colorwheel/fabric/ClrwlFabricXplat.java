package dev.djefrey.colorwheel.fabric;

import dev.djefrey.colorwheel.ClrwlXplat;
import dev.djefrey.colorwheel.Colorwheel;
import dev.djefrey.colorwheel.Version;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClrwlFabricXplat implements ClrwlXplat
{
    // Version of the vendored Colorwheel sources, formatted as the COLORWHEEL_VERSION macro
    // shaderpacks test against. There is no "colorwheel" mod container to read this from
    // once both share the flywheel mod id, so keep it in step with the upstream release
    // these sources were taken from.
    public static final String VENDORED_VERSION_FORMATTED = "10300";

    private final Pattern VERSION_REGEX = Pattern.compile("(\\d+).(\\d+).(\\d+).*");

    @Override
    public String getFormattedVersion()
    {
        var clrwlOpt = FabricLoader.getInstance().getModContainer(Colorwheel.MOD_ID);

        if (clrwlOpt.isEmpty())
        {
            return VENDORED_VERSION_FORMATTED;
        }

        ModContainer clrwl = clrwlOpt.get();

        String version = clrwl.getMetadata().getVersion().getFriendlyString();
        Matcher matcher = VERSION_REGEX.matcher(version);

        if (!matcher.matches())
        {
            throw new IllegalStateException("Could not parse Colorwheel mod version");
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));

        return "%d%02d%02d".formatted(major, minor, patch);
    }

    private Boolean hasFlywheel = null;

    @Override
    public boolean doesHaveFlywheel()
    {
        if (hasFlywheel == null)
        {
            hasFlywheel = ClrwlFabric.hasFlywheel();
        }

        return hasFlywheel;
    }

    private Version flwVersion = null;

    @Override
    public Version getFlywheelVersion()
    {
        if (flwVersion != null)
        {
            return flwVersion;
        }

        var modContainer = FabricLoader.getInstance().getModContainer("flywheel");

        if (modContainer.isEmpty())
        {
            return null;
        }

        String version = modContainer.get().getMetadata().getVersion().getFriendlyString();
        Matcher matcher = VERSION_REGEX.matcher(version);

        if (!matcher.matches())
        {
            throw new IllegalStateException("Could not parse Flywheel mod version");
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));

        flwVersion = new Version(major, minor, patch);
        return flwVersion;
    }

    @Override
    public boolean doesHaveCreate()
    {
        return FabricLoader.getInstance().isModLoaded("create");
    }

    @Override
    public boolean doesHavePonder()
    {
        return FabricLoader.getInstance().isModLoaded("ponder");
    }
}
