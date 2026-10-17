package dev.engine_room.flywheel.impl.mixin.fabric;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.engine_room.flywheel.backend.MobileOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;

@Mixin(TextureAtlas.class)
abstract class TextureAtlasMixin {
	/**
	 * {@code TextureAtlas#cycleAnimationFrames()} is what advances the animated sprites
	 * (item and block textures) and re-uploads the frames. While a screen is open the
	 * world is not the focus, so freezing the animation removes that upload traffic and
	 * the associated hitching at no real cost.
	 */
	@Inject(method = "cycleAnimationFrames", at = @At("HEAD"), cancellable = true)
	private void flywheel$freezeGuiItemAnimation(CallbackInfo ci) {
		if (MobileOptions.freezeGuiItemAnimation && Minecraft.getInstance().screen != null) {
			ci.cancel();
		}
	}
}
