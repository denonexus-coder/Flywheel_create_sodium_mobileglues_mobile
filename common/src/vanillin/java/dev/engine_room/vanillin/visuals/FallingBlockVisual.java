package dev.engine_room.vanillin.visuals;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.backend.MobileOptions;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public class FallingBlockVisual extends AbstractEntityVisual<FallingBlockEntity> implements SimpleDynamicVisual {
	private final TransformedInstance instance;
	private BlockState currentState;

	public FallingBlockVisual(VisualizationContext ctx, FallingBlockEntity entity, float partialTick) {
		super(ctx, entity, partialTick);

		currentState = entity.getBlockState();
		instance = instancerProvider()
				.instancer(InstanceTypes.TRANSFORMED, Models.block(currentState))
				.createInstance();

		updateInstance(partialTick);
	}

	@Override
	public void beginFrame(Context ctx) {
		// Reconcile instead of only hiding: writing only when the answer changes keeps a hidden
		// instance from being re-marked every frame, and brings it back if the option is
		// switched off at runtime. Hiding is what actually pulls it out of the instanced draw.
		boolean want = !MobileOptions.hideOffscreenInstances || isVisible(ctx.frustum());
		if (want != instance.handle().isVisible()) {
			instance.handle()
					.setVisible(want);
		}
		if (!want) {
			return;
		}

		// Vanilla never swaps a falling block's state, but re-keying on comparison is cheaper
		// than trusting that no mod ever will.
		BlockState state = entity.getBlockState();
		if (state != currentState) {
			currentState = state;
			instancerProvider()
					.instancer(InstanceTypes.TRANSFORMED, Models.block(state))
					.stealInstance(instance);
		}

		updateInstance(ctx.partialTick());
	}

	private void updateInstance(float partialTick) {
		// getVisualPosition matches the translate LevelRenderer.renderEntity applies before
		// handing the entity to its renderer; the half-block shift is FallingBlockRenderer's own.
		var pos = getVisualPosition(partialTick);

		instance.setIdentityTransform()
				.translate(pos)
				.translate(-0.5F, 0.0F, -0.5F)
				.light(computePackedLight(partialTick))
				.setChanged();
	}

	@Override
	protected void _delete() {
		instance.delete();
	}

	/**
	 * Whether the entity has any geometry to draw: FallingBlockRenderer returns early for states
	 * without a model and for the frame where the block still occupies its own cell in the level.
	 * Using this same predicate as both {@code shouldVisualize} and {@code skipVanillaRender}
	 * keeps exactly one of the two renderers drawing - never both, never neither.
	 */
	public static boolean shouldSkipRender(FallingBlockEntity entity) {
		BlockState state = entity.getBlockState();
		return state.getRenderShape() == RenderShape.MODEL
				&& state != entity.level().getBlockState(entity.blockPosition());
	}
}
