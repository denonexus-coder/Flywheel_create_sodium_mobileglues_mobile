package dev.engine_room.flywheel.impl;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.backend.BackendConfig;
import dev.engine_room.flywheel.backend.FlwBackend;
import dev.engine_room.flywheel.backend.MobileOptions;
import dev.engine_room.flywheel.backend.compile.LightSmoothness;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class FabricFlwConfig implements FlwConfig {
	public static final Path PATH = FabricLoader.getInstance()
			.getConfigDir()
			.resolve("flywheel.json");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final boolean LIMIT_UPDATES_DEFAULT = true;
	public static final int WORKER_THREADS_DEFAULT = -1;
	public static final int WORKER_THREADS_MAX = Runtime.getRuntime()
			.availableProcessors();

	public static final boolean OIT_DEFAULT = true;
	public static final boolean DISABLE_PARTICLES_DEFAULT = false;
	public static final boolean FREEZE_GUI_ITEM_ANIMATION_DEFAULT = false;
	public static final boolean ORPHAN_INSTANCE_UPLOAD_DEFAULT = true;
	public static final boolean HIDE_OFFSCREEN_INSTANCES_DEFAULT = true;

	public static final FabricFlwConfig INSTANCE = new FabricFlwConfig(PATH.toFile());

	private final File file;

	public Backend backend = BackendManager.offBackend();
	public boolean useDefaultBackend = true;
	public boolean limitUpdates = LIMIT_UPDATES_DEFAULT;
	public int workerThreads = WORKER_THREADS_DEFAULT;

	public boolean oit = OIT_DEFAULT;
	public boolean disableParticles = DISABLE_PARTICLES_DEFAULT;
	public boolean freezeGuiItemAnimation = FREEZE_GUI_ITEM_ANIMATION_DEFAULT;
	public boolean orphanInstanceUpload = ORPHAN_INSTANCE_UPLOAD_DEFAULT;
	public boolean hideOffscreenInstances = HIDE_OFFSCREEN_INSTANCES_DEFAULT;

	/**
	 * Whether the config file already records that the mobile defaults were settled - the
	 * {@code mobileDefaultsApplied} marker. Pushed to {@link MobileOptions#mobileDefaultsRecorded}
	 * on sync, and written back as {@code true} by {@link #toJson()}.
	 */
	private boolean mobileDefaultsRecorded;

	public final FabricBackendConfig backendConfig = new FabricBackendConfig();

	public FabricFlwConfig(File file) {
		this.file = file;
	}

	@Override
	public Backend backend() {
		if (useDefaultBackend) {
			return BackendManager.defaultBackend();
		}

		return backend;
	}

	@Override
	public boolean limitUpdates() {
		return limitUpdates;
	}

	@Override
	public int workerThreads() {
		return workerThreads;
	}

	@Override
	public BackendConfig backendConfig() {
		return backendConfig;
	}

	public void load() {
		if (file.exists()) {
			try (FileReader reader = new FileReader(file)) {
				fromJson(JsonParser.parseReader(reader));
			} catch (Exception e) {
				FlwImpl.CONFIG_LOGGER.warn("Could not load config from file '{}'", file.getAbsolutePath(), e);
			}
		}
		// In case we found an error in the config file, immediately save to fix it.
		save();
	}

	public void save() {
		try (FileWriter writer = new FileWriter(file)) {
			GSON.toJson(toJson(), writer);
		} catch (Exception e) {
			FlwImpl.CONFIG_LOGGER.warn("Could not save config to file '{}'", file.getAbsolutePath(), e);
		}
	}

	public void fromJson(JsonElement json) {
		if (!(json instanceof JsonObject object)) {
			FlwImpl.CONFIG_LOGGER.warn("Config JSON must be an object");
			backend = BackendManager.offBackend();
			useDefaultBackend = true;
			limitUpdates = LIMIT_UPDATES_DEFAULT;
			workerThreads = WORKER_THREADS_DEFAULT;
			oit = OIT_DEFAULT;
			mobileDefaultsRecorded = false;
			disableParticles = DISABLE_PARTICLES_DEFAULT;
			freezeGuiItemAnimation = FREEZE_GUI_ITEM_ANIMATION_DEFAULT;
			orphanInstanceUpload = ORPHAN_INSTANCE_UPLOAD_DEFAULT;
			hideOffscreenInstances = HIDE_OFFSCREEN_INSTANCES_DEFAULT;
			syncMobileOptions();
			return;
		}

		readBackend(object);
		readLimitUpdates(object);
		readWorkerThreads(object);
		readMobile(object);
		readFlwBackends(object);
		syncMobileOptions();
	}

	private void readBackend(JsonObject object) {
		var backendJson = object.get("backend");
		String msg = null;

		if (backendJson instanceof JsonPrimitive primitive && primitive.isString()) {
			var value = primitive.getAsString();
			if (value.equals(DEFAULT_BACKEND_STR)) {
				backend = BackendManager.offBackend();
				useDefaultBackend = true;
				return;
			}

			try {
				backend = Backend.REGISTRY.getOrThrow(new ResourceLocation(value));
				useDefaultBackend = false;
				return;
			} catch (ResourceLocationException e) {
				msg = "'backend' value '" + value + "' is not a valid resource location";
			} catch (IllegalArgumentException e) {
				msg = "Backend with ID '" + value + "' is not registered";
			} catch (Exception e) {
				// Something else went wrong? This should be dead code.
				msg = "'backend' value '" + value + "' is invalid";
			}
		} else if (backendJson != null) {
			msg = "'backend' value must be a string";
		}

		// Don't log an error if the field is missing.
		if (msg != null) {
			FlwImpl.CONFIG_LOGGER.warn(msg);
		}
		backend = BackendManager.offBackend();
		useDefaultBackend = true;
	}

	private void readLimitUpdates(JsonObject object) {
		var limitUpdatesJson = object.get("limitUpdates");

		if (limitUpdatesJson instanceof JsonPrimitive primitive && primitive.isBoolean()) {
			limitUpdates = primitive.getAsBoolean();
			return;
		} else if (limitUpdatesJson != null) {
			FlwImpl.CONFIG_LOGGER.warn("'limitUpdates' value must be a boolean");
		}

		limitUpdates = LIMIT_UPDATES_DEFAULT;
	}

	private void readWorkerThreads(JsonObject object) {
		var workerThreadsJson = object.get("workerThreads");

		if (workerThreadsJson instanceof JsonPrimitive primitive && primitive.isNumber()) {
			int value = primitive.getAsInt();
			int clamped = Mth.clamp(value, WORKER_THREADS_DEFAULT, WORKER_THREADS_MAX);

			if (clamped != value) {
				FlwImpl.CONFIG_LOGGER.warn("'workerThreads' value of {} is out of range, clamping to {}", value, clamped);
			}

			workerThreads = clamped;
			return;
		} else if (workerThreadsJson != null) {
			FlwImpl.CONFIG_LOGGER.warn("'workerThreads' value must be an integer");
		}

		workerThreads = WORKER_THREADS_DEFAULT;
	}

	private void readFlwBackends(JsonObject object) {
		var flwBackendsJson = object.get("flw_backends");

		if (flwBackendsJson instanceof JsonObject flwBackendsObject) {
			backendConfig.fromJson(flwBackendsObject);
		} else {
			FlwImpl.CONFIG_LOGGER.warn("'flw_backends' value must be an object");
		}
	}

	private void readMobile(JsonObject object) {
		oit = OIT_DEFAULT;
		mobileDefaultsRecorded = false;
		disableParticles = DISABLE_PARTICLES_DEFAULT;
		freezeGuiItemAnimation = FREEZE_GUI_ITEM_ANIMATION_DEFAULT;
		orphanInstanceUpload = ORPHAN_INSTANCE_UPLOAD_DEFAULT;
		hideOffscreenInstances = HIDE_OFFSCREEN_INSTANCES_DEFAULT;

		var mobileJson = object.get("mobile");

		if (mobileJson == null) {
			// The whole section is optional, so don't warn when it is missing.
			return;
		}

		if (!(mobileJson instanceof JsonObject mobile)) {
			FlwImpl.CONFIG_LOGGER.warn("'mobile' value must be an object");
			return;
		}

		// Absent on every profile written before this marker existed, and those are precisely the
		// ones still carrying a desktop default nobody chose. Once it is present the file alone
		// decides, which is what lets the in-game toggle survive a restart.
		var marker = mobile.get("mobileDefaultsApplied");
		mobileDefaultsRecorded = marker instanceof JsonPrimitive primitive && primitive.isBoolean() && primitive.getAsBoolean();

		oit = readBoolean(mobile, "oit", OIT_DEFAULT);
		disableParticles = readBoolean(mobile, "disableParticles", DISABLE_PARTICLES_DEFAULT);
		freezeGuiItemAnimation = readBoolean(mobile, "freezeGuiItemAnimation", FREEZE_GUI_ITEM_ANIMATION_DEFAULT);
		orphanInstanceUpload = readBoolean(mobile, "orphanInstanceUpload", ORPHAN_INSTANCE_UPLOAD_DEFAULT);
		hideOffscreenInstances = readBoolean(mobile, "hideOffscreenInstances", HIDE_OFFSCREEN_INSTANCES_DEFAULT);
	}

	private static boolean readBoolean(JsonObject object, String key, boolean defaultValue) {
		var json = object.get(key);

		if (json instanceof JsonPrimitive primitive && primitive.isBoolean()) {
			return primitive.getAsBoolean();
		} else if (json != null) {
			FlwImpl.CONFIG_LOGGER.warn("'{}' value must be a boolean", key);
		}

		return defaultValue;
	}

	/**
	 * Push the mobile options onto {@link MobileOptions} so the backend can read them
	 * without depending on this class, and without relying on config load order.
	 */
	private void syncMobileOptions() {
		MobileOptions.oit = oit;
		MobileOptions.disableParticles = disableParticles;
		MobileOptions.freezeGuiItemAnimation = freezeGuiItemAnimation;
		MobileOptions.orphanInstanceUpload = orphanInstanceUpload;
		MobileOptions.hideOffscreenInstances = hideOffscreenInstances;

		// Hand over what the file recorded, then let the defaults claim whatever it did not. Doing
		// this here and again from the driver detection makes the outcome independent of which of
		// the two runs first.
		MobileOptions.mobileDefaultsRecorded = mobileDefaultsRecorded;
		MobileOptions.applyMobileDefaults();

		// Take back whatever the defaults decided, so the save persists what is actually in effect
		// instead of the stale desktop value the file arrived with.
		oit = MobileOptions.oit;
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		object.addProperty("backend", useDefaultBackend ? DEFAULT_BACKEND_STR : Backend.REGISTRY.getIdOrThrow(backend).toString());
		object.addProperty("limitUpdates", limitUpdates);
		object.addProperty("workerThreads", workerThreads);
		object.add("flw_backends", backendConfig.toJson());

		JsonObject mobile = new JsonObject();
		mobile.addProperty("oit", oit);
		mobile.addProperty("disableParticles", disableParticles);
		mobile.addProperty("freezeGuiItemAnimation", freezeGuiItemAnimation);
		mobile.addProperty("orphanInstanceUpload", orphanInstanceUpload);
		mobile.addProperty("hideOffscreenInstances", hideOffscreenInstances);
		// From the runtime state rather than the local field: on a profile with no file yet the
		// defaults run without this class ever reading one, and that session must still be able to
		// record itself as settled or the defaults would keep running over the user's choices.
		mobile.addProperty("mobileDefaultsApplied", MobileOptions.mobileDefaultsRecorded);
		object.add("mobile", mobile);

		return object;
	}

	public static class FabricBackendConfig implements BackendConfig {
		public static final LightSmoothness LIGHT_SMOOTHNESS_DEFAULT = LightSmoothness.SMOOTH;

		public LightSmoothness lightSmoothness = LIGHT_SMOOTHNESS_DEFAULT;

		@Override
		public LightSmoothness lightSmoothness() {
			return lightSmoothness;
		}

		public void fromJson(JsonObject object) {
			readLightSmoothness(object);
		}

		private void readLightSmoothness(JsonObject object) {
			var lightSmoothnessJson = object.get("lightSmoothness");
			String msg = null;

			if (lightSmoothnessJson instanceof JsonPrimitive primitive && primitive.isString()) {
				var value = primitive.getAsString();

				for (var item : LightSmoothness.values()) {
					if (item.name()
							.equalsIgnoreCase(value)) {
						lightSmoothness = item;
						return;
					}
				}

				msg = "Unknown 'lightSmoothness' value: " + value;
			} else if (lightSmoothnessJson != null) {
				msg = "'lightSmoothness' value must be a string";
			}

			// Don't log an error if the field is missing.
			if (msg != null) {
				FlwBackend.LOGGER.warn(msg);
			}
			lightSmoothness = LIGHT_SMOOTHNESS_DEFAULT;
		}

		public JsonObject toJson() {
			JsonObject object = new JsonObject();
			object.addProperty("lightSmoothness", lightSmoothness.getSerializedName());
			return object;
		}
	}
}
