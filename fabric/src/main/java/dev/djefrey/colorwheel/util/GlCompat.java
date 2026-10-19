package dev.djefrey.colorwheel.util;

import com.mojang.blaze3d.platform.GlUtil;
import dev.djefrey.colorwheel.Colorwheel;

import java.util.Locale;

public class GlCompat
{
    public static final boolean SUPPORTS_OIT = isOitSupported();

    public static void init()
    {

    }

    private static boolean isOitSupported()
    {
        var renderer = GlUtil.getRenderer().toLowerCase(Locale.ROOT);

        // Upstream disables Apple Silicon. Tile-based mobile drivers hit the same wall: the
        // wavelet OIT passes are full-screen multi-target work that thrashes their bandwidth,
        // so MobileGlues is excluded the same way the instancing backend already excludes DSA.
        var isSupported = !renderer.startsWith("apple")
                && !dev.engine_room.flywheel.backend.gl.GlCompat.MOBILE_GLUES;

        Colorwheel.LOGGER.info("Is OIT supported: {}", isSupported);
        return isSupported;
    }
}
