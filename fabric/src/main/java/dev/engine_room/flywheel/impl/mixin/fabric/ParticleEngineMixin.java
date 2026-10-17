package dev.engine_room.flywheel.impl.mixin.fabric;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.engine_room.flywheel.backend.MobileOptions;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;

@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
	/**
	 * {@code ParticleEngine#add(Particle)} is the single funnel every particle goes
	 * through, including the ones spawned by other mods, so cancelling it here is
	 * enough to drop all of them.
	 */
	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void flywheel$disableParticles(Particle particle, CallbackInfo ci) {
		if (MobileOptions.disableParticles) {
			ci.cancel();
		}
	}
}
