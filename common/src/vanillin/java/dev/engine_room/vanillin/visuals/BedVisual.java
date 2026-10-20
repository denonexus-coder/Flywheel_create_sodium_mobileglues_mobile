package dev.engine_room.vanillin.visuals;

import java.util.function.Consumer;

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
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BrightnessCombiner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BedPart;

public class BedVisual extends AbstractBlockEntityVisual<BedBlockEntity> implements SimpleDynamicVisual {
	// Vanilla renders bed pieces with RenderType.entitySolid out of the beds atlas:
	// fully opaque, so no cutout and no mipmapping.
	private static final Material MATERIAL = SimpleMaterial.builder()
			.texture(Sheets.BED_SHEET)
			.mipmap(false)
			.build();

	private final InstanceTree instances;
	private final Matrix4f initialPose;
	private final DoubleBlockCombiner.NeighborCombineResult<BedBlockEntity> neighborCombineResult;
	private final Int2IntFunction brightness;

	public BedVisual(VisualizationContext ctx, BedBlockEntity blockEntity, float partialTick) {
		super(ctx, blockEntity, partialTick);

		// Head and foot are separate block entities; the block state says which half this one is
		// and the block entity carries the dye color, so layer and texture are fixed here.
		ModelLayerLocation layer = blockState.getValue(BedBlock.PART) == BedPart.HEAD ? ModelLayers.BED_HEAD : ModelLayers.BED_FOOT;
		net.minecraft.client.resources.model.Material texture = Sheets.BED_TEXTURES[blockEntity.getColor()
				.getId()];

		instances = InstanceTree.create(instancerProvider(), ModelTrees.of(layer, texture, MATERIAL));

		// One bed is two block entities, so light is taken from the brighter half exactly like
		// the vanilla BedRenderer does with the shared packed light.
		// "Neigbour", not "Neighbour": the vanilla method name carries the typo.
		neighborCombineResult = DoubleBlockCombiner.combineWithNeigbour(BlockEntityType.BED, BedBlock::getBlockType, BedBlock::getConnectedDirection, ChestBlock.FACING, blockState, level, pos, (lvl, p) -> false);
		brightness = neighborCombineResult.apply(new BrightnessCombiner<BedBlockEntity>());

		initialPose = createInitialPose();
		instances.updateInstancesStatic(initialPose);
	}

	private Matrix4f createInitialPose() {
		// Pose copied from the vanilla BedRenderer. Its z offset of -1 only applies to the
		// standalone item model it also draws, never to an in-world piece.
		float zRot = (180.0f + blockState.getValue(BedBlock.FACING)
				.toYRot()) * Mth.DEG_TO_RAD;
		return new Matrix4f().translate(getVisualPosition().getX(), getVisualPosition().getY(), getVisualPosition().getZ())
				.translate(0.0f, 0.5625f, 0.0f)
				.rotateX(Mth.HALF_PI)
				.translate(0.5f, 0.5f, 0.5f)
				.rotateZ(zRot)
				.translate(-0.5f, -0.5f, -0.5f);
	}

	@Override
	public void beginFrame(Context context) {
		// Reconcile instead of only hiding: writing only when the answer changes keeps a hidden
		// tree from being re-walked every frame, and brings instances back if the option is
		// switched off at runtime. Toggling the root covers the leg children.
		boolean want = !MobileOptions.hideOffscreenInstances || isVisible(context.frustum());
		if (want != instances.visible()) {
			instances.visible(want);
		}
		// Nothing here animates: the pose is pinned at creation and light is refreshed every
		// frame in updateLight, so a hidden bed needs no further work.
	}

	@Override
	public void setSectionCollector(SectionCollector sectionCollector) {
		this.lightSections = sectionCollector;

		lightSections.sections(neighborCombineResult.apply(new SectionPosCombiner()));
	}

	@Override
	public void updateLight(float partialTick) {
		int packedLight = brightness.get(computePackedLight());
		instances.traverse(instance -> {
			instance.light(packedLight)
					.setChanged();
		});
	}

	@Override
	public void collectCrumblingInstances(Consumer<Instance> consumer) {
		instances.traverse(consumer);
	}

	@Override
	protected void _delete() {
		instances.delete();
	}

	private class SectionPosCombiner implements DoubleBlockCombiner.Combiner<BlockEntity, LongSet> {
		@Override
		public LongSet acceptDouble(BlockEntity first, BlockEntity second) {
			long firstSection = SectionPos.asLong(first.getBlockPos());
			long secondSection = SectionPos.asLong(second.getBlockPos());

			if (firstSection == secondSection) {
				return LongSet.of(firstSection);
			} else {
				return LongSet.of(firstSection, secondSection);
			}
		}

		@Override
		public LongSet acceptSingle(BlockEntity single) {
			return LongSet.of(SectionPos.asLong(single.getBlockPos()));
		}

		@Override
		public LongSet acceptNone() {
			return LongSet.of(SectionPos.asLong(pos));
		}
	}
}
