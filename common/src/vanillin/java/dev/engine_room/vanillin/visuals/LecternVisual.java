package dev.engine_room.vanillin.visuals;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.backend.MobileOptions;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.lib.model.part.ModelTrees;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

public class LecternVisual extends AbstractBlockEntityVisual<LecternBlockEntity> implements SimpleDynamicVisual {
	// The vanilla renderer buffers the book from the block atlas with RenderType.entitySolid:
	// opaque, so no cutout and no mipmap shard.
	private static final Material MATERIAL = SimpleMaterial.builder()
			.texture(TextureAtlas.LOCATION_BLOCKS)
			.mipmap(false)
			.build();

	// BookModel.setupAnim(time=0, flip=0.1, open=0.9, scale=1.2), exactly as the vanilla
	// LecternRenderer calls it: angle = (sin(time * 0.02) * 0.1 + 1.25) * scale. The lectern
	// never animates its book, so the poses that formula produces are pinned once instead of
	// recomputed every frame.
	private static final float ANGLE = (Mth.sin(0.0f) * 0.1f + 1.25f) * 1.2f;
	private static final float PAGE_X = Mth.sin(ANGLE);

	@Nullable
	private final InstanceTree instances;

	public LecternVisual(VisualizationContext ctx, LecternBlockEntity blockEntity, float partialTick) {
		super(ctx, blockEntity, partialTick);

		if (!blockState.getValue(LecternBlock.HAS_BOOK)) {
			// Without a book the vanilla renderer draws nothing on top of the chunk-mesh
			// lectern body; the block state changing recreates this visual anyway.
			instances = null;
			return;
		}

		instances = InstanceTree.create(instancerProvider(), ModelTrees.of(ModelLayers.BOOK, EnchantTableRenderer.BOOK_LOCATION, MATERIAL));

		var leftLid = instances.childOrThrow("left_lid");
		leftLid.yRot(Mth.PI + ANGLE);
		var rightLid = instances.childOrThrow("right_lid");
		rightLid.yRot(-ANGLE);
		var leftPages = instances.childOrThrow("left_pages");
		leftPages.yRot(ANGLE);
		leftPages.xPos(PAGE_X);
		var rightPages = instances.childOrThrow("right_pages");
		rightPages.yRot(-ANGLE);
		rightPages.xPos(PAGE_X);
		var flipPage1 = instances.childOrThrow("flip_page1");
		flipPage1.yRot(ANGLE - ANGLE * 2.0f * 0.1f);
		flipPage1.xPos(PAGE_X);
		var flipPage2 = instances.childOrThrow("flip_page2");
		flipPage2.yRot(ANGLE - ANGLE * 2.0f * 0.9f);
		flipPage2.xPos(PAGE_X);

		instances.updateInstancesStatic(createInitialPose());
	}

	private Matrix4f createInitialPose() {
		// Pose copied from the vanilla LecternRenderer, relative to the lectern block.
		float yRot = -blockState.getValue(LecternBlock.FACING)
				.getClockWise()
				.toYRot() * Mth.DEG_TO_RAD;
		return new Matrix4f().translate(getVisualPosition().getX(), getVisualPosition().getY(), getVisualPosition().getZ())
				.translate(0.5f, 1.0625f, 0.5f)
				.rotateY(yRot)
				.rotateZ(67.5f * Mth.DEG_TO_RAD)
				.translate(0.0f, -0.125f, 0.0f);
	}

	@Override
	public void beginFrame(Context context) {
		if (instances == null) {
			return;
		}

		// Reconcile instead of only hiding: writing only when the answer changes keeps a hidden
		// tree from being re-walked every frame, and brings instances back if the option is
		// switched off at runtime. Toggling the root covers all book page children.
		boolean want = !MobileOptions.hideOffscreenInstances || isVisible(context.frustum());
		if (want != instances.visible()) {
			instances.visible(want);
		}
		// Nothing here animates: the book pose is pinned at creation and light is refreshed
		// every frame in updateLight, so a hidden book needs no further work.
	}

	@Override
	public void updateLight(float partialTick) {
		if (instances == null) {
			return;
		}

		int packedLight = computePackedLight();
		instances.traverse(instance -> {
			instance.light(packedLight)
					.setChanged();
		});
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		if (instances != null) {
			instances.traverse(consumer);
		}
	}

	@Override
	protected void _delete() {
		if (instances != null) {
			instances.delete();
		}
	}
}
