package dev.engine_room.flywheel.backend;

import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.backend.compile.IndirectPrograms;
import dev.engine_room.flywheel.backend.compile.InstancingPrograms;
import dev.engine_room.flywheel.backend.engine.EngineImpl;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectDrawManager;
import dev.engine_room.flywheel.backend.engine.instancing.InstancedDrawManager;
import dev.engine_room.flywheel.backend.gl.Driver;
import dev.engine_room.flywheel.backend.gl.GlCompat;
import dev.engine_room.flywheel.lib.backend.SimpleBackend;
import dev.engine_room.flywheel.lib.util.ResourceUtil;
import dev.engine_room.flywheel.lib.util.ShadersModHelper;

public final class Backends {
	/**
	 * Use GPU instancing to render everything.
	 */
	public static final Backend INSTANCING = SimpleBackend.builder()
			.engineFactory(level -> new EngineImpl(level, new InstancedDrawManager(InstancingPrograms.get()), 256))
			.priority(500)
			.supported(() -> GlCompat.SUPPORTS_INSTANCING && InstancingPrograms.allLoaded() && !ShadersModHelper.isShaderPackInUse())
			.register(ResourceUtil.rl("instancing"));

	/**
	 * Use Compute shaders to cull instances. Disabled on mobile/translation layers and when shaders are in use.
	 */
	public static final Backend INDIRECT = SimpleBackend.builder()
			.engineFactory(level -> new EngineImpl(level, new IndirectDrawManager(IndirectPrograms.get()), 256))
			.priority(() -> 1)
			.supported(() -> !GlCompat.MOBILE_GLUES && GlCompat.SUPPORTS_INDIRECT && IndirectPrograms.allLoaded() && !ShadersModHelper.isShaderPackInUse())
			.register(ResourceUtil.rl("indirect"));

	private Backends() {
	}

	public static void init() {
	}
}
