package dev.engine_room.flywheel.impl;

import java.util.ArrayList;

import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.backend.Backends;
import dev.engine_room.flywheel.backend.gl.GlCompat;
import dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl;
import dev.engine_room.flywheel.lib.backend.SimpleBackend;
import dev.engine_room.flywheel.lib.util.ResourceUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;

public final class BackendManagerImpl {
	public static final Backend OFF_BACKEND = SimpleBackend.builder()
			.engineFactory(level -> {
				throw new UnsupportedOperationException("Cannot create engine when backend is off.");
			})
			.supported(() -> true)
			.register(ResourceUtil.rl("off"));

	private static Backend backend = OFF_BACKEND;

	private BackendManagerImpl() {
	}

	public static Backend currentBackend() {
		return backend;
	}

	public static boolean isBackendOn() {
		return backend != OFF_BACKEND;
	}

	// Don't store this statically because backends can theoretically change their priorities at runtime.
	private static ArrayList<Backend> backendsByPriority() {
		var backends = new ArrayList<>(Backend.REGISTRY.getAll());

		// Sort with keys backwards so that the highest priority is first.
		backends.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
		return backends;
	}

	public static Backend defaultBackend() {
		var backendsByPriority = backendsByPriority();
		if (backendsByPriority.isEmpty()) {
			// This probably shouldn't happen, but fail gracefully.
			FlwImpl.LOGGER.warn("No backends registered, defaulting to 'flywheel:off'");
			return OFF_BACKEND;
		}

		return backendsByPriority.get(0);
	}

	private static void chooseBackend() {
		var preferred = FlwConfig.INSTANCE.backend();
		if (preferred.isSupported()) {
			backend = preferred;
			logChosenBackend(backend, false);
			return;
		}

		var backendsByPriority = backendsByPriority();

		var startIndex = backendsByPriority.indexOf(preferred) + 1;

		// For safety in case we don't find anything
		backend = OFF_BACKEND;
		for (int i = startIndex; i < backendsByPriority.size(); i++) {
			var candidate = backendsByPriority.get(i);
			if (candidate.isSupported()) {
				backend = candidate;
				break;
			}
		}

		FlwImpl.LOGGER.warn("Flywheel backend fell back from '{}' to '{}'", Backend.REGISTRY.getIdOrThrow(preferred), Backend.REGISTRY.getIdOrThrow(backend));
		logChosenBackend(backend, true);
	}

	/**
	 * Logs the chosen Flywheel backend so players and developers can immediately confirm
	 * which rendering path is active.
	 *
	 * <p>On MobileGlues/PowerVR the indirect (compute) backend is disabled at the
	 * {@link dev.engine_room.flywheel.backend.gl.GlCompat} level, so the instancing
	 * backend is always selected.  This message confirms that path is live.
	 */
	private static void logChosenBackend(Backend chosen, boolean wasFallback) {
		ResourceLocation id = Backend.REGISTRY.getId(chosen);
		String idStr = id != null ? id.toString() : "[unregistered]";

		if (chosen == Backends.INSTANCING) {
			boolean mobile = GlCompat.MOBILE_GLUES;
			FlwImpl.LOGGER.info(
					"[Flywheel] Backend ATIVO: {} (GPU instancing via VBO).{}",
					idStr,
					mobile
							? " MobileGlues detectado — INDIRECT (compute shaders) bloqueado; INSTANCING é a via rápida."
							: ""
			);
		} else if (chosen == OFF_BACKEND) {
			FlwImpl.LOGGER.warn("[Flywheel] Backend: OFF — nenhuma instância será renderizada via Flywheel.");
		} else {
			FlwImpl.LOGGER.info("[Flywheel] Backend ATIVO: {}{}", idStr,
					wasFallback ? " (fallback automático)" : "");
		}
	}

	public static String getBackendString() {
		ResourceLocation backendId = Backend.REGISTRY.getId(backend);
		if (backendId == null) {
			return "[unregistered]";
		}
		return backendId.toString();
	}

	public static void init() {
	}

	public static void onEndClientResourceReload(boolean didError) {
		if (didError) {
			return;
		}

		chooseBackend();
		VisualizationManagerImpl.resetAll();
	}

	public static void onReloadLevelRenderer(ClientLevel level) {
		chooseBackend();
		VisualizationManagerImpl.reset(level);
	}
}
