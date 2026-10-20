package dev.engine_room.vanillin.visuals;

import java.util.Set;
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
import net.minecraft.client.renderer.Sheets;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotPatterns;

public class DecoratedPotVisual extends AbstractBlockEntityVisual<DecoratedPotBlockEntity> implements SimpleDynamicVisual {
	// Vanilla buffers the pot with RenderType.entitySolid out of the decorated pot atlas:
	// fully opaque, so no cutout and no mipmapping.
	private static final Material MATERIAL = SimpleMaterial.builder()
			.texture(Sheets.DECORATED_POT_SHEET)
			.mipmap(false)
			.build();

	// The sides layer holds all four faces as children of the root; each per-side tree keeps
	// one child and prunes the other three so only that face is retextured with its pattern.
	private static final Set<String> PRUNE_FRONT = Set.of("/back", "/left", "/right");
	private static final Set<String> PRUNE_BACK = Set.of("/front", "/left", "/right");
	private static final Set<String> PRUNE_LEFT = Set.of("/front", "/back", "/right");
	private static final Set<String> PRUNE_RIGHT = Set.of("/front", "/back", "/left");

	private final InstanceTree base;
	// One tree per side in record order: front, back, left, right.
	private final InstanceTree[] sides = new InstanceTree[4];
	// The item each side tree was built for, so unchanged sides skip all rebuild work.
	private final Item[] sideItems = new Item[4];

	private Matrix4f pose;
	private Direction lastDirection;
	// Direction and sherds are block entity data rather than block state, so the framework
	// never recreates this visual for them; they are diffed in beginFrame instead. The flag
	// collects every reason to re-pose the trees, which then happens only while they are
	// shown, since a hidden tree silently drops pose propagation.
	private boolean poseDirty;

	public DecoratedPotVisual(VisualizationContext ctx, DecoratedPotBlockEntity blockEntity, float partialTick) {
		super(ctx, blockEntity, partialTick);

		// The base pattern is always plain brick; only the four side patterns are data driven.
		base = InstanceTree.create(instancerProvider(), ModelTrees.of(ModelLayers.DECORATED_POT_BASE, baseTexture(), MATERIAL));

		var decorations = blockEntity.getDecorations();
		createSide(0, decorations.front(), PRUNE_FRONT);
		createSide(1, decorations.back(), PRUNE_BACK);
		createSide(2, decorations.left(), PRUNE_LEFT);
		createSide(3, decorations.right(), PRUNE_RIGHT);

		lastDirection = blockEntity.getDirection();
		pose = createPose(lastDirection);
		applyPose();
		poseDirty = false;
	}

	private Matrix4f createPose(Direction direction) {
		// Pose copied from the vanilla DecoratedPotRenderer, relative to the pot block.
		float yRot = (180.0f - direction.toYRot()) * Mth.DEG_TO_RAD;
		return new Matrix4f().translate(getVisualPosition().getX(), getVisualPosition().getY(), getVisualPosition().getZ())
				.translate(0.5f, 0.0f, 0.5f)
				.rotateY(yRot)
				.translate(-0.5f, 0.0f, -0.5f);
	}

	private void applyPose() {
		base.updateInstances(pose);
		for (InstanceTree side : sides) {
			if (side != null) {
				side.updateInstances(pose);
			}
		}
	}

	@Override
	public void beginFrame(Context context) {
		// Reconcile instead of only hiding: writing only when the answer changes keeps hidden
		// trees from being re-walked every frame, and brings instances back if the option is
		// switched off at runtime. Every root toggles on its own because side trees can be
		// rebuilt while the pot is off screen.
		boolean want = !MobileOptions.hideOffscreenInstances || isVisible(context.frustum());
		reconcile(base, want);
		for (InstanceTree side : sides) {
			reconcile(side, want);
		}
		if (!want) {
			return;
		}

		Direction direction = blockEntity.getDirection();
		if (direction != lastDirection) {
			lastDirection = direction;
			pose = createPose(direction);
			poseDirty = true;
		}

		var decorations = blockEntity.getDecorations();
		if (decorations.front() != sideItems[0]) {
			rebuildSide(0, decorations.front(), PRUNE_FRONT);
		}
		if (decorations.back() != sideItems[1]) {
			rebuildSide(1, decorations.back(), PRUNE_BACK);
		}
		if (decorations.left() != sideItems[2]) {
			rebuildSide(2, decorations.left(), PRUNE_LEFT);
		}
		if (decorations.right() != sideItems[3]) {
			rebuildSide(3, decorations.right(), PRUNE_RIGHT);
		}

		if (poseDirty) {
			poseDirty = false;
			applyPose();
		}
	}

	private static void reconcile(@Nullable InstanceTree tree, boolean visible) {
		if (tree != null && tree.visible() != visible) {
			tree.visible(visible);
		}
	}

	private void rebuildSide(int index, Item item, Set<String> pathsToPrune) {
		if (sides[index] != null) {
			sides[index].delete();
		}
		createSide(index, item, pathsToPrune);
		if (sides[index] != null) {
			sides[index].visible(true);
		}
		poseDirty = true;
	}

	private void createSide(int index, Item item, Set<String> pathsToPrune) {
		sideItems[index] = item;

		var texture = sideTexture(item);
		if (texture == null) {
			// Mirrors vanilla, which draws no face when even the brick pattern is missing.
			sides[index] = null;
			return;
		}
		sides[index] = InstanceTree.create(instancerProvider(), ModelTrees.of(ModelLayers.DECORATED_POT_SIDES, pathsToPrune, texture, MATERIAL));
	}

	@Nullable
	private static net.minecraft.client.resources.model.Material baseTexture() {
		return Sheets.getDecoratedPotMaterial(DecoratedPotPatterns.BASE);
	}

	@Nullable
	private static net.minecraft.client.resources.model.Material sideTexture(Item item) {
		var texture = Sheets.getDecoratedPotMaterial(DecoratedPotPatterns.getResourceKey(item));
		if (texture == null) {
			// Fall back to the brick pattern exactly like the vanilla renderer does.
			texture = Sheets.getDecoratedPotMaterial(DecoratedPotPatterns.getResourceKey(Items.BRICK));
		}
		return texture;
	}

	@Override
	public void updateLight(float partialTick) {
		int packedLight = computePackedLight();
		base.traverse(instance -> {
			instance.light(packedLight)
					.setChanged();
		});
		for (InstanceTree side : sides) {
			if (side != null) {
				side.traverse(instance -> {
					instance.light(packedLight)
							.setChanged();
				});
			}
		}
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		base.traverse(consumer);
		for (InstanceTree side : sides) {
			if (side != null) {
				side.traverse(consumer);
			}
		}
	}

	@Override
	protected void _delete() {
		base.delete();
		for (InstanceTree side : sides) {
			if (side != null) {
				side.delete();
			}
		}
	}
}
