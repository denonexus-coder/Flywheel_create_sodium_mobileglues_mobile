package dev.engine_room.vanillin.visuals;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.backend.MobileOptions;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.lib.model.part.ModelTrees;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;

public class BoatVisual extends AbstractEntityVisual<Boat> implements SimpleDynamicVisual {
	private InstanceTree instances;
	private Boat.Type currentVariant;
	private InstanceTree leftPaddle;
	private InstanceTree rightPaddle;

	private final Matrix4f pose = new Matrix4f();

	public BoatVisual(VisualizationContext ctx, Boat entity, float partialTick) {
		super(ctx, entity, partialTick);

		currentVariant = entity.getVariant();
		createInstances();

		updateInstances(partialTick);
	}

	@Override
	public void beginFrame(Context ctx) {
		// Reconcile instead of only hiding: writing only when the answer changes keeps a hidden
		// tree from being re-walked every frame, and brings instances back if the option is
		// switched off at runtime.
		boolean want = !MobileOptions.hideOffscreenInstances || isVisible(ctx.frustum());
		if (want != instances.visible()) {
			instances.visible(want);
		}
		if (!want) {
			return;
		}

		// The variant is synced entity data, so treat a change as a rebuild rather than
		// assuming spawn fixed it forever.
		Boat.Type variant = entity.getVariant();
		if (variant != currentVariant) {
			instances.delete();
			currentVariant = variant;
			createInstances();
		}

		updateInstances(ctx.partialTick());
	}

	private void createInstances() {
		instances = InstanceTree.create(instancerProvider(), ModelTrees.of(ModelLayers.createBoatModelName(currentVariant), material(currentVariant)));

		// Vanilla renders water_patch twice: once inside the model pass, where its geometry
		// duplicates the bottom quad exactly, and once through RenderType.waterMask(), an
		// untextured position-only render type instances can't reproduce. Skipping the node
		// drops the redundant copy and, unavoidably, the water mask along with it.
		instances.childOrThrow("water_patch")
				.skipDraw(true);
		leftPaddle = instances.childOrThrow("left_paddle");
		rightPaddle = instances.childOrThrow("right_paddle");
	}

	private static Material material(Boat.Type variant) {
		return SimpleMaterial.builder()
				.texture(new ResourceLocation("textures/entity/boat/" + variant.getName() + ".png"))
				// Boats render through entityCutoutNoCull on a standalone texture: alpha tested
				// like the other entity visuals, un-mipmapped, and drawn without backface culling.
				.cutout(CutoutShaders.ONE_TENTH)
				.mipmap(false)
				.backfaceCulling(false)
				.build();
	}

	private void updateInstances(float partialTick) {
		// getVisualPosition matches the translate LevelRenderer.renderEntity applies before
		// handing the entity to its renderer; the rest of the chain mirrors BoatRenderer.
		var pos = getVisualPosition(partialTick);

		pose.identity();
		pose.translate(pos);
		pose.translate(0.0F, 0.375F, 0.0F);
		float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
		pose.rotateY((180.0F - yaw) * Mth.DEG_TO_RAD);

		float hurtTime = entity.getHurtTime() - partialTick;
		float damage = entity.getDamage() - partialTick;
		if (damage < 0.0F) {
			damage = 0.0F;
		}
		if (hurtTime > 0.0F) {
			// The hurt wobble is scaled by the damage the boat is still bleeding off.
			pose.rotateX(Mth.sin(hurtTime) * hurtTime * damage / 10.0F * entity.getHurtDir() * Mth.DEG_TO_RAD);
		}

		float bubbleAngle = entity.getBubbleAngle(partialTick);
		if (!Mth.equal(bubbleAngle, 0.0F)) {
			pose.rotate(new Quaternionf().setAngleAxis(bubbleAngle * Mth.DEG_TO_RAD, 1.0F, 0.0F, 1.0F));
		}

		// The model is authored backwards and turned by 90 degrees, exactly as BoatRenderer does.
		pose.scale(-1.0F, -1.0F, 1.0F);
		pose.rotateY(90.0F * Mth.DEG_TO_RAD);

		updatePaddles(partialTick);
		instances.updateInstances(pose);

		updateLight(partialTick);
	}

	private void updatePaddles(float partialTick) {
		// BoatModel#setupAnim only ever animates the two paddles; the rest of the tree holds
		// its baked pose.
		float leftRow = entity.getRowingTime(0, partialTick);
		leftPaddle.xRot(Mth.clampedLerp(-1.0471976F, -0.2617994F, (Mth.sin(-leftRow) + 1.0F) / 2.0F));
		leftPaddle.yRot(Mth.clampedLerp(-0.7853982F, 0.7853982F, (Mth.sin(-leftRow + 1.0F) + 1.0F) / 2.0F));

		float rightRow = entity.getRowingTime(1, partialTick);
		rightPaddle.xRot(Mth.clampedLerp(-1.0471976F, -0.2617994F, (Mth.sin(-rightRow) + 1.0F) / 2.0F));
		// Mirror the left paddle's yaw so both oars sweep relative to their own side of the hull.
		rightPaddle.yRot(Mth.PI - Mth.clampedLerp(-0.7853982F, 0.7853982F, (Mth.sin(-rightRow + 1.0F) + 1.0F) / 2.0F));
	}

	private void updateLight(float partialTick) {
		int packedLight = computePackedLight(partialTick);
		instances.traverse(instance -> {
			instance.light(packedLight)
					.setChanged();
		});
	}

	@Override
	protected void _delete() {
		instances.delete();
	}
}
