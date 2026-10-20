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

	/**
	 * Hide instances that fall outside the view frustum, by pulling them out of the instancer
	 * rather than by skipping their update.
	 *
	 * <p>The instancing backend has no culling of its own: frustum culling for instances lives in
	 * the indirect backend, which runs on compute shaders and therefore cannot run through a
	 * translation layer. Without this the backend submits every instance it has been given,
	 * including the block entities behind the camera.
	 *
	 * <p>The frustum test itself is not new - the visuals have always run it - but until now it
	 * only stopped the transform from being written, which leaves the instance in the draw. Hiding
	 * it instead removes it from the instancer, so it costs no draw call and is left out of the
	 * per-frame rewrite of the instance array.
	 */
	public static boolean hideOffscreenInstances = true;

	/**
	 * True once the config file has recorded that the mobile defaults have been settled.
	 *
	 * <p>This exists because the platform config writes every mobile key on each save, so an
	 * install that has run before already carries a value for {@link #oit} even though nobody
	 * ever chose one - that value is just the desktop default echoed back. Comparing against a
	 * stored value can never separate the two, so instead the first load applies the mobile
	 * defaults exactly once and the save marks them as dealt with. From that point on the file
	 * is the only authority, which is what makes the in-game toggle stick across restarts.
	 *
	 * <p>False until the config says otherwise, so a profile with no file at all still gets the
	 * defaults.
	 */
	public static boolean mobileDefaultsRecorded;

	private MobileOptions() {
	}

	/**
	 * Apply the mobile defaults to the switches that have one, once per profile.
	 *
	 * <p>Called from both places that can run first - when the config is loaded, and when the GL
	 * strings are finally read. Neither cares which came first: the first call through does the
	 * work and the file marks it done on the next save, so a config written before the GL context
	 * existed can still have its stale desktop default corrected.
	 */
	public static void applyMobileDefaults() {
		if (mobileDefaultsRecorded) {
			return;
		}

		if (mobileDriver) {
			oit = false;
		}

		// Resolved for this session either way. The save writes this back out, so a profile that
		// had no marker gets one and stops re-running the defaults over the user's choices.
		mobileDefaultsRecorded = true;
	}
}
