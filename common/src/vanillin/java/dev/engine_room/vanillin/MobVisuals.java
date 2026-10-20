package dev.engine_room.vanillin;

import dev.engine_room.vanillin.compose.VisualElement;
import dev.engine_room.vanillin.elements.ShadowElement;
import dev.engine_room.vanillin.visuals.BoatVisual;
import dev.engine_room.vanillin.visuals.FallingBlockVisual;
import dev.engine_room.vanillin.visuals.TntVisual;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * Registration of the entity visuals that do not belong to the block entity set.
 *
 * <p>This lives apart from {@link VanillaVisuals#init()} for one mechanical reason: two changes
 * made in parallel cannot both write to the same file without one of them overwriting the other.
 * Block entity registrations go in {@code VanillaVisuals.init()} and these go here, so the two
 * are disjoint files and either can be written without knowing about the other.
 *
 * <p>Everything in here is registered with the same {@code builder}/{@code composable} machinery
 * as the rest of the mod, so each visual still lands in {@code vanillin.json} and on Sodium's
 * page automatically - the options page enumerates the configurator rather than a hardcoded
 * list, and picks these up with no further change.
 */
public final class MobVisuals {
	// Element keys for the registrations below: each maps one entity type to its visual class,
	// kept beside the only registration that uses it instead of in the shared VisualElements set.
	private static final VisualElement.Unit<FallingBlockEntity> FALLING_BLOCK = FallingBlockVisual::new;
	private static final VisualElement.Unit<PrimedTnt> PRIMED_TNT = TntVisual::new;
	private static final VisualElement.Unit<Boat> BOAT = BoatVisual::new;

	private MobVisuals() {
	}

	/**
	 * Registers the additional entity visuals. Called from {@link VanillaVisuals#init()}, at its
	 * very end, so that the two registration blocks never share a line.
	 */
	public static void init() {
		// FallingBlockEntity.displayFireAnimation() is overridden to false, so there is no
		// dispatcher fire for a FIRE element to re-add. The predicate guards both sides of the
		// line: where FallingBlockRenderer's own early-out draws nothing, neither does vanilla
		// when left running, and where geometry should appear, vanilla is skipped and the
		// elements above draw instead - never both, never neither.
		VanillaVisuals.composable(EntityType.FALLING_BLOCK).apply(VanillaVisuals::commonElements)
				.with(VanillaVisuals.element(VisualElements.SHADOW).configure(new ShadowElement.Config(0.5f, ShadowElement.Config.DEFAULT_STRENGTH))
						.build())
				.with(VanillaVisuals.element(FALLING_BLOCK).build())
				.shouldVisualize((ctx, e) -> FallingBlockVisual.shouldSkipRender(e))
				.build()
				.skipVanillaRender(FallingBlockVisual::shouldSkipRender)
				// STABLE, not EXPERIMENTAL: EXPERIMENTAL resolves to the development-environment
				// flag, which is false in a release jar, so an experimental registration is not
				// "off by default" but absent. Each of these still has its own entry in
				// vanillin.json and on Sodium's page, so it can be switched off at runtime.
				.apply(VanillaVisuals.STABLE);

		// TntRenderer has no early-out, so skipping it unconditionally stays exclusive with the
		// visual; the builder default for skipVanillaRender is already that. FIRE restores the
		// flames that the skip would otherwise take with it. 0.5f is the renderer's shadow radius.
		VanillaVisuals.composable(EntityType.TNT).apply(VanillaVisuals::commonElements)
				.with(VanillaVisuals.element(VisualElements.SHADOW).configure(new ShadowElement.Config(0.5f, ShadowElement.Config.DEFAULT_STRENGTH))
						.build())
				.with(VanillaVisuals.element(VisualElements.FIRE).build())
				.with(VanillaVisuals.element(PRIMED_TNT).build())
				.build()
				.apply(VanillaVisuals.STABLE);

		// BoatRenderer likewise draws every boat unconditionally, so the default skip is again
		// correct; 0.8f is its shadow radius, and FIRE re-adds the dispatcher flames the skip
		// removes.
		VanillaVisuals.composable(EntityType.BOAT).apply(VanillaVisuals::commonElements)
				.with(VanillaVisuals.element(VisualElements.SHADOW).configure(new ShadowElement.Config(0.8f, ShadowElement.Config.DEFAULT_STRENGTH))
						.build())
				.with(VanillaVisuals.element(VisualElements.FIRE).build())
				.with(VanillaVisuals.element(BOAT).build())
				.build()
				.apply(VanillaVisuals.STABLE);
	}
}
