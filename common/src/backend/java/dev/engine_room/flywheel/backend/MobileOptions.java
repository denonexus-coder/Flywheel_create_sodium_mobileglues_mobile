package dev.engine_room.flywheel.backend;

/**
 * Runtime switches aimed at low-power and mobile drivers (PowerVR, Mali, Adreno, and
 * anything reached through a translation layer such as MobileGlues).
 *
 * <p>These are intentionally plain static fields rather than members of {@code FlwConfig}:
 * the classes that read them can be reached before the platform config has been loaded,
 * and the config itself is read while the GL context may not exist yet, so the defaults
 * cannot be derived from the driver there.
 */
public final class MobileOptions {
	/**
	 * True when the GL strings identify a mobile / translation-layer driver.
	 * Populated by {@link dev.engine_room.flywheel.backend.gl.GlCompat} at class init.
	 */
	public static boolean mobileDriver;

	/**
	 * Whether materials using {@code Transparency#ORDER_INDEPENDENT} actually go through
	 * the order-independent path. When false they are rendered as plain translucency,
	 * which is explicitly permitted by {@code Transparency#ORDER_INDEPENDENT}.
	 *
	 * <p>Order-independent transparency needs several color attachments plus a handful of
	 * full screen passes. On a tile-based mobile GPU each pass is a tile load/store, so
	 * turning this off can remove a large amount of bandwidth.
	 */
	public static boolean oit = true;

	/**
	 * Drop every particle at the source. On a weak GPU, with a lot of Create machinery
	 * running, this is usually one of the largest per-frame wins available.
	 */
	public static boolean disableParticles;

	/**
	 * Pause animated atlas sprites (item/block textures) while a GUI is open, so opening
	 * the inventory stops re-uploading animated texture frames every tick.
	 */
	public static boolean freezeGuiItemAnimation;

	/**
	 * Replace the per-span {@code glBufferSubData} instance uploads with a full orphaned
	 * re-upload of the instancer's array (see {@code InstancedInstancer#updateBuffer}).
	 *
	 * <p>On a driver that stalls sub-data updates against in-flight reads this is much
	 * cheaper than patching the spans in place; on a desktop driver that renames buffers
	 * instead, leaving this off is better. Only consulted when {@link #mobileDriver} is set.
	 */
	public static boolean orphanInstanceUpload = true;

	private MobileOptions() {
	}
}
