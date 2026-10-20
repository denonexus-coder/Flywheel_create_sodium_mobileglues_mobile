package dev.engine_room.vanillin.visuals;

import java.util.function.Consumer;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.backend.MobileOptions;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.vanillin.item.ItemModels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;

public class CampfireVisual extends AbstractBlockEntityVisual<CampfireBlockEntity> implements SimpleDynamicVisual {
	private static final int SLOT_COUNT = 4;

	// One instance per cooking slot. The vanilla block entity renderer draws exactly these four
	// item stacks: the logs and flame are part of the chunk-mesh block model, and the smoke and
	// sparkle come from CampfireBlock.animateTick particles, both of which stay vanilla.
	private final TransformedInstance[] instances = new TransformedInstance[SLOT_COUNT];
	// The stack each instance was last built for, so an unchanged slot skips all model work.
	private final ItemStack[] builtStacks = new ItemStack[SLOT_COUNT];

	public CampfireVisual(VisualizationContext ctx, CampfireBlockEntity blockEntity, float partialTick) {
		super(ctx, blockEntity, partialTick);
	}

	@Override
	public void beginFrame(Context context) {
		var items = blockEntity.getItems();

		// Reconcile instead of only hiding: writing only when the answer changes keeps hidden
		// instances from being re-marked every frame, and brings them back if the option is
		// switched off at runtime. Each slot reconciles separately because an empty slot must
		// stay hidden even while the campfire itself is on screen.
		boolean want = !MobileOptions.hideOffscreenInstances || isVisible(context.frustum());
		for (int i = 0; i < SLOT_COUNT; i++) {
			TransformedInstance instance = instances[i];
			if (instance == null) {
				continue;
			}

			boolean slotVisible = want && !items.get(i)
					.isEmpty();
			if (slotVisible != instance.handle()
					.isVisible()) {
				instance.handle()
						.setVisible(slotVisible);
			}
		}
		if (!want) {
			return;
		}

		// Distance limiting only skips updates; it must never hide anything.
		if (doDistanceLimitThisFrame(context)) {
			return;
		}

		Direction facing = blockState.getValue(CampfireBlock.FACING);
		int packedLight = computePackedLight();
		BlockPos visualPos = getVisualPosition();

		for (int i = 0; i < SLOT_COUNT; i++) {
			ItemStack stack = items.get(i);
			if (stack.isEmpty()) {
				continue;
			}

			TransformedInstance instance = instances[i];
			if (instance == null || !ItemStack.matches(stack, builtStacks[i])) {
				// The baked model follows the stack, so a changed stack must move the existing
				// instance into the new model's instancer before its transform is rewritten.
				var model = ItemModels.get(level, stack, ItemDisplayContext.FIXED);
				if (instance == null) {
					instance = instances[i] = instancerProvider().instancer(InstanceTypes.TRANSFORMED, model)
							.createInstance();
				} else {
					instancerProvider().instancer(InstanceTypes.TRANSFORMED, model)
							.stealInstance(instance);
				}
				builtStacks[i] = stack.copy();
			}

			// Pose chain copied from the vanilla CampfireRenderer, one slot per direction
			// starting from the block's facing.
			Direction direction = Direction.from2DDataValue((i + facing.get2DDataValue()) % 4);
			instance.setIdentityTransform()
					.translate(visualPos.getX(), visualPos.getY(), visualPos.getZ())
					.translate(0.5f, 0.44921875f, 0.5f)
					.rotateY(-direction.toYRot() * Mth.DEG_TO_RAD)
					.rotateX(Mth.HALF_PI)
					.translate(-0.3125f, -0.3125f, 0.0f)
					.scale(0.375f, 0.375f, 0.375f)
					.light(packedLight)
					.setChanged();
		}
	}

	@Override
	public void updateLight(float partialTick) {
		// Light is written in beginFrame alongside each transform: these instances are created
		// there and only ever render right after that pass, so a separate traversal would just
		// double the per-frame writes.
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
