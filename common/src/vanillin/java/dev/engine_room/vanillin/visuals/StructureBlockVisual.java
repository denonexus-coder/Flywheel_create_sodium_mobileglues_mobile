package dev.engine_room.vanillin.visuals;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.backend.MobileOptions;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.LineModelBuilder;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;

public class StructureBlockVisual extends AbstractBlockEntityVisual<StructureBlockEntity> implements SimpleDynamicVisual {
	// LineModelBuilder writes white vertices and the line shader multiplies in the instance
	// color, so every color family needs its own model and instance. Vanilla's
	// LevelRenderer.renderLineBox mixes its two color arguments per axis: x edges take
	// (r1, g2, b3), y edges (r2, g1, b3), z edges (r2, g2, b1), which turns the structure
	// block's main box call (0.9, 0.9, 0.9 / 0.5, 0.5, 0.5) into the three tints below.
	private static final float[] COLOR_X_EDGES = {0.9f, 0.5f, 0.5f};
	private static final float[] COLOR_Y_EDGES = {0.5f, 0.9f, 0.5f};
	private static final float[] COLOR_Z_EDGES = {0.5f, 0.5f, 0.9f};
	// The invisible-block overlay passes identical values for both color arguments.
	private static final float[] COLOR_AIR = {0.5f, 0.5f, 1.0f};
	private static final float[] COLOR_VOID = {1.0f, 0.75f, 0.75f};
	private static final float[] COLOR_BARRIER = {1.0f, 0.0f, 0.0f};
	private static final float[] COLOR_LIGHT = {1.0f, 1.0f, 0.0f};

	// Rescan the structure volume at most once per second. Vanilla rescans every frame, but it
	// streams straight to one buffer; rebuilding instanced models that often would be far
	// hotter than an editor overlay needs to be.
	private static final long RESCAN_INTERVAL_TICKS = 20L;

	private final TransformedInstance[] mainInstances = new TransformedInstance[3];
	private final Model[] mainModels = new Model[3];
	private final TransformedInstance[] invisInstances = new TransformedInstance[4];
	private final Model[] invisModels = new Model[4];

	@Nullable
	private Snapshot lastSnapshot;
	private boolean mainDirty = true;
	private boolean invisDirty = true;
	private long lastRescan = Long.MIN_VALUE;

	public StructureBlockVisual(VisualizationContext ctx, StructureBlockEntity blockEntity, float partialTick) {
		super(ctx, blockEntity, partialTick);
	}

	@Override
	public void beginFrame(Context context) {
		Snapshot snapshot = new Snapshot(blockEntity.getStructurePos(), blockEntity.getStructureSize(), blockEntity.getMode(), blockEntity.getMirror(), blockEntity.getRotation(), blockEntity.getShowAir(), blockEntity.getShowBoundingBox());

		// Reconcile instead of only hiding: writing only when the answer changes keeps hidden
		// instances from being re-marked every frame, and brings them back if the option is
		// switched off at runtime. Each instance also hides when the debug lines are not
		// allowed for this player or mode, which vanilla gates per frame the same way.
		boolean want = !MobileOptions.hideOffscreenInstances || isVisible(context.frustum());
		boolean allowed = snapshot.linesAllowed() && canUseGameMasterBlocks();
		boolean mainEnabled = allowed && (snapshot.mode() == StructureMode.SAVE || snapshot.showBoundingBox());
		boolean invisEnabled = allowed && snapshot.mode() == StructureMode.SAVE && snapshot.showAir();
		reconcile(mainInstances, want && mainEnabled);
		reconcile(invisInstances, want && invisEnabled);
		if (!want) {
			return;
		}

		// Distance limiting only skips updates; it must never hide anything.
		if (doDistanceLimitThisFrame(context)) {
			return;
		}

		if (!snapshot.equals(lastSnapshot)) {
			lastSnapshot = snapshot;
			mainDirty = true;
			invisDirty = true;
		}

		if (mainEnabled && mainDirty) {
			mainDirty = false;
			rebuildMainBox(snapshot);
		}

		long gameTime = level.getGameTime();
		// Dirty wins over the throttle, and it starts dirty, so the arithmetic below only ever
		// runs with a real timestamp from a previous rebuild.
		if (invisEnabled && (invisDirty || gameTime - lastRescan >= RESCAN_INTERVAL_TICKS)) {
			lastRescan = gameTime;
			invisDirty = false;
			rebuildInvisibleBlocks(snapshot);
		}
	}

	// Mirrors the vanilla gate: block master permissions or spectator, checked per frame so
	// switching modes mid-session takes effect immediately.
	private static boolean canUseGameMasterBlocks() {
		var player = Minecraft.getInstance().player;
		return player != null && (player.canUseGameMasterBlocks() || player.isSpectator());
	}

	private static void reconcile(TransformedInstance[] instances, boolean visible) {
		for (TransformedInstance instance : instances) {
			if (instance != null && visible != instance.handle()
					.isVisible()) {
				instance.handle()
						.setVisible(visible);
			}
		}
	}

	private void rebuildMainBox(Snapshot snapshot) {
		// Mirror and rotation turn the size vector into the two x/z corners of the volume,
		// transcribed from the vanilla renderer's switch statements.
		double d = snapshot.structureSize()
				.getX();
		double w = snapshot.structureSize()
				.getZ();
		if (snapshot.mirror() == Mirror.FRONT_BACK) {
			d = -d;
		} else if (snapshot.mirror() == Mirror.LEFT_RIGHT) {
			w = -w;
		}

		double x = snapshot.structurePos()
				.getX();
		double z = snapshot.structurePos()
				.getZ();
		double x1;
		double z1;
		double x2;
		double z2;
		switch (snapshot.rotation()) {
			case CLOCKWISE_90 -> {
				x1 = w < 0.0 ? x : x + 1.0;
				z1 = d < 0.0 ? z + 1.0 : z;
				x2 = x1 - w;
				z2 = z1 + d;
			}
			case CLOCKWISE_180 -> {
				x1 = d < 0.0 ? x : x + 1.0;
				z1 = w < 0.0 ? z : z + 1.0;
				x2 = x1 - d;
				z2 = z1 - w;
			}
			case COUNTERCLOCKWISE_90 -> {
				x1 = w < 0.0 ? x + 1.0 : x;
				z1 = d < 0.0 ? z : z + 1.0;
				x2 = x1 + w;
				z2 = z1 - d;
			}
			default -> {
				x1 = d < 0.0 ? x + 1.0 : x;
				z1 = w < 0.0 ? z + 1.0 : z;
				x2 = x1 + d;
				z2 = z1 + w;
			}
		}
		double y1 = snapshot.structurePos()
				.getY();
		double y2 = y1 + snapshot.structureSize()
				.getY();

		// Vanilla colors each axis' edges differently, so the box's twelve edges are split
		// into three four-line models and tinted per instance.
		LineModelBuilder xAxis = new LineModelBuilder(4);
		LineModelBuilder yAxis = new LineModelBuilder(4);
		LineModelBuilder zAxis = new LineModelBuilder(4);
		for (double y : new double[]{y1, y2}) {
			for (double cornerZ : new double[]{z1, z2}) {
				xAxis.line((float) x1, (float) y, (float) cornerZ, (float) x2, (float) y, (float) cornerZ);
			}
		}
		for (double cornerX : new double[]{x1, x2}) {
			for (double cornerZ : new double[]{z1, z2}) {
				yAxis.line((float) cornerX, (float) y1, (float) cornerZ, (float) cornerX, (float) y2, (float) cornerZ);
			}
		}
		for (double cornerX : new double[]{x1, x2}) {
			for (double y : new double[]{y1, y2}) {
				zAxis.line((float) cornerX, (float) y, (float) z1, (float) cornerX, (float) y, (float) z2);
			}
		}

		setInstance(mainInstances, mainModels, 0, xAxis.build(), COLOR_X_EDGES);
		setInstance(mainInstances, mainModels, 1, yAxis.build(), COLOR_Y_EDGES);
		setInstance(mainInstances, mainModels, 2, zAxis.build(), COLOR_Z_EDGES);
	}

	private void rebuildInvisibleBlocks(Snapshot snapshot) {
		// One builder per color family, matching the vanilla renderer's per-block classification.
		LineModelBuilder[] builders = {new LineModelBuilder(), new LineModelBuilder(), new LineModelBuilder(), new LineModelBuilder(),};

		BlockPos origin = pos.offset(snapshot.structurePos());
		BlockPos end = origin.offset(snapshot.structureSize())
				.offset(-1, -1, -1);
		for (BlockPos blockPos : BlockPos.betweenClosed(origin, end)) {
			BlockState state = level.getBlockState(blockPos);
			int kind;
			if (state.isAir()) {
				kind = 0;
			} else if (state.is(Blocks.STRUCTURE_VOID)) {
				kind = 1;
			} else if (state.is(Blocks.BARRIER)) {
				kind = 2;
			} else if (state.is(Blocks.LIGHT)) {
				kind = 3;
			} else {
				continue;
			}

			// Boxes hug the middle of each block; air is drawn slightly smaller so neighboring
			// air boxes read apart from each other, exactly as in vanilla.
			float shrink = kind == 0 ? 0.05f : 0.0f;
			float x1 = blockPos.getX() - pos.getX() + 0.45f - shrink;
			float y1 = blockPos.getY() - pos.getY() + 0.45f - shrink;
			float z1 = blockPos.getZ() - pos.getZ() + 0.45f - shrink;
			float x2 = blockPos.getX() - pos.getX() + 0.55f + shrink;
			float y2 = blockPos.getY() - pos.getY() + 0.55f + shrink;
			float z2 = blockPos.getZ() - pos.getZ() + 0.55f + shrink;
			addBox(builders[kind], x1, y1, z1, x2, y2, z2);
		}

		setInstance(invisInstances, invisModels, 0, builders[0].build(), COLOR_AIR);
		setInstance(invisInstances, invisModels, 1, builders[1].build(), COLOR_VOID);
		setInstance(invisInstances, invisModels, 2, builders[2].build(), COLOR_BARRIER);
		setInstance(invisInstances, invisModels, 3, builders[3].build(), COLOR_LIGHT);
	}

	private static void addBox(LineModelBuilder builder, float x1, float y1, float z1, float x2, float y2, float z2) {
		builder.line(x1, y1, z1, x2, y1, z1);
		builder.line(x1, y2, z1, x2, y2, z1);
		builder.line(x1, y1, z2, x2, y1, z2);
		builder.line(x1, y2, z2, x2, y2, z2);
		builder.line(x1, y1, z1, x1, y2, z1);
		builder.line(x2, y1, z1, x2, y2, z1);
		builder.line(x1, y1, z2, x1, y2, z2);
		builder.line(x2, y1, z2, x2, y2, z2);
		builder.line(x1, y1, z1, x1, y1, z2);
		builder.line(x2, y1, z1, x2, y1, z2);
		builder.line(x1, y2, z1, x1, y2, z2);
		builder.line(x2, y2, z1, x2, y2, z2);
	}

	private void setInstance(TransformedInstance[] instances, Model[] models, int index, Model model, float[] color) {
		TransformedInstance instance = instances[index];
		if (instance == null) {
			instance = instances[index] = instancerProvider().instancer(InstanceTypes.TRANSFORMED, model)
					.createInstance();
			// All line coordinates are relative to this block entity, and debug lines are an
			// editor overlay: they ignore world light in vanilla, so pin them full bright.
			BlockPos visualPos = getVisualPosition();
			instance.setIdentityTransform()
					.translate(visualPos.getX(), visualPos.getY(), visualPos.getZ());
			instance.color(color[0], color[1], color[2]);
			instance.light(LightTexture.FULL_BRIGHT);
		} else if (models[index] != model) {
			instancerProvider().instancer(InstanceTypes.TRANSFORMED, model)
					.stealInstance(instance);
		}
		models[index] = model;
		instance.setChanged();
	}

	@Override
	public void updateLight(float partialTick) {
		// Debug lines are pinned full bright at creation; they never sample world light.
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		for (TransformedInstance instance : mainInstances) {
			if (instance != null) {
				consumer.accept(instance);
			}
		}
		for (TransformedInstance instance : invisInstances) {
			if (instance != null) {
				consumer.accept(instance);
			}
		}
	}

	@Override
	protected void _delete() {
		for (TransformedInstance instance : mainInstances) {
			if (instance != null) {
				instance.delete();
			}
		}
		for (TransformedInstance instance : invisInstances) {
			if (instance != null) {
				instance.delete();
			}
		}
	}

	// Everything the drawn geometry depends on, in one comparable lump so a change to any
	// part of it invalidates the built models.
	private record Snapshot(BlockPos structurePos, Vec3i structureSize, StructureMode mode, Mirror mirror, Rotation rotation, boolean showAir, boolean showBoundingBox) {
		// Vanilla returns before drawing anything unless every size axis is at least one and
		// the structure is being saved or loaded.
		boolean linesAllowed() {
			return structureSize.getX() >= 1 && structureSize.getY() >= 1 && structureSize.getZ() >= 1 && (mode == StructureMode.SAVE || mode == StructureMode.LOAD);
		}
	}
}
