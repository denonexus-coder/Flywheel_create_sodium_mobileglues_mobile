package dev.engine_room.vanillin.visuals;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.backend.MobileOptions;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Blocks;

public class TntVisual extends AbstractEntityVisual<PrimedTnt> implements SimpleDynamicVisual {
	private static final int WHITE_OVERLAY = OverlayTexture.pack(OverlayTexture.u(1.0F), 10);

	private final TransformedInstance instance;

	public TntVisual(VisualizationContext ctx, PrimedTnt entity, float partialTick) {
		super(ctx, entity, partialTick);

		instance = instancerProvider()
				.instancer(InstanceTypes.TRANSFORMED, Models.block(Blocks.TNT.defaultBlockState()))
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

		updateInstance(ctx.partialTick());
	}

	private void updateInstance(float partialTick) {
		var pos = getVisualPosition(partialTick);
		int fuse = entity.getFuse();

		// TntRenderer's fuse pulse: over the final ten ticks f climbs to 1 and the block
		// swells toward 1.3x its resting size.
		float scale = 1.0F;
		if ((float) fuse - partialTick + 1.0F < 10.0F) {
			float f = 1.0F - ((float) fuse - partialTick + 1.0F) / 10.0F;
			f = Mth.clamp(f, 0.0F, 1.0F);
			f *= f;
			f *= f;
			scale = 1.0F + f * 0.3F;
		}

		// TntRenderer alternates a white flash every five fuse ticks via the overlay texture.
		int overlay = fuse / 5 % 2 == 0 ? WHITE_OVERLAY : OverlayTexture.NO_OVERLAY;

		// TntRenderer swings the model between two quarter turns around a -0.5 translate; the
		// sandwich collapses to a plain half-block offset, so use the collapsed form. Overlay
		// must be set before light: light() narrows the chain to ColoredLitInstance, which
		// has no overlay().
		instance.setIdentityTransform()
				.translate(pos)
				.translate(0.0F, 0.5F, 0.0F)
				.scale(scale, scale, scale)
				.translate(-0.5F, -0.5F, -0.5F)
				.overlay(overlay)
				.light(computePackedLight(partialTick))
				.setChanged();
	}

	@Override
	protected void _delete() {
		instance.delete();
	}
}
