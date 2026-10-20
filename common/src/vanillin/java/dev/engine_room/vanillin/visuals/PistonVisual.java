package dev.engine_room.vanillin.visuals;

import java.util.function.Consumer;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.backend.MobileOptions;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;

public class PistonVisual extends AbstractBlockEntityVisual<PistonMovingBlockEntity> implements SimpleDynamicVisual {
	// Slot 0 holds the head (or the whole moved block); slot 1 holds the piston body that the
	// vanilla renderer adds only behind a fully retracted source piston.
	private final TransformedInstance[] instances = new TransformedInstance[2];
	// Cached per slot so a model swap can be detected without rebuilding anything.
	private final Model[] models = new Model[2];

	public PistonVisual(VisualizationContext ctx, PistonMovingBlockEntity blockEntity, float partialTick) {
		super(ctx, blockEntity, partialTick);
	}

	@Override
	public void beginFrame(Context context) {
		float progress = blockEntity.getProgress(context.partialTick());
		BlockState movedState = blockEntity.getMovedState();

		// The three branches of the vanilla PistonHeadRenderer, replayed to learn which block
		// states draw in which slot this frame. A moving block entity is created and destroyed
		// by block state changes, but progress flips its branch and SHORT property live, so
		// everything here is re-evaluated per frame.
		BlockState[] states = new BlockState[2];
		if (!movedState.isAir()) {
			if (movedState.is(Blocks.PISTON_HEAD) && progress <= 4.0f) {
				states[0] = movedState.setValue(PistonHeadBlock.SHORT, progress <= 0.5f);
			} else if (blockEntity.isSourcePiston() && !blockEntity.isExtending()) {
				PistonType type = movedState.is(Blocks.STICKY_PISTON) ? PistonType.STICKY : PistonType.DEFAULT;
				states[0] = Blocks.PISTON_HEAD.defaultBlockState()
						.setValue(PistonHeadBlock.TYPE, type)
						.setValue(PistonHeadBlock.FACING, movedState.getValue(PistonBaseBlock.FACING))
						.setValue(PistonHeadBlock.SHORT, progress >= 0.5f);
				states[1] = movedState.setValue(PistonBaseBlock.EXTENDED, true);
			} else {
				states[0] = movedState;
			}
		}

		// Reconcile instead of only hiding: writing only when the answer changes keeps hidden
		// instances from being re-marked every frame, and brings them back if the option is
		// switched off at runtime. Each slot reconciles separately because slot 1 vanishes on
		// its own when the piston is no longer a retracting source.
		boolean want = !MobileOptions.hideOffscreenInstances || isVisible(context.frustum());
		for (int i = 0; i < instances.length; i++) {
			TransformedInstance instance = instances[i];
			boolean visible = want && states[i] != null;
			if (instance != null && visible != instance.handle()
					.isVisible()) {
				instance.handle()
						.setVisible(visible);
			}
		}
		if (!want) {
			return;
		}

		// Distance limiting only skips updates; it must never hide anything.
		if (doDistanceLimitThisFrame(context)) {
			return;
		}

		int packedLight = computePackedLight();
		for (int i = 0; i < instances.length; i++) {
			if (states[i] != null) {
				updateInstance(i, states[i], progress, packedLight);
			}
		}
	}

	private void updateInstance(int index, BlockState state, float progress, int packedLight) {
		// Models.block caches per block state, so the SHORT flip mid-animation only swaps which
		// cached model the existing instance is stolen into.
		Model model = Models.block(state);
		TransformedInstance instance = instances[index];
		if (instance == null) {
			instance = instances[index] = instancerProvider().instancer(InstanceTypes.TRANSFORMED, model)
					.createInstance();
		} else if (models[index] != model) {
			instancerProvider().instancer(InstanceTypes.TRANSFORMED, model)
					.stealInstance(instance);
		}
		models[index] = model;

		// The block rides the moving block entity's own interpolated offset from its position;
		// the vanilla renderer uses the head position only for light and random seeding.
		BlockPos visualPos = getVisualPosition();
		instance.setIdentityTransform()
				.translate(visualPos.getX(), visualPos.getY(), visualPos.getZ())
				.translate(blockEntity.getXOff(progress), blockEntity.getYOff(progress), blockEntity.getZOff(progress))
				.light(packedLight)
				.setChanged();
	}

	@Override
	public void updateLight(float partialTick) {
		// Light is written in beginFrame alongside each transform: both instances re-evaluate
		// their state every frame anyway, so a separate traversal would only double the writes.
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		for (TransformedInstance instance : instances) {
			if (instance != null) {
				consumer.accept(instance);
			}
		}
	}

	@Override
	protected void _delete() {
		for (TransformedInstance instance : instances) {
			if (instance != null) {
				instance.delete();
			}
		}
	}
}
