package dev.engine_room.flywheel.backend.engine.instancing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.engine_room.flywheel.api.backend.Engine;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.backend.MobileOptions;
import dev.engine_room.flywheel.backend.Samplers;
import dev.engine_room.flywheel.backend.compile.ContextShader;
import dev.engine_room.flywheel.backend.compile.InstancingPrograms;
import dev.engine_room.flywheel.backend.compile.PipelineCompiler;
import dev.engine_room.flywheel.backend.engine.AbstractInstancer;
import dev.engine_room.flywheel.backend.engine.CommonCrumbling;
import dev.engine_room.flywheel.backend.engine.DrawManager;
import dev.engine_room.flywheel.backend.engine.GroupKey;
import dev.engine_room.flywheel.backend.engine.InstancerKey;
import dev.engine_room.flywheel.backend.engine.LightStorage;
import dev.engine_room.flywheel.backend.engine.MaterialEncoder;
import dev.engine_room.flywheel.backend.engine.MaterialRenderState;
import dev.engine_room.flywheel.backend.engine.MeshPool;
import dev.engine_room.flywheel.backend.engine.TextureBinder;
import dev.engine_room.flywheel.backend.engine.embed.Environment;
import dev.engine_room.flywheel.backend.engine.embed.EnvironmentStorage;
import dev.engine_room.flywheel.backend.engine.indirect.OitFramebuffer;
import dev.engine_room.flywheel.backend.engine.uniform.Uniforms;
import dev.engine_room.flywheel.backend.gl.array.GlVertexArray;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelBakery;

public class InstancedDrawManager extends DrawManager<InstancedInstancer<?>> {
	/**
	 * Draw order, most significant key first:
	 *
	 * <ol>
	 *     <li>{@code bias}. The API promises instancers render in ascending bias, so this key
	 *     cannot move - every other key is a tie-break underneath it.</li>
	 *     <li>Context shader, then instance type. Together with the material these three name
	 *     the GL program, so grouping by them is what lets {@link #submitDraws} find the program
	 *     it already has bound instead of calling {@code glUseProgram} for every draw.</li>
	 *     <li>Material, through {@link MaterialRenderState#COMPARATOR}, which sorts by
	 *     {@link Transparency} first. Because {@link Transparency#OPAQUE} is the first constant,
	 *     every solid part of a bucket is drawn before any blended part, and it also does so
	 *     without ever unbinding the shader.</li>
	 *     <li>Mesh index, so the parts of one model that share a material keep the order the
	 *     model defines them in.</li>
	 * </ol>
	 *
	 * <p>Written as a plain method rather than a chained comparator so the key order is visible
	 * in one place and the compiler never has to guess which {@code thenComparing} overload a
	 * lambda belongs to.
	 */
	private static final Comparator<InstancedDraw> DRAW_COMPARATOR = InstancedDrawManager::compareDraws;

	private static int compareDraws(InstancedDraw lhs, InstancedDraw rhs) {
		int cmp = Integer.compare(lhs.bias(), rhs.bias());
		if (cmp != 0) {
			return cmp;
		}

		cmp = Integer.compare(lhs.groupKey.environment()
				.contextShader()
				.ordinal(), rhs.groupKey.environment()
						.contextShader()
						.ordinal());
		if (cmp != 0) {
			return cmp;
		}

		// Namespace then path rather than Comparable: it groups by the shader each instance type
		// compiles against without betting on an interface the class may not declare, and a pair
		// that fails to group only costs one extra bind, never a wrong one - submitDraws compares
		// the program object itself.
		var lhsType = lhs.groupKey.instanceType()
				.vertexShader();
		var rhsType = rhs.groupKey.instanceType()
				.vertexShader();

		cmp = lhsType.getNamespace().compareTo(rhsType.getNamespace());
		if (cmp != 0) {
			return cmp;
		}

		cmp = lhsType.getPath().compareTo(rhsType.getPath());
		if (cmp != 0) {
			return cmp;
		}

		cmp = MaterialRenderState.COMPARATOR.compare(lhs.material(), rhs.material());
		if (cmp != 0) {
			return cmp;
		}

		return Integer.compare(lhs.indexOfMeshInModel(), rhs.indexOfMeshInModel());
	}

	private final List<InstancedDraw> allDraws = new ArrayList<>();
	private boolean needSort = false;

	private final List<InstancedDraw> draws = new ArrayList<>();
	private final List<InstancedDraw> oitDraws = new ArrayList<>();

	private final InstancingPrograms programs;
	/**
	 * A map of vertex types to their mesh pools.
	 */
	private final MeshPool meshPool;
	private final GlVertexArray vao;
	private final InstancedLight light;

	@Nullable
	private final OitFramebuffer oitFramebuffer;

	public InstancedDrawManager(InstancingPrograms programs) {
		programs.acquire();
		this.programs = programs;

		meshPool = new MeshPool();
		vao = GlVertexArray.create();
		light = new InstancedLight();

		meshPool.bind(vao);

		oitFramebuffer = MobileOptions.oit ? new OitFramebuffer(programs.oitPrograms()) : null;

	}

	@Override
	public void render(LightStorage lightStorage, EnvironmentStorage environmentStorage) {
		super.render(lightStorage, environmentStorage);

		this.instancers.values()
				.removeIf(instancer -> {
			if (instancer.instanceCount() == 0) {
				instancer.delete();
				return true;
			} else {
				instancer.updateBuffer();
				return false;
			}
		});

		// Remove the draw calls for any instancers we deleted.
		needSort |= allDraws.removeIf(InstancedDraw::deleted);

		if (needSort) {
			allDraws.sort(DRAW_COMPARATOR);

			draws.clear();
			oitDraws.clear();

			for (var draw : allDraws) {
				if (MobileOptions.oit && draw.material()
						.transparency() == Transparency.ORDER_INDEPENDENT) {
					oitDraws.add(draw);
				} else {
					draws.add(draw);
				}
			}

			needSort = false;
		}

		meshPool.flush();

		light.flush(lightStorage);

		if (allDraws.isEmpty()) {
			return;
		}

		Uniforms.bindAll();
		vao.bindForDraw();
		TextureBinder.bindLightAndOverlay();
		light.bind();

		submitDraws();

		if (oitFramebuffer != null && !oitDraws.isEmpty()) {
			oitFramebuffer.prepare();

			oitFramebuffer.depthRange();

			submitOitDraws(PipelineCompiler.OitMode.DEPTH_RANGE);

			oitFramebuffer.renderTransmittance();

			submitOitDraws(PipelineCompiler.OitMode.GENERATE_COEFFICIENTS);

			oitFramebuffer.renderDepthFromTransmittance();

			// Need to bind this again because we just drew a full screen quad for OIT.
			vao.bindForDraw();

			oitFramebuffer.accumulate();

			submitOitDraws(PipelineCompiler.OitMode.EVALUATE);

			oitFramebuffer.composite();
		}

		MaterialRenderState.reset();
		TextureBinder.resetLightAndOverlay();
	}

	/**
	 * Draw every opaque and ordinarily blended draw call, skipping whatever the previous draw
	 * already put in place.
	 *
	 * <p>All the tracked state is local to this call on purpose. The OIT path interleaves these
	 * loops with full screen passes of its own, and those bind their own program and touch the
	 * framebuffer, so state carried across loops could have the loop swear a program or a
	 * material is bound when it no longer is. Starting each loop cold costs one redundant bind
	 * and buys a correctness that cannot be argued with.
	 */
	private void submitDraws() {
		GlProgram boundProgram = null;
		Material uploadedMaterial = null;
		Environment setupEnvironment = null;
		Material statedMaterial = null;
		int uploadedBaseVertex = -1;

		for (var drawCall : draws) {
			var material = drawCall.material();
			var groupKey = drawCall.groupKey;
			var environment = groupKey.environment();

			var program = programs.get(groupKey.instanceType(), environment.contextShader(), material, PipelineCompiler.OitMode.OFF);

			if (program != boundProgram) {
				// A freshly bound program has none of our uniforms in it yet.
				program.bind();
				boundProgram = program;

				environment.setupDraw(program);
				setupEnvironment = environment;

				uploadedMaterial = null;
				uploadedBaseVertex = -1;
			} else if (environment != setupEnvironment) {
				environment.setupDraw(program);
				setupEnvironment = environment;
			}

			if (uploadedMaterial == null || !MaterialRenderState.materialEquals(material, uploadedMaterial)) {
				uploadMaterialUniform(program, material);
				uploadedMaterial = material;
			}

			int baseVertex = drawCall.mesh()
					.baseVertex();
			if (baseVertex != uploadedBaseVertex) {
				program.setUInt("_flw_baseVertex", baseVertex);
				uploadedBaseVertex = baseVertex;
			}

			// Plain GL state, independent of which program is bound, so it survives a program
			// switch and is only re-asserted when the material actually changes.
			if (statedMaterial == null || !MaterialRenderState.materialEquals(material, statedMaterial)) {
				MaterialRenderState.setup(material);
				statedMaterial = material;
			}

			Samplers.INSTANCE_BUFFER.makeActive();

			drawCall.render();
		}
	}

	/**
	 * The OIT counterpart of {@link #submitDraws}, with the same local-only state for the same
	 * reason: a full screen pass runs between every call and rebinds a program of its own.
	 */
	private void submitOitDraws(PipelineCompiler.OitMode mode) {
		GlProgram boundProgram = null;
		Material uploadedMaterial = null;
		Environment setupEnvironment = null;
		Material statedMaterial = null;
		int uploadedBaseVertex = -1;

		for (var drawCall : oitDraws) {
			var material = drawCall.material();
			var groupKey = drawCall.groupKey;
			var environment = groupKey.environment();

			var program = programs.get(groupKey.instanceType(), environment.contextShader(), material, mode);

			if (program != boundProgram) {
				program.bind();
				boundProgram = program;

				environment.setupDraw(program);
				setupEnvironment = environment;

				uploadedMaterial = null;
				uploadedBaseVertex = -1;
			} else if (environment != setupEnvironment) {
				environment.setupDraw(program);
				setupEnvironment = environment;
			}

			if (uploadedMaterial == null || !MaterialRenderState.materialEquals(material, uploadedMaterial)) {
				uploadMaterialUniform(program, material);
				uploadedMaterial = material;
			}

			int baseVertex = drawCall.mesh()
					.baseVertex();
			if (baseVertex != uploadedBaseVertex) {
				program.setUInt("_flw_baseVertex", baseVertex);
				uploadedBaseVertex = baseVertex;
			}

			// setupOit reads the write mask but not the blend mode, and materialEquals compares
			// both, so re-running it on a material change is conservative rather than excessive.
			if (statedMaterial == null || !MaterialRenderState.materialEquals(material, statedMaterial)) {
				MaterialRenderState.setupOit(material);
				statedMaterial = material;
			}

			Samplers.INSTANCE_BUFFER.makeActive();

			drawCall.render();
		}
	}

	@Override
	public void delete() {
		instancers.values()
				.forEach(InstancedInstancer::delete);

		allDraws.forEach(InstancedDraw::delete);
		allDraws.clear();
		draws.clear();
		oitDraws.clear();

		meshPool.delete();
		programs.release();
		vao.delete();

		light.delete();

		if (oitFramebuffer != null) {
			oitFramebuffer.delete();
		}

		super.delete();
	}

	@Override
	protected <I extends Instance> InstancedInstancer<I> create(InstancerKey<I> key) {
		return new InstancedInstancer<>(key, new AbstractInstancer.Recreate<>(key, this));
	}

	@Override
	protected <I extends Instance> void initialize(InstancerKey<I> key, InstancedInstancer<?> instancer) {
		instancer.init();

		var meshes = key.model()
				.meshes();
		for (int i = 0; i < meshes.size(); i++) {
			var entry = meshes.get(i);
			var mesh = meshPool.alloc(entry.mesh());

			GroupKey<?> groupKey = new GroupKey<>(key.type(), key.environment());
			InstancedDraw instancedDraw = new InstancedDraw(instancer, mesh, groupKey, entry.material(), key.bias(), i);

			allDraws.add(instancedDraw);
			needSort = true;
			instancer.addDrawCall(instancedDraw);
		}
	}

	@Override
	public void renderCrumbling(List<Engine.CrumblingBlock> crumblingBlocks) {
		// Sort draw calls into buckets, so we don't have to do as many shader binds.
		var byType = doCrumblingSort(crumblingBlocks, handle -> {
			// AbstractInstancer directly implement HandleState, so this check is valid.
			if (handle instanceof InstancedInstancer<?> instancer) {
				return instancer;
			}
			// This rejects instances that were created by a different engine,
			// and also instances that are hidden or deleted.
			return null;
		});

		if (byType.isEmpty()) {
			return;
		}

		var crumblingMaterial = SimpleMaterial.builder();

		Uniforms.bindAll();
		vao.bindForDraw();
		TextureBinder.bindLightAndOverlay();

		for (var groupEntry : byType.entrySet()) {
			var byProgress = groupEntry.getValue();

			GroupKey<?> shader = groupEntry.getKey();

			for (var progressEntry : byProgress.int2ObjectEntrySet()) {
				Samplers.CRUMBLING.makeActive();
				TextureBinder.bind(ModelBakery.BREAKING_LOCATIONS.get(progressEntry.getIntKey()));

				for (var instanceHandlePair : progressEntry.getValue()) {
					InstancedInstancer<?> instancer = instanceHandlePair.getFirst();
					var index = instanceHandlePair.getSecond().index;

					for (InstancedDraw draw : instancer.draws()) {
						CommonCrumbling.applyCrumblingProperties(crumblingMaterial, draw.material());
						var program = programs.get(shader.instanceType(), ContextShader.CRUMBLING, crumblingMaterial, PipelineCompiler.OitMode.OFF);
						program.bind();
						program.setInt("_flw_baseInstance", index);
						uploadMaterialUniform(program, crumblingMaterial);

						MaterialRenderState.setup(crumblingMaterial);

						Samplers.INSTANCE_BUFFER.makeActive();

						draw.renderOne();
					}
				}
			}
		}

		MaterialRenderState.reset();
		TextureBinder.resetLightAndOverlay();
	}

	@Override
	public void triggerFallback() {
		InstancingPrograms.kill();
		Minecraft.getInstance().levelRenderer.allChanged();
	}

	@Override
	public MeshPool meshPool() {
		return meshPool;
	}

	public static void uploadMaterialUniform(GlProgram program, Material material) {
		int packedFogAndCutout = MaterialEncoder.packUberShader(material);
		int packedMaterialProperties = MaterialEncoder.packProperties(material);
		program.setUVec2("_flw_packedMaterial", packedFogAndCutout, packedMaterialProperties);
	}
}
