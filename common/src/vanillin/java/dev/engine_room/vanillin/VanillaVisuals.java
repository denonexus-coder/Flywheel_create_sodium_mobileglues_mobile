package dev.engine_room.vanillin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import dev.engine_room.vanillin.compose.ComposableEntityVisual;
import dev.engine_room.vanillin.compose.ConfiguredElement;
import dev.engine_room.vanillin.compose.ConfiguredElementImpl;
import dev.engine_room.vanillin.compose.VisualElement;
import dev.engine_room.vanillin.compose.VisualizationPredicate;
import dev.engine_room.vanillin.config.BlockEntityVisualizerBuilder;
import dev.engine_room.vanillin.config.Configurator;
import dev.engine_room.vanillin.config.EntityVisualizerBuilder;
import dev.engine_room.vanillin.elements.ShadowElement;
import dev.engine_room.vanillin.visuals.BedVisual;
import dev.engine_room.vanillin.visuals.BellVisual;
import dev.engine_room.vanillin.visuals.BlockDisplayVisual;
import dev.engine_room.vanillin.visuals.CampfireVisual;
import dev.engine_room.vanillin.visuals.ChestVisual;
import dev.engine_room.vanillin.visuals.DecoratedPotVisual;
import dev.engine_room.vanillin.visuals.ItemDisplayVisual;
import dev.engine_room.vanillin.visuals.ItemFrameVisual;
import dev.engine_room.vanillin.visuals.ItemVisual;
import dev.engine_room.vanillin.visuals.LecternVisual;
import dev.engine_room.vanillin.visuals.MinecartVisual;
import dev.engine_room.vanillin.visuals.PistonVisual;
import dev.engine_room.vanillin.visuals.ShulkerBoxVisual;
import dev.engine_room.vanillin.visuals.StructureBlockVisual;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class VanillaVisuals {
	private static final Logger LOGGER = LogManager.getLogger("Vanillin-BlockEntityInstancing");

	public static final Configurator CONFIGURATOR = new Configurator();

	// Ultra Performance Profile: only essential block entities/entities instanced
	// Based on user config: chest/ender_chest/trapped_chest + block_display/falling_block + item_display/item
	public static final boolean PERFORMANCE = true;
	// Stable visuals (only the performance-critical ones)
	public static final boolean STABLE = true;
	// Experimental visuals (only item_display and item for dropped items)
	public static final boolean EXPERIMENTAL = VanillinXplat.INSTANCE.isDevelopmentEnvironment();

	public static void init() {
		// ===== BLOCK ENTITIES (PERFORMANCE PROFILE) =====
		// Only: chest, ender_chest, trapped_chest (per user config "default")
		builder(BlockEntityType.CHEST)
				.factory(ChestVisual::new)
				.apply(STABLE);
		builder(BlockEntityType.ENDER_CHEST)
				.factory(ChestVisual::new)
				.apply(STABLE);
		builder(BlockEntityType.TRAPPED_CHEST)
				.factory(ChestVisual::new)
				.apply(STABLE);

		// DISABLED for performance: BELL, SHULKER_BOX, BED, LECTERN, CAMPFIRE, DECORATED_POT, PISTON, STRUCTURE_BLOCK

		// ===== ENTITIES (PERFORMANCE PROFILE) =====
		// block_display (user config "default")
		builder(EntityType.BLOCK_DISPLAY).factory(BlockDisplayVisual::new)
				.apply(STABLE);

		// item_display + item (user config "force_enable" - dropped items and item displays)
		composable(EntityType.ITEM_DISPLAY).with(element(VisualElements.ITEM_DISPLAY).build())
				.shouldVisualize((ctx, e) -> ItemDisplayVisual.shouldVisualize(e))
				.build()
				.skipVanillaRender(ItemDisplayVisual::shouldVisualize)
				.apply(EXPERIMENTAL);

		// minecarts DISABLED for performance
		// item_frame / glow_item_frame DISABLED for performance

		// item entity (dropped items) - FORCE ENABLE per user config
		composable(EntityType.ITEM).apply(VanillaVisuals::commonElements)
				.with(element(VisualElements.FIRE).build())
				.with(element(VisualElements.SHADOW).configure(new ShadowElement.Config(0.15f, 0.75f))
						.build())
				.with(element(VisualElements.ITEM_ENTITY).build())
				.shouldVisualize(((ctx, entity) -> ItemVisual.isSupported(entity)))
				.build()
				.skipVanillaRender(ItemVisual::isSupported)
				.apply(EXPERIMENTAL);

		// Kept as the last statement so that the two registration blocks never share a line:
		// anything added above belongs to this method, anything for entities goes in
		// MobVisuals.init(), and the two can be edited without either seeing the other.
		MobVisuals.init();

		// Log every block-entity and entity type registered for Flywheel instancing so
		// players and developers can confirm that the vanilla renderer is being bypassed.
		LOGGER.info(
				"[Vanillin] Block-Entity & Entity Instancing ATIVO via Flywheel (Técnica 3 - ULTRA PERFORMANCE).\n" +
				"  BlockEntities instanciados: CHEST, ENDER_CHEST, TRAPPED_CHEST (3)\n" +
				"  Entidades instanciadas  (STABLE): BLOCK_DISPLAY (1)\n" +
				"  Entidades instanciadas  (EXPERIMENTAL): ITEM_DISPLAY, ITEM (2)\n" +
				"  DESABILITADOS p/ performance: BELL, SHULKER_BOX, BED, LECTERN, CAMPFIRE, DECORATED_POT, PISTON, STRUCTURE_BLOCK,\n" +
				"    CHEST_MINECART, COMMAND_BLOCK_MINECART, FURNACE_MINECART, HOPPER_MINECART, MINECART, SPAWNER_MINECART,\n" +
				"    TNT_MINECART, ITEM_FRAME, GLOW_ITEM_FRAME\n" +
				"  Cada tipo usa um único VBO de instâncias — o renderer vanilla é ignorado " +
				"e substituído pelo Flywheel INSTANCING backend (VBO/UBO, sem compute shaders).",
				EXPERIMENTAL
		);
	}

	public static <T extends Entity> void commonElements(EntityBuilder<T> builder) {
		builder.with(element(VisualElements.HITBOX).configure(false)
				.build());
	}

	public static <T extends ItemFrame> EntityVisualizerBuilder<T> itemFrame(EntityType<T> type) {
		return composable(type).apply(VanillaVisuals::commonElements)
				.with(element(VisualElements.ITEM_FRAME).build())
				.shouldVisualize((ctx, entity) -> ItemFrameVisual.shouldVisualize(entity))
				.build()
				.skipVanillaRender(ItemFrameVisual::shouldVisualize);
	}

	public static <T extends AbstractMinecart> EntityVisualizerBuilder<T> minecart(EntityType<T> type, ModelLayerLocation variant) {
		return composable(type).apply(VanillaVisuals::commonElements)
				.with(element(VisualElements.SHADOW).configure(new ShadowElement.Config(0.7f, ShadowElement.Config.DEFAULT_STRENGTH))
						.build())
				.with(element(VisualElements.FIRE).build())
				.with(element(VisualElements.MINECART).configure(variant)
						.build())
				.build()
				.skipVanillaRender(MinecartVisual::shouldSkipRender);
	}

	public static <T extends Entity> EntityBuilder<T> composable(EntityType<T> entityType) {
		return new EntityBuilder<>(entityType);
	}

	public static <T, C> ConfiguredElementImpl.ConfiguredElementBuilder<T, C> element(VisualElement<T, C> element) {
		return new ConfiguredElementImpl.ConfiguredElementBuilder<>(element);
	}

	public static class EntityBuilder<T extends Entity> {
		private final List<ConfiguredElement<? super T>> elements = new ArrayList<>();

		private final EntityType<T> entityType;
		@Nullable
		private VisualizationPredicate<T> predicate;

		public EntityBuilder(EntityType<T> entityType) {
			this.entityType = entityType;
		}

		/**
		 * Set a predicate to control whether <em>all</em> elements are visualized.
		 * <p>This is useful when you can't guarantee than an entity will support visualization for its entire lifetime.
		 *
		 * @param predicate A visualization predicate, returning {@code true} to indicate the entity should be visualized.
		 */
		public EntityBuilder<T> shouldVisualize(VisualizationPredicate<T> predicate) {
			this.predicate = predicate;
			return this;
		}

		/**
		 * Add a configured visual element to this visualizer.
		 *
		 * @param element The configured visual element.
		 */
		public EntityBuilder<T> with(ConfiguredElement<? super T> element) {
			elements.add(element);
			return this;
		}

		public EntityBuilder<T> apply(Consumer<EntityBuilder<T>> mutate) {
			mutate.accept(this);
			return this;
		}

		public EntityVisualizerBuilder<T> build() {
			var elementsArray = elements.toArray(new ConfiguredElement[0]);

			if (predicate == null) {
				predicate = VisualizationPredicate.alwaysTrue();
			}

			var controller = new ComposableEntityVisual.Controller<T>(elementsArray, predicate);

			return builder(entityType).factory((ctx, entity, partialTick) -> new ComposableEntityVisual<T>(ctx, entity, partialTick, controller));
		}
	}

	public static <T extends BlockEntity> BlockEntityVisualizerBuilder<T> builder(BlockEntityType<T> type) {
		return new BlockEntityVisualizerBuilder<>(CONFIGURATOR, type);
	}

	public static <T extends Entity> EntityVisualizerBuilder<T> builder(EntityType<T> type) {
		return new EntityVisualizerBuilder<>(CONFIGURATOR, type);
	}
}
